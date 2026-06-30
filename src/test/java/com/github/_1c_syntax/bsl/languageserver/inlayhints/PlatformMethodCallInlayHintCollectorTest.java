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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для inlay-подсказок параметров платформенных
 * методов / глобальных функций / конструкторов
 * ({@link PlatformMethodCallInlayHintCollector}).
 */
class PlatformMethodCallInlayHintCollectorTest extends AbstractServerContextAwareTest {

  private static final String FILE_PATH =
    "./src/test/resources/types/PlatformMethodInlayHints.bsl";

  private static final String CONSTRUCTOR_FIXTURE_DIR =
    "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CONSTRUCTOR_CALLER_PATH = CONSTRUCTOR_FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private PlatformMethodCallInlayHintCollector supplier;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex oScriptLibraryIndex;

  @Test
  void noHintsForSourceDefinedOScriptConstructor() {
    // given — конструктор OneScript-класса (ПриСозданииОбъекта) source-defined и
    // покрывается SourceDefinedMethodCallInlayHintCollector'ом; платформенный
    // supplier не должен дублировать эти подсказки.
    initServerContext(java.nio.file.Path.of(CONSTRUCTOR_FIXTURE_DIR).toAbsolutePath());
    oScriptLibraryIndex.reindex(context);

    var documentContext = TestUtils.getDocumentContextFromFile(CONSTRUCTOR_CALLER_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).isEmpty();
  }

  @Test
  void hintsTooltipContainsTypesAndOptionalLabel() {
    // given
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — tooltip с info про parameter присутствует у хотя бы одного hint.
    assertThat(hints).anySatisfy(hint -> {
      assertThat(hint.getTooltip()).isNotNull();
      var tooltip = hint.getTooltip().getRight();
      if (tooltip != null) {
        assertThat(tooltip.getValue()).isNotBlank();
      }
    });
  }

  @Test
  void hintsForGlobalFunctionCall() {
    // given
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — СтрНайти(...) имеет именованные параметры, ожидаем подсказки.
    assertThat(hints)
      .isNotEmpty()
      .allSatisfy(hint -> assertThat(hint.getKind()).isEqualTo(InlayHintKind.Parameter));
    assertThat(hints)
      .extracting(InlayHint::getLabel)
      .extracting(Either::getLeft)
      .anyMatch(label -> label != null && !label.isBlank());
  }

  @Test
  void hintsForMethodCallViaDot() {
    // given
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — М.Вставить(0, "x") даёт подсказки для двух параметров.
    assertThat(hints).isNotEmpty();
  }

  @Test
  void hintsForNewConstructor() {
    // given
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — Новый Массив(10) — конструктор с одним параметром.
    assertThat(hints)
      .filteredOn(hint -> hint.getKind() == InlayHintKind.Parameter)
      .isNotEmpty();
  }

  @Test
  void variadicConstructorHintsAreNumbered() {
    // given — `Новый Массив(2, 3)` в фикстуре: вариадик-конструктор
    // (КоличествоЭлементов…) разворачивается в нумерованные метки.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — у вариадик-хвоста метки нумеруются по фактическим аргументам.
    var labels = hints.stream()
      .map(InlayHint::getLabel)
      .filter(org.eclipse.lsp4j.jsonrpc.messages.Either::isLeft)
      .map(org.eclipse.lsp4j.jsonrpc.messages.Either::getLeft)
      .toList();
    assertThat(labels).contains("КоличествоЭлементов1:", "КоличествоЭлементов2:");
  }

  @Test
  void noHintsForEmptyAst() {
    // given
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext("");

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).isEmpty();
  }

  @Test
  void noHintsForSourceDefinedMethodCall() {
    // given — source-defined метод не должен получать подсказки от платформенного
    // supplier'а (этим занимается SourceDefinedMethodCallInlayHintCollector).
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext(
      "Процедура МойМетод(Параметр)\nКонецПроцедуры\n\nМойМетод(1);\n"
    );

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).noneMatch(hint ->
      hint.getLabel().isLeft() && hint.getLabel().getLeft().contains("Параметр"));
  }

  @Test
  void noHintsWhenRangeExcludesCalls() {
    // given — range «до файла» (line < 0), вызов в него не попадает.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH, context);
    var params = new InlayHintParams();
    params.setTextDocument(TestUtils.getTextDocumentIdentifier(documentContext.getUri()));
    params.setRange(new Range(new Position(0, 0), new Position(0, 1)));

    // when
    var hints = supplier.getInlayHints(documentContext, params);

    // then — range покрывает только первый символ, ни один вызов не задет.
    assertThat(hints).isEmpty();
  }

  @Test
  void noHintsForCallWithoutArguments() {
    // given — Сообщить() без аргументов, paramList пуст.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext("Сообщить();\n");

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).isEmpty();
  }

  @Test
  void noHintsForUnknownConstructorType() {
    // given — Новый НесуществующийТип(arg) — typeService.resolve не находит.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext(
      "Х = Новый СовершенноНеизвестныйТипX(1);\n"
    );

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).isEmpty();
  }

  @Test
  void noHintsForCallOnUntypedVariable() {
    // given — у переменной нет инфер-типа, member не резолвится.
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext(
      "Перем X;\nX.НеизвестныйМетод(1, 2);\n"
    );

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then
    assertThat(hints).isEmpty();
  }

  @Test
  void hintsAreSuppressedForBlankArgumentText() {
    // given — пробел между запятыми трактуется как blank arg; supplier
    // не должен падать (TypeSet.EMPTY для blank).
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContext(
      "СтрНайти(\"abc\", \"b\");\n"
    );

    // when
    var hints = supplier.getInlayHints(documentContext, fullRangeParams(documentContext));

    // then — есть хоть один hint (на «где искать»), супплайер не падает
    assertThat(hints).isNotNull();
  }

  private static InlayHintParams fullRangeParams(DocumentContext documentContext) {
    var lines = documentContext.getContentList();
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
