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

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.OScriptExtends;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.TypeRelations;
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
 *       {@link FileType#OS};</li>
 *   <li>регистрирует ленивый {@code MemberSource}, который при каждом запросе
 *       идёт в актуальный {@code SymbolTree} документа (это даёт hot-reload);</li>
 *   <li>для OScriptClass дополнительно регистрирует ленивый источник
 *       конструкторов из {@code ПриСозданииОбъекта} и связывает URI класса с его
 *       типом в {@link GlobalScopeProvider} (self-тип: неквалифицированный доступ
 *       к своим членам и встроенным методам класса внутри его же тела).</li>
 * </ul>
 */
@Slf4j
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class OScriptModuleMembersProvider {

  private static final String RAISE_EVENT_NAME_RU = "ВызватьСобытие";
  private static final String RAISE_EVENT_NAME_EN = "RaiseEvent";
  private static final String THIS_OBJECT_NAME_RU = "ЭтотОбъект";
  private static final String THIS_OBJECT_NAME_EN = "ThisObject";

  private final TypeRegistry typeRegistry;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final GlobalScopeProvider globalScopeProvider;
  private final OScriptExtends oScriptExtends;
  private final TypeRelations typeRelations;
  private final OScriptIterable oScriptIterable;
  private final CommentTypeResolver commentTypeResolver;

  /** URI документа → множество qualifiedNames зарегистрированных типов
   *  (один .os может одновременно быть и модулем, и классом). */
  private final Map<URI, Set<String>> registeredByUri = new ConcurrentHashMap<>();

  // Раньше ReferenceIndexFiller (@Order 200): register() наполняет moduleTypeRefByUri
  // OScript-типов, от которого зависит self-member проход индексатора.
  @Order(100)
  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    if (documentContext.getFileType() != FileType.OS) {
      return;
    }
    register(documentContext);
    invalidateMembersOfDocumentAndSubtypes(documentContext);
  }

  /**
   * Точечно сбросить memo членов правленого документа и всех его наследников.
   * <p>
   * Member-source типа лениво читает символьное дерево своего документа, поэтому правка
   * требует пересборки его членов. Наследники задеты транзитивно: источник унаследованных
   * членов ({@code TypeRelations.inheritedMembers}) копирует к себе результат
   * {@code getMembers} родителя, так что в memo наследника лежит снимок членов родителя —
   * сброса одного лишь родителя недостаточно, снимок «протухает» на всю глубину иерархии.
   * <p>
   * Обход идёт по прямым наследникам ({@code &Расширяет}); интерфейсы ({@code &Реализует})
   * членов не приносят, поэтому реализаторов обходить не нужно. Повторные посещения
   * отсекаются по URI — это же защищает от циклов в объявлениях наследования.
   *
   * @param documentContext правленый {@code .os}-документ.
   */
  private void invalidateMembersOfDocumentAndSubtypes(DocumentContext documentContext) {
    var visited = new HashSet<URI>();
    var queue = new ArrayDeque<DocumentContext>();
    queue.add(documentContext);
    while (!queue.isEmpty()) {
      var current = queue.poll();
      if (!visited.add(current.getUri())) {
        continue;
      }
      var names = registeredByUri.get(current.getUri());
      if (names != null) {
        for (var name : names) {
          typeRegistry.resolve(name, FileType.OS).ifPresent(typeRegistry::invalidateMembers);
        }
      }
      queue.addAll(typeRelations.subtypes(current));
    }
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
      // NFC-нормализация: имя файла на macOS хранится в NFD и иначе не совпадёт
      // с идентификатором типа в исходном коде (NFC). См. OScriptLibraryIndex#nameKey.
      var baseName = java.text.Normalizer.normalize(
        FilenameUtils.getBaseName(uri.getPath()), java.text.Normalizer.Form.NFC);
      registerOne(documentContext, baseName, null);
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
    var ref = typeRegistry.registerUserType(qualifiedName, module, FileType.OS);

    // Признак обходимой коллекции (&Обходимое) выставляется при каждой
    // регистрации, а не только при первой: так добавление/удаление аннотации
    // подхватывается hot-reload без ре-регистрации типа.
    typeRegistry.setUserTypeIterable(ref, oScriptIterable.isIterable(documentContext), FileType.OS);

    if (firstTimeForName) {
      typeRegistry.registerMemberSource(ref, () -> collectMembers(documentContext), FileType.OS);
      if (libraryEntry != null) {
        if (libraryEntry.kind() == OScriptLibraryIndex.EntryKind.CLASS) {
          typeRegistry.registerConstructorSource(ref, () -> collectConstructors(documentContext, ref), FileType.OS);
          registerInheritedMembers(documentContext, ref);
          typeRegistry.registerMemberSource(ref, () -> builtinClassMembers(ref), FileType.OS);
          registerSelfTypeUnlessModuleRoleClaimedIt(uri, ref);
        } else if (libraryEntry.kind() == OScriptLibraryIndex.EntryKind.MODULE) {
          // Обратный индекс URI→тип для вывода типа ресивера-модуля по ModuleSymbol
          // (единый источник в GlobalScopeProvider вместо обращения инференсера к
          // oScriptLibraryIndex). Только для роли MODULE: у dual-role .os-файла
          // роль CLASS не должна перетирать тип модуля под тем же URI.
          globalScopeProvider.indexModuleType(uri, ref);
          // library-модуль — глобальное свойство (OS).
          // declaration уже хранит UserType (registerUserType выше), поэтому
          // символ не передаём; член собирает сам TypeRegistry (override-source).
          typeRegistry.registerGlobalPropertyType(ref, FileType.OS);
        }
      } else if (documentContext.getModuleType() == ModuleType.OScriptClass) {
        typeRegistry.registerConstructorSource(ref, () -> collectConstructors(documentContext, ref), FileType.OS);
        registerInheritedMembers(documentContext, ref);
        typeRegistry.registerMemberSource(ref, () -> builtinClassMembers(ref), FileType.OS);
        globalScopeProvider.indexModuleType(uri, ref);
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
      // снять пометку глобального свойства до удаления типа (resolve по имени)
      typeRegistry.resolve(name)
        .ifPresent(ref -> typeRegistry.unregisterGlobalPropertyType(ref, FileType.OS));
      typeRegistry.unregisterUserType(name);
    }
  }

  /**
   * Зарегистрировать ленивый источник членов, наследуемых от родительского
   * класса библиотеки {@code extends} (аннотация {@code &Расширяет} над
   * {@code ПриСозданииОбъекта}). Обход цепочки наследования и защита от циклов —
   * в {@link TypeRelations}; здесь лишь регистрируется делегирующий источник. Источник добавляется ПОСЛЕ собственного источника
   * членов класса, поэтому при дедупликации в {@link TypeRegistry#getMembers}
   * собственные/переопределённые члены выигрывают у унаследованных. Резолв
   * родителя ленивый — он может быть проиндексирован позже наследника, а смена
   * {@code &Расширяет} подхватывается без ре-регистрации (hot-reload).
   */
  private void registerInheritedMembers(DocumentContext documentContext, TypeRef classRef) {
    typeRegistry.registerMemberSource(
      classRef,
      () -> typeRelations.inheritedMembers(documentContext, classRef),
      FileType.OS
    );
  }

  /**
   * Связать URI library-класса с его типом в {@link GlobalScopeProvider}, если
   * этот URI ещё не занят типом модуля. Пропускается для dual-role .os-файла
   * (одновременно {@code <module>} и {@code <class>} на один {@code file}):
   * ролью модуля URI уже занят, и его self-тип не должен смениться на класс.
   */
  private void registerSelfTypeUnlessModuleRoleClaimedIt(URI uri, TypeRef classRef) {
    if (globalScopeProvider.moduleTypeRefByUri(uri).isEmpty()) {
      globalScopeProvider.indexModuleType(uri, classRef);
    }
  }

  /**
   * Встроенные члены, доступные у ЛЮБОГО OScript-класса вне зависимости от
   * того, что он объявляет сам: {@code ВызватьСобытие}/{@code RaiseEvent}
   * (оповещает подписчиков, добавленных через {@code ДобавитьОбработчик}/
   * {@code AddHandler}; не связан с аннотацией {@code &Событие} — это отдельный,
   * не поддерживаемый здесь механизм) и {@code ЭтотОбъект}/{@code ThisObject}
   * (ссылка на текущий экземпляр класса).
   */
  private List<MemberDescriptor> builtinClassMembers(TypeRef classRef) {
    var stringType = typeRegistry.resolve("Строка", FileType.OS).map(TypeSet::of).orElse(TypeSet.EMPTY);
    var arrayType = typeRegistry.resolve("Массив", FileType.OS).map(TypeSet::of).orElse(TypeSet.EMPTY);
    var signature = new SignatureDescriptor(
      List.of(
        new ParameterDescriptor(BilingualString.of("ИмяСобытия", "EventName"), stringType, false,
          BilingualString.of("Имя события.", "Event name."), ""),
        new ParameterDescriptor(BilingualString.of("ПараметрыСобытия", "EventArgs"), arrayType, true,
          BilingualString.of("Параметры события, передаваемые подписчикам.",
            "Event arguments passed to subscribers."), "")
      ),
      TypeSet.EMPTY,
      BilingualString.EMPTY
    );
    var raiseEvent = MemberDescriptor.method(RAISE_EVENT_NAME_RU,
        "Вызывает событие с указанным именем, оповещая подписчиков, добавленных через "
          + "ДобавитьОбработчик/AddHandler.",
        List.of(signature))
      .withBilingualName(BilingualString.of(RAISE_EVENT_NAME_RU, RAISE_EVENT_NAME_EN))
      .withStandardLibrary(true);
    var thisObject = MemberDescriptor.property(THIS_OBJECT_NAME_RU, classRef,
        "Ссылка на текущий экземпляр класса.")
      .withBilingualName(BilingualString.of(THIS_OBJECT_NAME_RU, THIS_OBJECT_NAME_EN))
      .withStandardLibrary(true);
    return List.of(raiseEvent, thisObject);
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
        var types = propertyTypesFromComment(variable);
        if (types.isEmpty()) {
          members.add(MemberDescriptor.property(variable.getName()));
        } else {
          members.add(MemberDescriptor.property(variable.getName(), types, ""));
        }
      }
    }
    return members;
  }

  /**
   * Типы экспортной переменной-свойства из типизирующего висячего комментария
   * её декларации ({@code Перем Контейнер Экспорт; // Массив из Число},
   * {@code Перем Сложно Экспорт; // см. НовыйСложно}). Делегирует общему для обоих
   * языков {@link CommentTypeResolver}.
   */
  private TypeSet propertyTypesFromComment(VariableSymbol variable) {
    return commentTypeResolver.resolve(variable, FileType.OS);
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
      var name = DescriptionTypes.resolveName(td);
      if (!name.isBlank()) {
        typeRegistry.resolve(name).ifPresent(refs::add);
      }
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
