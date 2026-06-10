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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс желудей фреймворка «ОСень»: отображение «имя/прозвище желудя → тип».
 * <p>
 * Покрывает случаи, которые нельзя разрешить прямым резолвом имени типа:
 * <ul>
 *   <li>переименованный компонент — {@code &Желудь("ДругоеИмя")};</li>
 *   <li>класс-фабрика — {@code &Дуб} (сам тоже желудь);</li>
 *   <li>фабричный метод — {@code &Завязь} (только внутри {@code &Дуб});</li>
 *   <li>прозвища — {@code &Прозвище("Алиас")} (повторяемая);</li>
 *   <li>приоритет при конфликте имён/прозвищ — {@code &Верховный}.</li>
 * </ul>
 * Аннотации компонента ({@code &Желудь}/{@code &Дуб}) размещаются только над
 * конструктором, поэтому он берётся из дерева символов напрямую; методы
 * сканируются на {@code &Завязь} лишь когда класс — дуб.
 * <p>
 * Хранилище — {@link ConcurrentHashMap} (как {@code AnnotationRepository}):
 * {@link #resolve} читает без блокировок и параллельно (важно — вывод типов
 * зовётся из диагностик в parallel-стримах). Правка обычного .os-класса
 * обновляется точечно (удаление вклада URI + переиндексация только его), без
 * глобальных копирований. Полный ребилд нужен лишь там, где это неустранимо:
 * индекс — производный от {@code AnnotationRepository} (резолвит роли кастомных
 * аннотаций), поэтому правка класса-<i>определения</i> аннотации и
 * переиндексация библиотек сбрасывают индекс на ленивую пересборку. Допускается
 * eventual consistency во время редкой пересборки — как у
 * {@code AnnotationRepository}.
 */
@Component
@WorkspaceScope
public class AutumnBeanIndex extends AbstractAutumnLibraryIndex {

  /** Роли, под которыми класс является желудём (имя берётся с этой роль-аннотации). */
  private static final List<String> COMPONENT_ROLES = List.of(AutumnAnnotations.COMPONENT, AutumnAnnotations.OAK);

  private final TypeRegistry typeRegistry;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

  /** Имя/прозвище желудя (lowercase) → кандидаты. Значения — конкурентные множества (как в Repository). */
  private final Map<String, Set<BeanDeclaration>> beansByName = new ConcurrentHashMap<>();
  /** URI .os-файла → имена, под которыми он зарегистрировал кандидатов (для точечного удаления). */
  private final Map<URI, Set<String>> namesByUri = new ConcurrentHashMap<>();
  public AutumnBeanIndex(OScriptLibraryIndex libraryIndex,
                         ServerContextProvider serverContextProvider,
                         TypeRegistry typeRegistry,
                         AutumnMetaAnnotationResolver metaAnnotationResolver) {
    super(libraryIndex, serverContextProvider);
    this.typeRegistry = typeRegistry;
    this.metaAnnotationResolver = metaAnnotationResolver;
  }

  /**
   * Объявление производителя желудя для навигации — зеркало autumn-{@code Завязь}: у каждого
   * желудя есть метод-производитель (конструктор или {@code &Завязь}), различаемые флагом.
   *
   * @param type               Тип производимого желудя.
   * @param primary            Признак приоритетного желудя ({@code &Верховный}).
   * @param sourceUri          URI .os-файла с объявлением производителя.
   * @param producerMethodName Имя метода-производителя: конструктора ({@code ПриСозданииОбъекта})
   *                           для компонентного желудя либо метода {@code &Завязь} для фабричного.
   * @param isConstructor      {@code true}, если производитель — конструктор класса (компонентный
   *                           желудь {@code &Желудь}/{@code &Дуб}); {@code false} — метод {@code &Завязь}.
   */
  public record BeanDeclaration(
    TypeRef type,
    boolean primary,
    URI sourceUri,
    String producerMethodName,
    boolean isConstructor
  ) {
  }

  /**
   * Разрешить тип желудя по его имени или прозвищу.
   *
   * @return тип(ы) желудя; при конфликте имён предпочитаются помеченные
   *         {@code &Верховный}, иначе объединяются все кандидаты. Пусто, если
   *         желудь с таким именем не найден.
   */
  public TypeSet resolve(String name) {
    if (name.isBlank()) {
      return TypeSet.EMPTY;
    }
    ensureBuilt();
    var selected = selectCandidates(name);
    if (selected.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new LinkedHashSet<TypeRef>();
    for (var candidate : selected) {
      refs.add(candidate.type());
    }
    return TypeSet.of(refs);
  }

  /**
   * Разрешить объявления производителей желудя по его имени или прозвищу — для навигации к
   * месту объявления (конструктор класса-компонента или фабричный метод {@code &Завязь}).
   *
   * @param name Имя или прозвище желудя.
   * @return Объявления производителей; при конфликте имён предпочитаются помеченные
   *         {@code &Верховный}, иначе возвращаются все кандидаты. Пусто, если желудь не найден.
   */
  public List<BeanDeclaration> resolveDeclarations(String name) {
    if (name.isBlank()) {
      return List.of();
    }
    ensureBuilt();
    return selectCandidates(name);
  }

  /**
   * Разрешить объявления ВСЕХ производителей, подходящих под имя/прозвище, — без приоритета
   * {@code &Верховный}. Для навигации к членам прилепляемой коллекции, которой по контракту
   * нужны все подходящие желуди, а не один.
   *
   * @param name Имя или прозвище (квалификатор) желудя.
   * @return Объявления всех производителей под этим именем; пусто, если их нет.
   */
  public List<BeanDeclaration> resolveAllDeclarations(String name) {
    if (name.isBlank()) {
      return List.of();
    }
    ensureBuilt();
    return candidatesFor(name);
  }

  /**
   * Имена и прозвища желудей, зарегистрированные из указанного .os-файла.
   * <p>
   * Для обратной линзы: какие желуди объявляет этот документ-производитель — по ним ищутся
   * точки внедрения.
   *
   * @param uri URI .os-файла.
   * @return Имена желудей (lowercase) этого файла; пусто, если файл желудей не объявляет.
   */
  public Set<String> namesForUri(URI uri) {
    ensureBuilt();
    var names = namesByUri.get(uri);
    return names == null ? Set.of() : Set.copyOf(names);
  }

  /**
   * Фабричные желуди ({@code &Завязь}), объявленные в указанном файле, сгруппированные по
   * фабричному методу: имя метода → имена/прозвища производимого им желудя.
   * <p>
   * Для обратной линзы: над каждым методом {@code &Завязь} показываются точки внедрения именно
   * его желудя — отдельно от агрегатной линзы на конструкторе.
   *
   * @param uri URI .os-файла.
   * @return группы «фабричный метод → имена желудя»; пусто, если фабричных желудей в файле нет.
   */
  public List<FactoryMethodBean> factoryMethodBeansForUri(URI uri) {
    ensureBuilt();
    var names = namesByUri.get(uri);
    if (names == null) {
      return List.of();
    }
    Map<String, Set<String>> namesByMethod = new LinkedHashMap<>();
    for (var name : names) {
      for (var candidate : beansByName.getOrDefault(name, Set.of())) {
        if (uri.equals(candidate.sourceUri()) && !candidate.isConstructor()) {
          namesByMethod.computeIfAbsent(candidate.producerMethodName(), key -> new LinkedHashSet<>()).add(name);
        }
      }
    }
    return namesByMethod.entrySet().stream()
      .map(entry -> new FactoryMethodBean(entry.getKey(), Set.copyOf(entry.getValue())))
      .toList();
  }

  /**
   * Фабричный желудь файла для обратной линзы: метод {@code &Завязь} и имена производимого желудя.
   *
   * @param factoryMethodName Имя фабричного метода {@code &Завязь}.
   * @param beanNames         Имена/прозвища производимого желудя (ключи поиска точек внедрения).
   */
  public record FactoryMethodBean(String factoryMethodName, Set<String> beanNames) {
  }

  /**
   * Имена/прозвища компонентного желудя ({@code &Желудь}/{@code &Дуб}), объявленного в указанном
   * файле, — производитель которого его конструктор.
   * <p>
   * Для обратной линзы на конструкторе: фабричные желуди ({@code &Завязь}) сюда не входят — у них
   * собственные линзы на методах (см. {@link #factoryMethodBeansForUri(URI)}).
   *
   * @param uri URI .os-файла.
   * @return имена компонентного желудя файла (lowercase); пусто, если компонентного желудя нет.
   */
  public Set<String> componentBeanNamesForUri(URI uri) {
    ensureBuilt();
    var names = namesByUri.get(uri);
    if (names == null) {
      return Set.of();
    }
    var result = new LinkedHashSet<String>();
    for (var name : names) {
      for (var candidate : beansByName.getOrDefault(name, Set.of())) {
        if (uri.equals(candidate.sourceUri()) && candidate.isConstructor()) {
          result.add(name);
        }
      }
    }
    return Set.copyOf(result);
  }

  /** Все объявления, зарегистрированные под именем/прозвищем (lowercase), либо пустой список. */
  private List<BeanDeclaration> candidatesFor(String name) {
    var candidates = beansByName.get(name.toLowerCase(Locale.ROOT));
    return candidates == null || candidates.isEmpty() ? List.of() : List.copyOf(candidates);
  }

  /**
   * Выбрать объявления по имени с учётом приоритета {@code &Верховный}: при наличии хотя бы
   * одного приоритетного возвращаются только приоритетные, иначе — все объявления имени.
   */
  private List<BeanDeclaration> selectCandidates(String name) {
    var candidates = candidatesFor(name);
    var primaryCandidates = candidates.stream()
      .filter(BeanDeclaration::primary)
      .toList();
    return primaryCandidates.isEmpty() ? candidates : primaryCandidates;
  }

  @Override
  protected void clearIndex() {
    beansByName.clear();
    namesByUri.clear();
  }

  /** Удалить из индекса все кандидаты, зарегистрированные из указанного .os-файла. */
  @Override
  protected void removeByUri(URI uri) {
    var names = namesByUri.remove(uri);
    if (names == null) {
      return;
    }
    for (var name : names) {
      beansByName.computeIfPresent(name, (key, candidates) -> {
        candidates.removeIf(candidate -> uri.equals(candidate.sourceUri()));
        return candidates.isEmpty() ? null : candidates; // null => удалить ключ
      });
    }
  }

  @Override
  protected void indexClass(DocumentContext document, List<LibraryEntry> classEntries, URI uri) {
    var symbolTree = document.getSymbolTree();
    // Аннотации компонента (&Желудь/&Дуб) размещаются исключительно над конструктором.
    symbolTree.getConstructor().ifPresent(constructor -> {
      var constructorAnnotations = constructor.getAnnotations();
      // Класс-определение пользовательской аннотации (&Аннотация("Имя")) — не желудь:
      // его конструкторные аннотации нужны лишь для разворачивания мета-аннотаций.
      if (AutumnAnnotations.find(constructorAnnotations, AutumnAnnotations.ANNOTATION_MARKER).isPresent()) {
        return;
      }
      for (var entry : classEntries) {
        typeRegistry.resolve(entry.qualifiedName()).ifPresent(ownerType ->
          registerComponent(constructorAnnotations, entry.qualifiedName(), ownerType, uri, constructor.getName()));
      }
      // &Завязь размещается над методом и допустима только в классе-дубе.
      if (metaAnnotationResolver.hasRole(constructorAnnotations, AutumnAnnotations.OAK)) {
        for (var method : symbolTree.getMethods()) {
          registerFactory(method, uri);
        }
      }
    });
  }

  private void registerComponent(List<Annotation> annotations, String defaultName, TypeRef ownerType, URI uri,
                                 String constructorName) {
    // &Желудь либо &Дуб: класс является желудём (дуб сам по себе тоже желудь).
    COMPONENT_ROLES.stream()
      .flatMap(role -> metaAnnotationResolver.findByRole(annotations, role).stream()
        .map(component -> Map.entry(role, component)))
      .findFirst()
      .ifPresent(match -> {
        // Имя желудя берётся с аннотации роли (&Желудь/&Дуб) в развёрнутой цепочке,
        // а не с алиаса: &Контроллер("/") — это &Желудь без имени → имя класса, "/" маршрут.
        // Фоллбэк на имя класса — когда имя НЕ задано (список пуст), как в autumn.
        var name = metaAnnotationResolver.roleValues(match.getValue(), match.getKey()).stream()
          .findFirst()
          .orElse(defaultName);
        register(annotations, name, ownerType, uri, constructorName, true);
      });
  }

  private void registerFactory(MethodSymbol method, URI uri) {
    var annotations = method.getAnnotations();
    metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.FACTORY).ifPresent(factory -> {
      // Как в autumn (ФабрикаЖелудей.ПрочитатьИмяЖелудя/ПрочитатьТипЖелудя): переданные
      // Значение/Тип берутся как есть, при их отсутствии — имя метода. Тип резолвится по
      // имени; пустой/неизвестный тип не резолвится → желудь не регистрируется. Спец-обработки
      // Тип="Желудь" у &Завязь нет (это семантика &Пластилин).
      var name = metaAnnotationResolver.roleValues(factory, AutumnAnnotations.FACTORY).stream()
        .findFirst()
        .orElse(method.getName());
      var typeName = metaAnnotationResolver
        .roleParameterValues(factory, AutumnAnnotations.FACTORY, AutumnAnnotations.TYPE_PARAMETER).stream()
        .findFirst()
        .orElse(method.getName());
      typeRegistry.resolve(typeName)
        .ifPresent(beanType ->
          register(annotations, name, beanType, uri, method.getName(), false));
    });
  }

  private void register(List<Annotation> annotations, String primaryName, TypeRef type, URI uri,
                        String producerMethodName, boolean isConstructor) {
    var primary = metaAnnotationResolver.hasRole(annotations, AutumnAnnotations.PRIMARY);
    var candidate = new BeanDeclaration(type, primary, uri, producerMethodName, isConstructor);

    addCandidate(uri, primaryName, candidate);
    for (var alias : metaAnnotationResolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER)) {
      addCandidate(uri, alias, candidate);
    }
  }

  private void addCandidate(URI uri, String name, BeanDeclaration candidate) {
    var key = name.toLowerCase(Locale.ROOT);
    beansByName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(candidate);
    namesByUri.computeIfAbsent(uri, u -> ConcurrentHashMap.newKeySet()).add(key);
  }
}
