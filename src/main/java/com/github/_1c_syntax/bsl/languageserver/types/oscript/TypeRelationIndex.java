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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Единая точка истины об отношениях наследования между OneScript-классами
 * библиотеки <a href="https://github.com/oscript-library/extends">extends</a>:
 * {@code &Расширяет} (наследование) и {@code &Реализует} (реализация интерфейса).
 * <p>
 * Раньше транзитивный обход цепочки {@code &Расширяет} и защита от циклов были
 * продублированы в трёх местах — провайдере членов ({@link OScriptModuleMembersProvider}),
 * провайдере иерархии типов и провайдере перехода к реализациям. Теперь все
 * обходы и cycle-guard'ы живут здесь.
 * <p>
 * Разбор аннотаций делегируется {@link OScriptExtends}. Разрешение имени класса
 * в документ — каноническим путём системы типов (тем же, что использует
 * {@code Новый Имя}): {@link TypeRegistry#resolve} → {@link UserType} →
 * символ-источник → его документ, с приоритетом по каталогу library-классов
 * {@link OScriptLibraryIndex}. Все три бина workspace-scoped (CGLIB-прокси),
 * поэтому конструкторные зависимости между ними цикл графа бинов не образуют.
 * <p>
 * Отношения читаются «вживую» из аннотаций при каждом запросе (а не кэшируются),
 * что бесплатно даёт hot-reload: правка {@code &Расширяет}/{@code &Реализует}
 * подхватывается без ре-регистрации.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class TypeRelationIndex {

  /**
   * Защита от циклов наследования ({@code A → B → A}) при сборке наследуемых
   * членов. Сборка рекурсивна (члены родителя берутся через {@code getMembers},
   * который снова дёргает этот индекс), поэтому guard — потоколокальный набор
   * типов, для которых сборка уже идёт в текущем потоке.
   */
  private final ThreadLocal<Set<TypeRef>> inheritanceInProgress = ThreadLocal.withInitial(HashSet::new);

  private final OScriptExtends oScriptExtends;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final TypeRegistry typeRegistry;

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
    if (parentName.isEmpty() || !oScriptExtends.isAnnotationDefinition(documentContext)) {
      return parentName;
    }
    var serverContext = documentContext.getServerContext();
    return parentName.filter(name -> resolveDocument(name, serverContext)
      .map(oScriptExtends::isAnnotationDefinition)
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
    var ownNames = new HashSet<String>();
    for (var name : oScriptLibraryIndex.classNames(documentContext)) {
      ownNames.add(name.toLowerCase(Locale.ROOT));
    }
    var result = new ArrayList<DocumentContext>();
    for (var candidate : documentContext.getServerContext().getDocuments().values()) {
      if (candidate.getFileType() != FileType.OS
        || candidate.getUri().equals(documentContext.getUri())) {
        continue;
      }
      oScriptExtends.parentClassName(candidate)
        .filter(parent -> ownNames.contains(parent.toLowerCase(Locale.ROOT)))
        .filter(parent -> inheritableFromParent(candidate, documentContext))
        .ifPresent(parent -> result.add(candidate));
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
    return !oScriptExtends.isAnnotationDefinition(child)
      || oScriptExtends.isAnnotationDefinition(parent);
  }

  /**
   * Реализует ли документ-кандидат (транзитивно) хотя бы один из интерфейсов.
   * Учитываются два измерения транзитивности из документации extends:
   * <ul>
   *   <li><b>наследование классов</b> — родитель объявляет
   *       {@code &Реализует("Интерфейс")}, а наследник через {@code &Расширяет}
   *       считается реализацией (случай абстрактного родителя);</li>
   *   <li><b>иерархия интерфейсов</b> — интерфейс может расширять другой интерфейс
   *       аннотацией {@code &Расширяет}, поэтому реализатор производного интерфейса
   *       является реализатором и всех его базовых интерфейсов.</li>
   * </ul>
   * Обход цепочки классов итеративный (guard — множество посещённых URI),
   * разворачивание каждого интерфейса вверх по его {@code &Расширяет}-цепочке —
   * в {@link #interfaceClosureMatches} (guard — множество посещённых имён).
   *
   * @param candidate      проверяемый документ
   * @param interfaceNames имена искомых интерфейсов в нижнем регистре
   * @return {@code true}, если кандидат реализует один из интерфейсов
   */
  public boolean implementsAny(DocumentContext candidate, Set<String> interfaceNames) {
    if (interfaceNames.isEmpty()) {
      return false;
    }
    var serverContext = candidate.getServerContext();
    var visited = new HashSet<URI>();
    DocumentContext current = candidate;
    while (current != null && visited.add(current.getUri())) {
      for (var name : oScriptExtends.implementedInterfaceNames(current)) {
        if (interfaceClosureMatches(name, interfaceNames, serverContext)) {
          return true;
        }
      }
      current = oScriptExtends.parentClassName(current)
        .flatMap(name -> resolveDocument(name, serverContext))
        .orElse(null);
    }
    return false;
  }

  /**
   * Содержит ли замыкание интерфейса {@code interfaceName} (он сам плюс все его
   * базовые интерфейсы, объявленные через {@code &Расширяет}) хотя бы одно из
   * искомых имён. Обход вверх по иерархии интерфейсов с защитой от циклов по
   * множеству посещённых имён.
   */
  private boolean interfaceClosureMatches(
    String interfaceName,
    Set<String> targets,
    ServerContext serverContext
  ) {
    var visited = new HashSet<String>();
    String name = interfaceName;
    while (name != null && visited.add(name.toLowerCase(Locale.ROOT))) {
      if (targets.contains(name.toLowerCase(Locale.ROOT))) {
        return true;
      }
      name = resolveDocument(name, serverContext)
        .flatMap(oScriptExtends::parentClassName)
        .orElse(null);
    }
    return false;
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
    if (oScriptExtends.isAnnotationDefinition(documentContext)) {
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
