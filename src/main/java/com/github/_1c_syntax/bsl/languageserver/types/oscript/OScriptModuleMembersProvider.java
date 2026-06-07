/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Регистрирует USER-типы и источники членов для .os-файлов
 * (OneScript-классов и модулей).
 * <p>
 * Реагирует на {@link DocumentContextContentChangedEvent} для любого .os-файла:
 * <ul>
 *   <li>определяет qualifiedName: override из {@link OScriptLibraryIndex}
 *       (если файл принадлежит индексированной библиотеке с {@code lib.config}),
 *       fallback — basename файла;</li>
 *   <li>регистрирует тип через {@link TypeRegistry#registerUserType} со скоупом
 *       {@link LanguageScope#OS};</li>
 *   <li>регистрирует ленивый {@code MemberSource}, который при каждом запросе
 *       идёт в актуальный {@code SymbolTree} документа (это даёт hot-reload);</li>
 *   <li>для OScriptClass дополнительно регистрирует ленивый источник
 *       конструкторов из {@code ПриСозданииОбъекта}.</li>
 * </ul>
 */
@Slf4j
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class OScriptModuleMembersProvider {

  private final TypeRegistry typeRegistry;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final GlobalScopeProvider globalScopeProvider;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /** URI документа → множество qualifiedNames зарегистрированных типов
   *  (один .os может одновременно быть и модулем, и классом). */
  private final Map<URI, java.util.Set<String>> registeredByUri = new ConcurrentHashMap<>();

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    if (documentContext.getFileType() != FileType.OS) {
      return;
    }
    register(documentContext);
  }

  /**
   * Зарегистрировать .os-документ как пользовательский тип. Вызывается
   * как через {@link DocumentContextContentChangedEvent}, так и напрямую
   * из {@link OScriptLibraryIndex} (чтобы гарантировать выполнение в том
   * же workspace-scope, что и индексация).
   */
  public void register(DocumentContext documentContext) {
    var uri = documentContext.getUri();
    var libraryEntries = oScriptLibraryIndex.findEntriesByUri(uri);
    if (libraryEntries.isEmpty()) {
      // не библиотечный .os — регистрируем по basename как USER-тип.
      registerOne(documentContext, FilenameUtils.getBaseName(uri.getPath()), null);
      return;
    }
    // Для библиотечного файла регистрируем каждую роль (модуль и/или класс)
    // под её qualifiedName из lib.config.
    for (var entry : libraryEntries) {
      registerOne(documentContext, entry.qualifiedName(), entry);
    }
  }

  private void registerOne(DocumentContext documentContext, String qualifiedName,
                           OScriptLibraryIndex.LibraryEntry libraryEntry) {
    if (qualifiedName == null || qualifiedName.isBlank()) {
      return;
    }
    var uri = documentContext.getUri();
    var names = registeredByUri.computeIfAbsent(uri, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
    var firstTimeForName = names.add(qualifiedName);

    var module = documentContext.getSymbolTree().getModule();
    var ref = typeRegistry.registerUserType(qualifiedName, module, LanguageScope.OS);

    if (firstTimeForName) {
      typeRegistry.registerMemberSource(ref, () -> collectMembers(documentContext), LanguageScope.OS);
      if (libraryEntry != null) {
        if (libraryEntry.kind() == OScriptLibraryIndex.EntryKind.CLASS) {
          typeRegistry.registerConstructorSource(ref, () -> collectConstructors(documentContext, ref), LanguageScope.OS);
          registerInheritedMembers(documentContext, ref);
          globalScopeProvider.registerLibraryClass(qualifiedName, ref);
        } else if (libraryEntry.kind() == OScriptLibraryIndex.EntryKind.MODULE) {
          // Обратный индекс URI→тип для вывода типа ресивера-модуля по ModuleSymbol
          // (единый источник в GlobalScopeProvider вместо обращения инференсера к
          // oScriptLibraryIndex). Только для роли MODULE: у dual-role .os-файла
          // роль CLASS не должна перетирать тип модуля под тем же URI.
          globalScopeProvider.indexModuleType(uri, ref);
          globalScopeProvider.registerLibraryModule(qualifiedName, ref);
        }
      } else if (documentContext.getModuleType() == ModuleType.OScriptClass) {
        typeRegistry.registerConstructorSource(ref, () -> collectConstructors(documentContext, ref), LanguageScope.OS);
        registerInheritedMembers(documentContext, ref);
      }
      LOGGER.debug("Registered .os module-as-type: {} -> {} kind={}", uri, qualifiedName,
        libraryEntry != null ? libraryEntry.kind() : documentContext.getModuleType());
    }
  }

  /**
   * Удалить ранее зарегистрированные типы/namespace по URI документа.
   * Вызывается из {@link OScriptLibraryIndex#handleDocumentRemoved} и при
   * удалении любого {@code .os}-документа из {@code ServerContext}.
   */
  public void unregister(URI uri) {
    globalScopeProvider.removeModuleType(uri);
    var names = registeredByUri.remove(uri);
    if (names == null) {
      return;
    }
    for (var name : names) {
      typeRegistry.unregisterUserType(name);
      globalScopeProvider.unregisterLibraryModule(name);
      globalScopeProvider.unregisterLibraryClass(name);
    }
  }

  /**
   * Защита от циклов наследования ({@code A → B → A}): набор типов, сборка
   * наследуемых членов которых уже идёт в текущем потоке. Без неё ленивые
   * источники зациклились бы через {@link TypeRegistry#getMembers}.
   */
  private static final ThreadLocal<Set<TypeRef>> INHERITANCE_IN_PROGRESS =
    ThreadLocal.withInitial(HashSet::new);

  /**
   * Зарегистрировать ленивый источник членов, наследуемых от родительского
   * класса библиотеки {@code extends} (аннотация {@code &Расширяет} над
   * {@code ПриСозданииОбъекта}). Источник добавляется ПОСЛЕ собственного
   * источника членов класса, поэтому при дедупликации в
   * {@link TypeRegistry#getMembers} собственные/переопределённые члены
   * выигрывают у унаследованных. Резолв родителя ленивый — он может быть
   * проиндексирован позже наследника, а смена {@code &Расширяет} подхватывается
   * без ре-регистрации (hot-reload).
   */
  private void registerInheritedMembers(DocumentContext documentContext, TypeRef classRef) {
    typeRegistry.registerMemberSource(classRef, () -> inheritedMembers(documentContext, classRef), LanguageScope.OS);
  }

  /**
   * Экспортируемые члены родительского класса (транзитивно — через его
   * собственный унаследованный источник). Пустой список, если наследование не
   * объявлено, родитель не разрешается или обнаружен цикл.
   */
  private Collection<MemberDescriptor> inheritedMembers(DocumentContext documentContext, TypeRef classRef) {
    var parentName = OScriptExtends.parentClassName(documentContext, metaAnnotationResolver).orElse(null);
    if (parentName == null) {
      return List.of();
    }
    var parentRef = typeRegistry.resolve(parentName, FileType.OS).orElse(null);
    if (parentRef == null || parentRef.equals(classRef)) {
      return List.of();
    }
    var inProgress = INHERITANCE_IN_PROGRESS.get();
    if (!inProgress.add(classRef)) {
      return List.of();
    }
    try {
      return List.copyOf(typeRegistry.getMembers(parentRef, FileType.OS));
    } finally {
      inProgress.remove(classRef);
    }
  }

  private Collection<MemberDescriptor> collectMembers(DocumentContext documentContext) {
    var symbolTree = documentContext.getSymbolTree();
    var constructor = symbolTree.getConstructor();
    var members = new ArrayList<MemberDescriptor>();
    for (var method : symbolTree.getMethods()) {
      if (constructor.isPresent() && method == constructor.get()) {
        continue;
      }
      if (!method.isExport()) {
        continue;
      }
      members.add(toMemberDescriptor(method));
    }
    for (VariableSymbol variable : symbolTree.getVariables()) {
      if (variable.isExport()) {
        members.add(MemberDescriptor.property(variable.getName()));
      }
    }
    return members;
  }

  private List<SignatureDescriptor> collectConstructors(DocumentContext documentContext, TypeRef classRef) {
    var ctor = documentContext.getSymbolTree().getConstructor();
    if (ctor.isEmpty()) {
      return List.of(new SignatureDescriptor(List.of(), classRef, ""));
    }
    var member = toMemberDescriptor(ctor.get());
    var rawSignatures = member.signatures();
    if (rawSignatures.isEmpty()) {
      return List.of(new SignatureDescriptor(List.of(), classRef, ""));
    }
    var result = new ArrayList<SignatureDescriptor>(rawSignatures.size());
    for (var sig : rawSignatures) {
      // У ПриСозданииОбъекта возвращаемый тип — сам класс.
      result.add(new SignatureDescriptor(sig.parameters(), classRef, sig.description()));
    }
    return result;
  }

  private MemberDescriptor toMemberDescriptor(MethodSymbol method) {
    var paramDescs = method.getDescription()
      .map(com.github._1c_syntax.bsl.parser.description.MethodDescription::getParameters)
      .orElse(List.of());
    var paramDefs = method.getParameters();
    var params = new ArrayList<ParameterDescriptor>(paramDefs.size());
    for (int i = 0; i < paramDefs.size(); i++) {
      var def = paramDefs.get(i);
      TypeSet types = TypeSet.EMPTY;
      String description = "";
      if (i < paramDescs.size()) {
        var pd = paramDescs.get(i);
        types = resolveTypes(pd.types());
        description = buildParameterDescription(pd);
      }
      params.add(new ParameterDescriptor(def.getName(), types, def.isOptional(), description));
    }
    TypeRef returnType = TypeRef.UNKNOWN;
    if (method.isFunction()) {
      var returnTypes = method.getDescription()
        .map(com.github._1c_syntax.bsl.parser.description.MethodDescription::getReturnedValue)
        .orElse(List.of());
      var resolved = resolveTypes(returnTypes);
      if (!resolved.refs().isEmpty()) {
        returnType = resolved.refs().iterator().next();
      }
    }
    var purpose = method.getDescription()
      .map(com.github._1c_syntax.bsl.parser.description.MethodDescription::getPurposeDescription)
      .orElse("");
    var signature = new SignatureDescriptor(params, returnType, purpose);
    return MemberDescriptor.method(method.getName(), purpose, List.of(signature))
      .withSourceSymbol(method);
  }

  private TypeSet resolveTypes(List<com.github._1c_syntax.bsl.parser.description.TypeDescription> types) {
    if (types == null || types.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new ArrayList<TypeRef>(types.size());
    for (var td : types) {
      typeRegistry.resolve(td.name()).ifPresent(refs::add);
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  private static String buildParameterDescription(com.github._1c_syntax.bsl.parser.description.ParameterDescription pd) {
    var typeDescriptions = pd.types();
    if (typeDescriptions == null || typeDescriptions.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    for (var td : typeDescriptions) {
      var text = td.description();
      if (text == null || text.isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(text.trim());
    }
    return sb.toString();
  }
}
