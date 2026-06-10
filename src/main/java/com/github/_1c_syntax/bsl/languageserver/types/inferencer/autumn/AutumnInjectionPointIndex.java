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
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex.ProducerKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обратный индекс точек внедрения фреймворка «ОСень»: где какой желудь внедряется
 * ({@code &Пластилин} на полях и параметрах конструктора).
 * <p>
 * Зеркало {@link AutumnBeanIndex}: сканирует те же .os-классы библиотек (потребители желудей —
 * это классы-желуди) и для каждой точки внедрения запоминает имя внедряемого желудя (через
 * {@link AutumnComponentInferencer#injectedBean} — единый источник правды с прямой линзой и
 * выводом типа) и признак внедрения коллекции.
 * <p>
 * Для обратной линзы важна точность по <i>производителю</i>, а не только по имени: при конфликте
 * имён одиночное внедрение {@code &Пластилин("Имя")} ведёт лишь к выбранному правилами DI
 * производителю (приоритет {@code &Верховный}), а коллекция — ко всем. Поэтому
 * {@link #usagesOf} «переигрывает» выбор производителя на каждую точку через живой
 * {@link AutumnBeanIndex} (всегда актуальный — без рассинхрона с инкрементальными апдейтами).
 * Общая обвязка ленивой сборки — в {@link AbstractAutumnLibraryIndex}.
 */
@Component
@WorkspaceScope
public class AutumnInjectionPointIndex extends AbstractAutumnLibraryIndex {

  private final AutumnComponentInferencer componentInferencer;
  private final AutumnBeanIndex beanIndex;

  /** Имя желудя (lowercase) → точки внедрения, ссылающиеся на него. */
  private final Map<String, Set<InjectionPoint>> pointsByName = new ConcurrentHashMap<>();
  /** URI .os-файла → имена, под которыми он зарегистрировал точки (для точечного удаления). */
  private final Map<URI, Set<String>> namesByUri = new ConcurrentHashMap<>();

  public AutumnInjectionPointIndex(OScriptLibraryIndex libraryIndex,
                                   ServerContextProvider serverContextProvider,
                                   AutumnComponentInferencer componentInferencer,
                                   AutumnBeanIndex beanIndex) {
    super(libraryIndex, serverContextProvider);
    this.componentInferencer = componentInferencer;
    this.beanIndex = beanIndex;
  }

  /**
   * Точка внедрения: .os-файл потребителя, диапазон поля/параметра с {@code &Пластилин} и признак
   * внедрения прилепляемой коллекции (коллекция внедряет всех подходящих производителей, одиночное
   * внедрение — лишь выбранного).
   *
   * @param uri        URI .os-файла потребителя.
   * @param range      Диапазон имени поля или параметра — точки внедрения.
   * @param collection {@code true}, если это внедрение прилепляемой коллекции.
   */
  public record InjectionPoint(URI uri, Range range, boolean collection) {
  }

  /**
   * Точки внедрения, которые разрешаются именно в указанного производителя.
   * <p>
   * Для каждой точки переигрывается выбор производителя: одиночное внедрение по имени
   * включается, только если правила DI ({@link AutumnBeanIndex#resolveDeclarations}) выбирают
   * именно этого производителя (учитывая {@code &Верховный}); внедрение коллекции включается
   * всегда (коллекция внедряет всех производителей подходящего имени, включая этого).
   *
   * @param producerUri       URI .os-файла производителя.
   * @param factoryMethodName Имя фабричного метода {@code &Завязь} либо {@code null}, если
   *                          производитель — компонентный желудь ({@code &Желудь}/{@code &Дуб}).
   * @param beanNames         Имена/прозвища желудя, производимого этим производителем.
   * @return точки внедрения, разрешающиеся в этого производителя; пусто, если их нет.
   */
  public List<InjectionPoint> usagesOf(URI producerUri, @Nullable String factoryMethodName, Set<String> beanNames) {
    ensureBuilt();
    var result = new LinkedHashSet<InjectionPoint>();
    for (var beanName : beanNames) {
      var points = pointsByName.get(beanName.toLowerCase(Locale.ROOT));
      if (points == null || points.isEmpty()) {
        continue;
      }
      var singletonResolvesToProducer = producesName(beanName, producerUri, factoryMethodName);
      for (var point : points) {
        if (point.collection() || singletonResolvesToProducer) {
          result.add(point);
        }
      }
    }
    return List.copyOf(result);
  }

  /** Выбирают ли правила DI (с приоритетом {@code &Верховный}) для имени именно этого производителя. */
  private boolean producesName(String beanName, URI producerUri, @Nullable String factoryMethodName) {
    return beanIndex.resolveDeclarations(beanName).stream()
      .anyMatch(declaration -> producerUri.equals(declaration.sourceUri())
        && (factoryMethodName == null
          ? declaration.kind() == ProducerKind.COMPONENT
          : declaration.kind() == ProducerKind.FACTORY
            && factoryMethodName.equals(declaration.factoryMethodName())));
  }

  @Override
  protected void clearIndex() {
    pointsByName.clear();
    namesByUri.clear();
  }

  @Override
  protected void indexClass(DocumentContext document, List<LibraryEntry> classEntries, URI uri) {
    if (isAnnotationDefinition(document)) {
      return;
    }
    var symbolTree = document.getSymbolTree();

    symbolTree.getConstructor().ifPresent(constructor ->
      constructor.getParameters().forEach(parameter ->
        indexInjection(uri, parameter.getAnnotations(), parameter.getName(), parameter.getRange())));

    for (var variable : symbolTree.getVariables()) {
      indexInjection(uri, variable.getAnnotations(), variable.getName(), variable.getVariableNameRange());
    }
  }

  @Override
  protected void removeByUri(URI uri) {
    var names = namesByUri.remove(uri);
    if (names == null) {
      return;
    }
    for (var name : names) {
      pointsByName.computeIfPresent(name, (key, points) -> {
        points.removeIf(point -> uri.equals(point.uri()));
        return points.isEmpty() ? null : points;
      });
    }
  }

  private void indexInjection(URI uri, List<Annotation> annotations, String memberName, Range range) {
    componentInferencer.injectedBean(annotations, memberName).ifPresent(bean -> {
      var key = bean.name().toLowerCase(Locale.ROOT);
      pointsByName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
        .add(new InjectionPoint(uri, range, bean.collection()));
      namesByUri.computeIfAbsent(uri, u -> ConcurrentHashMap.newKeySet()).add(key);
    });
  }
}
