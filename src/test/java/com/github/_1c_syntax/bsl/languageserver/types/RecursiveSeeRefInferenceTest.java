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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #4194: рекурсивный авто-комплит. Самоссылающаяся структура (узел дерева, поле
 * которого — {@code Массив из см. ЭтотЖеМетод}) должна разворачиваться на любую
 * глубину навигации, ограниченную лишь выражением под курсором.
 */
@CleanupContextBeforeClassAndAfterClass
class RecursiveSeeRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOverRecursiveTreeCutsElementCycleWithSeeRef() {
    // Hover по узлу дерева (поле Потомки — Массив из см. ДеревоУзел): разворот
    // вложенных полей завершается, а повторный вход в элемент-цикл рендерится
    // ссылкой `См. ДеревоУзел` (а не уходит в бесконечную рекурсию).
    var dc = doc();
    var content = dc.getContent();
    int offset = content.indexOf("Узел = Корень.Потомки[0]");
    int lineStart = content.lastIndexOf('\n', offset) + 1;
    int line = content.substring(0, offset).split("\n", -1).length - 1;
    // позиция внутри идентификатора `Узел`
    var params = new HoverParams(new TextDocumentIdentifier(dc.getUri().toString()),
      new Position(line, offset - lineStart + 1));
    var hover = hoverProvider.getHover(dc, params)
      .map(h -> h.getContents().getRight().getValue())
      .orElse("");

    assertThat(hover)
      .as("hover рекурсивного дерева показывает поля и обрывает элемент-цикл ссылкой")
      .contains("Значение")
      .contains("Потомки")
      .containsPattern("См\\. ДеревоУзел");
  }

  @Test
  void deepNavigationThroughRecursiveSeeRefResolves() {
    // Корень.Потомки[0].Потомки[0].Значение — два уровня рекурсии и финальное поле.
    var t = at("Глубоко = Корень.Потомки[0].Потомки[0].Значение", "Глубоко = ".length() + 1);

    assertThat(t.refs())
      .as("глубокая навигация по рекурсивной см.-ссылке разрешается до Строки")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Строка");
  }

  @Test
  void intermediateRecursiveLevelExposesFields() {
    // Узел = Корень.Потомки[0] — должен снова быть узлом дерева (Структура с
    // полями Значение/Потомки): именно это даёт автокомплит после `Узел.`.
    var dc = doc();
    var method = dc.getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase("Пример"))
      .findFirst()
      .orElseThrow();
    var node = dc.getSymbolTree().getVariableSymbol("Узел", method).orElseThrow();

    var t = inferencer.inferSymbol(node);

    assertThat(t.getAllFieldNames())
      .as("промежуточный уровень рекурсии раскрывается в узел с полями Значение/Потомки")
      .contains("Значение", "Потомки");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    assertThat(markerStart)
      .as("Marker must exist in fixture: %s", marker)
      .isGreaterThanOrEqualTo(0);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/RecursiveSeeRef.bsl");
  }
}
