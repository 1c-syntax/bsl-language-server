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
package com.github._1c_syntax.bsl.languageserver.types.oscript.extends_;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptModuleMembersProvider;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import java.util.Set;

/**
 * Единая точка истины об отношениях наследования между OneScript-классами
 * библиотеки <a href="https://github.com/nixel2007/extends">extends</a>:
 * {@code &Расширяет} (наследование) и {@code &Реализует} (реализация интерфейса).
 * <p>
 * Разбор аннотаций делегируется {@link OScriptExtends}. Разрешение имени класса
 * в документ — каноническим путём системы типов (тем же, что использует
 * {@code Новый Имя}): {@link TypeRegistry#resolve} → {@link UserType} →
 * символ-источник → его документ, с приоритетом по каталогу library-классов
 * {@link OScriptLibraryIndex}. Бины workspace-scoped (CGLIB-прокси), поэтому их
 * конструкторные зависимости не образуют цикл графа бинов.
 * <p>
 * Отношения «вниз» (наследники, реализаторы) находятся через {@link TypeRelationIndex} —
 * обратный индекс прямых отношений (имя → URI), без сканирования всех документов
 * workspace. Отношения «вверх» (родитель) читаются «вживую» из аннотаций при каждом
 * запросе, что бесплатно даёт hot-reload: правка {@code &Расширяет}/{@code &Реализует}
 * подхватывается без ре-регистрации (точечную инвалидацию индекса выполняют его
 * event-обработчики).
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class TypeRelations {

  /**
   * Защита от циклов наследования ({@code A → B → A}) при сборке наследуемых
   * членов. Сборка рекурсивна (члены родителя берутся через {@code getMembers},
   * который снова дёргает этот индекс), поэтому guard — потоколокальный набор
   * типов, для которых сборка уже идёт в текущем потоке.
   */
  private final ThreadLocal<Set<TypeRef>> inheritanceInProgress = ThreadLocal.withInitial(HashSet::new);

  private final OScriptExtends oScriptExtends;
  private final OScriptMetaAnnotationResolver metaAnnotationResolver;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final TypeRegistry typeRegistry;
  private final TypeRelationIndex typeRelationIndex;

  /**
   * Является ли документ интерфейсом ({@code &Интерфейс} на конструкторе).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return {@code true}, если документ — интерфейс
   */
  public boolean isInterface(DocumentContext documentContext) {
    return oScriptExtends.isInterface(documentContext);
  }

  /**
   * Имя супертипа для целей иерархии типов с поправкой на мета-аннотации «ОСени».
   * Для обычного класса — имя из {@code &Расширяет} (возвращается даже если
   * родитель не разрешается). Для класса-определения аннотации ({@code &Аннотация})
   * {@code &Расширяет} — это шаблон мета-аннотации, а не собственный супертип,
   * поэтому имя возвращается лишь когда родитель сам разрешается в класс-определение
   * аннотации (наследование аннотация→аннотация).
   *
   * @param documentContext документ, для которого вычисляется супертип
   * @return имя супертипа либо {@link Optional#empty()}
   */
  public Optional<String> supertypeName(DocumentContext documentContext) {
    var parentName = oScriptExtends.parentClassName(documentContext);
    if (parentName.isEmpty() || !metaAnnotationResolver.isAnnotationDefinition(documentContext)) {
      return parentName;
    }
    var serverContext = documentContext.getServerContext();
    return parentName.filter(name -> resolveDocument(name, serverContext)
      .map(metaAnnotationResolver::isAnnotationDefinition)
      .orElse(false));
  }

  /**
   * Прямой родительский документ (супертип через {@code &Расширяет}). Имя
   * родителя разрешается в документ каноническим путём системы типов; для
   * класса-определения аннотации действует поправка из {@link #supertypeName}.
   *
   * @param documentContext документ-наследник
   * @return документ родителя либо {@link Optional#empty()}
   */
  public Optional<DocumentContext> supertype(DocumentContext documentContext) {
    var serverContext = documentContext.getServerContext();
    return oScriptExtends.parentClassName(documentContext)
      .flatMap(name -> resolveDocument(name, serverContext))
      .filter(parent -> inheritableFromParent(documentContext, parent));
  }

  /**
   * Прямые наследники: все {@code .os}-документы workspace, объявившие
   * {@code documentContext} своим родителем через {@code &Расширяет}.
   *
   * @param documentContext документ-родитель
   * @return список документов-наследников
   */
  public List<DocumentContext> subtypes(DocumentContext documentContext) {
    var serverContext = documentContext.getServerContext();
    var ownNames = oScriptLibraryIndex.classNames(documentContext);
    var result = new ArrayList<DocumentContext>();
    for (var uri : typeRelationIndex.directSubtypeUris(ownNames, serverContext)) {
      if (uri.equals(documentContext.getUri())) {
        continue;
      }
      var candidate = serverContext.getDocument(uri);
      if (candidate != null && inheritableFromParent(candidate, documentContext)) {
        result.add(candidate);
      }
    }
    return result;
  }

  /**
   * Допустимо ли регистрировать отношение наследования {@code child → parent}
   * библиотеки {@code extends}. Класс-определение аннотации ({@code &Аннотация})
   * несёт {@code &Расширяет} как шаблон мета-аннотации для классов, помеченных
   * этой аннотацией, а не как собственный супертип, поэтому такое отношение
   * считается реальным только между аннотациями (аннотация→аннотация);
   * аннотация→обычный класс отбрасывается. Для обычных классов ограничения нет.
   */
  private boolean inheritableFromParent(DocumentContext child, DocumentContext parent) {
    return !metaAnnotationResolver.isAnnotationDefinition(child)
      || metaAnnotationResolver.isAnnotationDefinition(parent);
  }

  /**
   * Все реализаторы интерфейса: документы, разрешающиеся реализациями
   * {@code interfaceDocument} с учётом двух измерений транзитивности из
   * документации extends:
   * <ul>
   *   <li><b>иерархия интерфейсов</b> — интерфейс может расширять другой интерфейс
   *       аннотацией {@code &Расширяет}, поэтому реализатор производного интерфейса
   *       является реализатором и всех его базовых;</li>
   *   <li><b>наследование классов</b> — наследник (через {@code &Расширяет})
   *       класса-реализатора сам является реализатором (случай абстрактного
   *       родителя).</li>
   * </ul>
   * Поиск идёт «вниз» от интерфейса по {@link TypeRelationIndex} без сканирования
   * документов workspace: сначала замыкание производных интерфейсов, затем их
   * прямые реализаторы и поддеревья наследников реализаторов. Циклы гасятся
   * множествами посещённых URI.
   *
   * @param interfaceDocument документ-интерфейс
   * @return документы-реализаторы (без самих интерфейсов); пусто, если их нет
   */
  public List<DocumentContext> implementors(DocumentContext interfaceDocument) {
    var serverContext = interfaceDocument.getServerContext();
    var interfaceUris = new HashSet<URI>();
    var interfaceNames = interfaceClosureNames(interfaceDocument, interfaceUris, serverContext);
    return expandImplementors(interfaceNames, interfaceUris, serverContext);
  }

  /**
   * Замыкание интерфейсов «вниз»: имена (lowercase) самого интерфейса и всех
   * производных интерфейсов, расширяющих его через {@code &Расширяет}.
   * Посещённые URI интерфейсов складываются в {@code interfaceUris} — и как
   * cycle-guard, и для исключения интерфейсов из списка реализаторов.
   */
  private Set<String> interfaceClosureNames(
    DocumentContext interfaceDocument,
    Set<URI> interfaceUris,
    ServerContext serverContext
  ) {
    var names = new LinkedHashSet<String>();
    var queue = new ArrayDeque<DocumentContext>();
    queue.add(interfaceDocument);
    while (!queue.isEmpty()) {
      var current = queue.poll();
      if (!interfaceUris.add(current.getUri())) {
        continue;
      }
      var currentNames = oScriptLibraryIndex.classNames(current);
      for (var name : currentNames) {
        names.add(name.toLowerCase(Locale.ROOT));
      }
      for (var uri : typeRelationIndex.directSubtypeUris(currentNames, serverContext)) {
        var derived = serverContext.getDocument(uri);
        if (derived != null && oScriptExtends.isInterface(derived)) {
          queue.add(derived);
        }
      }
    }
    return names;
  }

  /**
   * Прямые реализаторы интерфейсов замыкания плюс поддеревья их наследников
   * (наследник реализатора — тоже реализатор); сами интерфейсы исключаются.
   */
  private List<DocumentContext> expandImplementors(
    Set<String> interfaceNames,
    Set<URI> interfaceUris,
    ServerContext serverContext
  ) {
    var result = new LinkedHashMap<URI, DocumentContext>();
    var queue = new ArrayDeque<DocumentContext>();
    for (var uri : typeRelationIndex.directImplementorUris(interfaceNames, serverContext)) {
      var implementor = serverContext.getDocument(uri);
      if (implementor != null) {
        queue.add(implementor);
      }
    }
    while (!queue.isEmpty()) {
      var current = queue.poll();
      if (interfaceUris.contains(current.getUri())
        || result.putIfAbsent(current.getUri(), current) != null) {
        continue;
      }
      var names = oScriptLibraryIndex.classNames(current);
      for (var uri : typeRelationIndex.directSubtypeUris(names, serverContext)) {
        var child = serverContext.getDocument(uri);
        if (child != null && inheritableFromParent(child, current)) {
          queue.add(child);
        }
      }
    }
    return List.copyOf(result.values());
  }

  /**
   * Диапазон выделения класса для элементов навигации/иерархии: subName
   * конструктора, если он есть (на нём объявляются аннотации extends), иначе —
   * selectionRange модуля (поле без {@code @NonNull} может быть {@code null} —
   * даём безопасный нулевой fallback).
   *
   * @param documentContext контекст {@code .os}-документа-класса
   * @return диапазон выделения класса
   */
  public Range classSelectionRange(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getConstructor()
      .map(MethodSymbol::getSelectionRange)
      .orElseGet(() -> {
        var range = documentContext.getSymbolTree().getModule().getSelectionRange();
        return range != null ? range : Ranges.create(0, 0, 0, 0);
      });
  }

  /**
   * Экспортируемые члены родительского класса (транзитивно — через его
   * собственный унаследованный источник членов). Это «мост» отношения
   * наследования в систему типов: {@link OScriptModuleMembersProvider}
   * регистрирует ленивый {@code MemberSource}, делегирующий сюда, после
   * собственного источника членов класса — поэтому при дедупликации в
   * {@code TypeRegistry#getMembers} собственные/переопределённые члены
   * выигрывают у унаследованных.
   *
   * @param documentContext документ-наследник
   * @param classRef         тип наследника (ключ guard'а от циклов)
   * @return унаследованные члены; пустой список, если наследование не объявлено,
   *         родитель не разрешается или обнаружен цикл
   */
  public Collection<MemberDescriptor> inheritedMembers(DocumentContext documentContext, TypeRef classRef) {
    // Класс-определение аннотации (&Аннотация) несёт &Расширяет как шаблон
    // мета-аннотации, а не собственное наследование, поэтому членов родителя не
    // получает. Редкий случай аннотация→аннотация по членам не разворачиваем —
    // для класса-маркера это несущественно (в отличие от иерархии типов).
    if (metaAnnotationResolver.isAnnotationDefinition(documentContext)) {
      return List.of();
    }
    var parentRef = oScriptExtends.parentClassName(documentContext)
      .flatMap(name -> typeRegistry.resolve(name, FileType.OS))
      .orElse(null);
    if (parentRef == null || parentRef.equals(classRef)) {
      return List.of();
    }
    var inProgress = inheritanceInProgress.get();
    if (!inProgress.add(classRef)) {
      return List.of();
    }
    try {
      return List.copyOf(typeRegistry.getMembers(parentRef, FileType.OS));
    } finally {
      inProgress.remove(classRef);
      // Не держим пустой Set в ThreadLocal на пуловых потоках (S5164).
      if (inProgress.isEmpty()) {
        inheritanceInProgress.remove();
      }
    }
  }

  /**
   * Разрешить имя OneScript-класса ({@code &Расширяет("Имя")} / {@code &Реализует("Имя")})
   * в документ. Сначала — через каталог library-классов {@link OScriptLibraryIndex}
   * (надёжный URI без слабых ссылок), затем — каноническим путём системы типов:
   * {@code Имя → TypeRef → UserType → символ-источник → его документ} (тот же
   * резолв, что у {@code Новый Имя}; покрывает обычные {@code .os} по basename).
   */
  private Optional<DocumentContext> resolveDocument(String name, ServerContext serverContext) {
    var libraryUri = oScriptLibraryIndex.findClassUri(name)
      .or(() -> oScriptLibraryIndex.findUri(name));
    if (libraryUri.isPresent()) {
      var document = serverContext.getDocument(libraryUri.get());
      if (document != null) {
        return Optional.of(document);
      }
    }
    return typeRegistry.resolve(name, FileType.OS)
      .map(typeRegistry::get)
      .filter(UserType.class::isInstance)
      .map(UserType.class::cast)
      .flatMap(UserType::getDeclaration)
      .map(SourceDefinedSymbol::getOwner);
  }
}
