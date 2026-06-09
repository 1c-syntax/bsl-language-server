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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обратный индекс точек внедрения фреймворка «ОСень»: отображение «имя желудя → точки внедрения»
 * ({@code &Пластилин} на полях и параметрах конструктора).
 * <p>
 * Зеркало {@link AutumnBeanIndex}: сканирует те же .os-классы библиотек (настоящие потребители
 * желудей — это классы-желуди), но собирает не объявления производителей, а места внедрения.
 * Используется обратной линзой «производитель → точки внедрения». Имя внедряемого желудя
 * вычисляется через {@link AutumnComponentInferencer#injectedBean} — единый источник правды
 * с прямой линзой и выводом типа. Общая обвязка ленивой сборки — в {@link AbstractAutumnLibraryIndex}.
 */
@Component
@WorkspaceScope
public class AutumnInjectionPointIndex extends AbstractAutumnLibraryIndex {

  private final AutumnComponentInferencer componentInferencer;

  /** Имя желудя (lowercase) → точки внедрения, ссылающиеся на него. */
  private final Map<String, Set<InjectionPoint>> pointsByName = new ConcurrentHashMap<>();
  /** URI .os-файла → имена, под которыми он зарегистрировал точки (для точечного удаления). */
  private final Map<URI, Set<String>> namesByUri = new ConcurrentHashMap<>();

  public AutumnInjectionPointIndex(OScriptLibraryIndex libraryIndex,
                                   ServerContextProvider serverContextProvider,
                                   AutumnComponentInferencer componentInferencer) {
    super(libraryIndex, serverContextProvider);
    this.componentInferencer = componentInferencer;
  }

  /**
   * Точка внедрения: .os-файл потребителя и диапазон поля/параметра с {@code &Пластилин}.
   *
   * @param uri   URI .os-файла потребителя.
   * @param range Диапазон имени поля или параметра — точки внедрения.
   */
  public record InjectionPoint(URI uri, Range range) {
  }

  /**
   * Разрешить точки внедрения желудя по его имени или прозвищу.
   *
   * @param name Имя или прозвище (квалификатор) желудя.
   * @return Точки внедрения, ссылающиеся на это имя; пусто, если их нет.
   */
  public List<InjectionPoint> resolve(String name) {
    if (name.isBlank()) {
      return List.of();
    }
    ensureBuilt();
    var points = pointsByName.get(name.toLowerCase(Locale.ROOT));
    return points == null || points.isEmpty() ? List.of() : List.copyOf(points);
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
      pointsByName.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(new InjectionPoint(uri, range));
      namesByUri.computeIfAbsent(uri, u -> ConcurrentHashMap.newKeySet()).add(key);
    });
  }
}
