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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для inlay-подсказок параметров платформенных
 * методов / глобальных функций / конструкторов
 * ({@link PlatformMethodCallInlayHintSupplier}).
 */
class PlatformMethodCallInlayHintSupplierTest extends AbstractServerContextAwareTest {

  private static final String FILE_PATH =
    "./src/test/resources/types/PlatformMethodInlayHints.bsl";

  @Autowired
  private PlatformMethodCallInlayHintSupplier supplier;

  @Test
  void hintsForGlobalFunctionCall() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // СтрНайти("abc", "b", НаправлениеПоиска.СНачала, 1) — параметры имеют имена,
    // которые подмешиваются как inlay-метки.
    assertThat(hints)
      .isNotEmpty()
      .allSatisfy(hint -> assertThat(hint.getKind()).isEqualTo(InlayHintKind.Parameter));

    // Хотя бы одна метка относится к СтрНайти
    assertThat(hints)
      .extracting(InlayHint::getLabel)
      .extracting(either -> either.getLeft())
      .anyMatch(label -> label != null && !label.isBlank());
  }

  @Test
  void hintsForMethodCallViaDot() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // М.Вставить(0, "x") — два аргумента, ожидаем подсказки имён параметров.
    assertThat(hints).isNotEmpty();
  }

  @Test
  void hintsForNewConstructor() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // Новый Массив(10) — конструктор с 1 параметром.
    assertThat(hints)
      .filteredOn(h -> h.getKind() == InlayHintKind.Parameter)
      .isNotEmpty();
  }

  @Test
  void noHintsForEmptyAst() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext("");

    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    assertThat(hints).isEmpty();
  }

  @Test
  void noHintsForSourceDefinedMethodCall() {
    // Source-defined метод (в модуле есть его описание) НЕ должен получать
    // inlay-подсказки от PlatformMethodCallInlayHintSupplier — он отдаётся
    // отдельным SourceDefinedMethodCallInlayHintSupplier.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext(
      "Процедура МойМетод(Параметр)\nКонецПроцедуры\n\nМойМетод(1);\n"
    );

    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // Платформенный supplier ничего не возвращает для локального метода.
    assertThat(hints).noneMatch(h ->
      h.getLabel().isLeft() && h.getLabel().getLeft().contains("Параметр"));
  }

  private static InlayHintParams fullRangeParams(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext
  ) {
    var content = documentContext.getContent();
    var lines = content.split("\\R", -1);
    var lastLine = Math.max(0, lines.length - 1);
    var lastCol = lines[lastLine].length();
    var range = new Range(
      Ranges.create(0, 0, 1).getStart(),
      Ranges.create(lastLine, 0, lastCol).getEnd()
    );
    var params = new InlayHintParams();
    params.setTextDocument(TestUtils.getTextDocumentIdentifier(documentContext.getUri()));
    params.setRange(range);
    return params;
  }
}
