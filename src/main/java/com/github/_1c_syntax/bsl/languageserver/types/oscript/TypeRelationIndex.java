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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
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
import java.util.function.Function;

/**
 * Единая точка истины об отношениях наследования между OneScript-классами
 * библиотеки <a href="https://github.com/oscript-library/extends">extends</a>:
 * {@code &Расширяет} (наследование) и {@code &Реализует} (реализация интерфейса).
 * <p>
 * Раньше транзитивный обход цепочки {@code &Расширяет} и защита от циклов были
 * продублированы в трёх местах — провайдере членов ({@link OScriptModuleMembersProvider}),
 * провайдере иерархии типов и провайдере перехода к реализациям. Теперь все
 * обходы и cycle-guard'ы живут здесь, а потребители лишь задают способ
 * разрешения имён (в документ или в {@link TypeRef}) через функции — поэтому
 * индекс не зависит ни от {@code TypeRegistry}, ни от {@code OScriptClassResolver}
 * и не образует с ними циклов в графе бинов.
 * <p>
 * Отношения читаются «вживую» из аннотаций через {@link OScriptExtends} при
 * каждом запросе (а не кэшируются), что бесплатно даёт hot-reload: правка
 * {@code &Расширяет}/{@code &Реализует} подхватывается без ре-регистрации.
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

  /**
   * Является ли документ интерфейсом ({@code &Интерфейс} на конструкторе).
   */
  public boolean isInterface(DocumentContext documentContext) {
    return oScriptExtends.isInterface(documentContext);
  }

  /**
   * Имя родителя, объявленного через {@code &Расширяет} (без требования, чтобы
   * родитель уже был доступен/разрешался). {@link Optional#empty()}, если
   * наследование не объявлено.
   */
  public Optional<String> supertypeName(DocumentContext documentContext) {
    return oScriptExtends.parentClassName(documentContext);
  }

  /**
   * Имя родителя для целей иерархии типов с поправкой на мета-аннотации «ОСени».
   * Для обычного класса — то же, что {@link #supertypeName(DocumentContext)}
   * (имя возвращается даже без разрешения родителя). Для класса-определения
   * аннотации ({@code &Аннотация}) {@code &Расширяет} — это шаблон мета-аннотации,
   * а не собственный супертип, поэтому имя возвращается лишь когда родитель сам
   * разрешается в класс-определение аннотации (наследование аннотация→аннотация).
   *
   * @param documentContext документ, для которого вычисляется супертип
   * @param nameToDocument   разрешение имени класса в документ (обычно через
   *                         {@code OScriptClassResolver})
   * @return имя супертипа либо {@link Optional#empty()}
   */
  public Optional<String> supertypeName(
    DocumentContext documentContext,
    Function<String, Optional<DocumentContext>> nameToDocument
  ) {
    var parentName = oScriptExtends.parentClassName(documentContext);
    if (parentName.isEmpty() || !oScriptExtends.isAnnotationDefinition(documentContext)) {
      return parentName;
    }
    return parentName.filter(name -> nameToDocument.apply(name)
      .map(oScriptExtends::isAnnotationDefinition)
      .orElse(false));
  }

  /**
   * Прямой родительский документ (супертип через {@code &Расширяет}),
   * разрешённый переданной функцией {@code nameToDocument}.
   *
   * @param documentContext документ-наследник
   * @param nameToDocument   разрешение имени класса в документ (обычно через
   *                         {@code OScriptClassResolver})
   * @return документ родителя либо {@link Optional#empty()}
   */
  public Optional<DocumentContext> supertype(
    DocumentContext documentContext,
    Function<String, Optional<DocumentContext>> nameToDocument
  ) {
    return oScriptExtends.parentClassName(documentContext)
      .flatMap(nameToDocument)
      .filter(parent -> inheritableFromParent(documentContext, parent));
  }

  /**
   * Допустимо ли регистрировать отношение наследования {@code child → parent}
   * библиотеки {@code extends}. Класс-определение аннотации ({@code &Аннотация})
   * несёт {@code &Расширяет} как шаблон мета-аннотации для классов, помеченных
   * этой аннотацией, а не как собственный супертип, поэтому такое отношение
   * считается реальным только между аннотациями (аннотация→аннотация);
   * аннотация→обычный класс отбрасывается. Для обычных классов ограничения нет.
   *
   * @param child  документ-наследник
   * @param parent документ-родитель
   * @return {@code true}, если отношение наследования допустимо
   */
  private boolean inheritableFromParent(DocumentContext child, DocumentContext parent) {
    return !oScriptExtends.isAnnotationDefinition(child)
      || oScriptExtends.isAnnotationDefinition(parent);
  }

  /**
   * Прямые наследники: все {@code .os}-документы из {@code universe}, объявившие
   * {@code documentContext} своим родителем через {@code &Расширяет}.
   *
   * @param documentContext документ-родитель
   * @param universe         просматриваемые документы (обычно все из ServerContext)
   * @param classNamesOf     имена, под которыми класс известен другим (обычно
   *                         через {@code OScriptClassResolver#classNames})
   * @return список документов-наследников
   */
  public List<DocumentContext> subtypes(
    DocumentContext documentContext,
    Collection<DocumentContext> universe,
    Function<DocumentContext, List<String>> classNamesOf
  ) {
    var ownNames = new HashSet<String>();
    for (var name : classNamesOf.apply(documentContext)) {
      ownNames.add(name.toLowerCase(Locale.ROOT));
    }
    var result = new ArrayList<DocumentContext>();
    for (var candidate : universe) {
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
   * @param nameToDocument разрешение имени класса/интерфейса в документ
   * @return {@code true}, если кандидат реализует один из интерфейсов
   */
  public boolean implementsAny(
    DocumentContext candidate,
    Set<String> interfaceNames,
    Function<String, Optional<DocumentContext>> nameToDocument
  ) {
    if (interfaceNames.isEmpty()) {
      return false;
    }
    var visited = new HashSet<URI>();
    DocumentContext current = candidate;
    while (current != null && visited.add(current.getUri())) {
      for (var name : oScriptExtends.implementedInterfaceNames(current)) {
        if (interfaceClosureMatches(name, interfaceNames, nameToDocument)) {
          return true;
        }
      }
      current = oScriptExtends.parentClassName(current).flatMap(nameToDocument).orElse(null);
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
    Function<String, Optional<DocumentContext>> nameToDocument
  ) {
    var visited = new HashSet<String>();
    String name = interfaceName;
    while (name != null && visited.add(name.toLowerCase(Locale.ROOT))) {
      if (targets.contains(name.toLowerCase(Locale.ROOT))) {
        return true;
      }
      name = nameToDocument.apply(name)
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
   * @param nameToRef        разрешение имени родителя в {@link TypeRef}
   * @param membersOf        получение членов типа (обычно {@code TypeRegistry#getMembers})
   * @return унаследованные члены; пустой список, если наследование не объявлено,
   *         родитель не разрешается или обнаружен цикл
   */
  public Collection<MemberDescriptor> inheritedMembers(
    DocumentContext documentContext,
    TypeRef classRef,
    Function<String, Optional<TypeRef>> nameToRef,
    Function<TypeRef, Collection<MemberDescriptor>> membersOf
  ) {
    // Класс-определение аннотации (&Аннотация) несёт &Расширяет как шаблон
    // мета-аннотации, а не собственное наследование, поэтому членов родителя не
    // получает. Здесь доступно лишь разрешение имени в TypeRef (не в документ),
    // поэтому редкий случай аннотация→аннотация по членам не разворачиваем — для
    // класса-маркера это несущественно (в отличие от иерархии типов в supertype).
    if (oScriptExtends.isAnnotationDefinition(documentContext)) {
      return List.of();
    }
    var parentRef = oScriptExtends.parentClassName(documentContext).flatMap(nameToRef).orElse(null);
    if (parentRef == null || parentRef.equals(classRef)) {
      return List.of();
    }
    var inProgress = inheritanceInProgress.get();
    if (!inProgress.add(classRef)) {
      return List.of();
    }
    try {
      return List.copyOf(membersOf.apply(parentRef));
    } finally {
      inProgress.remove(classRef);
      // Не держим пустой Set в ThreadLocal на пуловых потоках (S5164).
      if (inProgress.isEmpty()) {
        inheritanceInProgress.remove();
      }
    }
  }
}
