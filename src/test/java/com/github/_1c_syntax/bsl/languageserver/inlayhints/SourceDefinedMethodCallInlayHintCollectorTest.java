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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class SourceDefinedMethodCallInlayHintCollectorTest extends AbstractServerContextAwareTest {

  private static final String FILE_PATH = "./src/test/resources/inlayhints/SourceDefinedMethodCallInlayHintSupplier.bsl";

  private static final String CONSTRUCTOR_FIXTURE_DIR =
    "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CONSTRUCTOR_CALLER_PATH = CONSTRUCTOR_FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private SourceDefinedMethodCallInlayHintCollector supplier;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private OScriptLibraryIndex oScriptLibraryIndex;

  private static String labelValue(InlayHint inlayHint) {
    return inlayHint.getLabel().getRight().getFirst().getValue();
  }

  @Test
  void testDefaultInlayHints() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = firstMethod.getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(2)
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("PlayersHealth:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("Amount:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 32));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Amount**: ");
      })
    ;
  }

  @Test
  void testInlayHintsShowParametersWithTheSameName() {

    // given
    configuration.getInlayHintOptions().getParameters().put(
      "methodCall",
      Either.forRight(Map.of("showParametersWithTheSameName", true))
    );

    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = firstMethod.getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(3)
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("Player:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 14));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Player**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("PlayersHealth:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("Amount:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 32));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Amount**: ");
      })
    ;
  }
  @Test
  void testHintsAreEmittedForEveryCallSiteOfSameMethod() {

    // given — ChangeHealth(...) вызывается в нескольких процедурах файла; диапазон
    // охватывает весь документ, поэтому в выборку попадают все вызовы метода.
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var lines = documentContext.getContentList();
    var lastLine = Math.max(0, lines.length - 1);
    var fullRange = new Range(
      new Position(0, 0),
      Ranges.create(lastLine, 0, lines[lastLine].length()).getEnd()
    );

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, fullRange);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then — каждый из восьми вызовов ChangeHealth(...) даёт по две подсказки
    // (PlayersHealth и Amount), а позиции подсказок уникальны (вызовы не схлопнулись).
    var changeHealthHints = inlayHints.stream()
      .filter(hint -> hint.getLabel().isRight())
      .filter(hint -> {
        var label = labelValue(hint);
        return "PlayersHealth:".equals(label) || "Amount:".equals(label);
      })
      .toList();
    assertThat(changeHealthHints).hasSize(16);
    assertThat(changeHealthHints)
      .extracting(InlayHint::getPosition)
      .doesNotHaveDuplicates();
  }

  @Test
  void testEmptyArgumentShowsDefaultValueHint() {

    // given — вызов метода с единственным пустым доводом: парсер формирует один
    // пустой callParam, и при включённом показе значений по умолчанию подсказка
    // содержит имя параметра и его значение по умолчанию.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Внешняя()
          Внутренняя();
      КонецПроцедуры

      Процедура Внутренняя(Знач Параметр = 1)
      КонецПроцедуры
      """);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = documentContext.getSymbolTree().getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .extracting(SourceDefinedMethodCallInlayHintCollectorTest::labelValue)
      .containsExactly("Параметр (1)");
  }

  @Test
  void testFewerArgumentsThanParametersStopAtLastPassedArgument() {

    // given — аргументов меньше, чем параметров: обход прерывается на последнем
    // переданном доводе, для отсутствующих доводов подсказки не формируются.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Внешняя()
          Внутренняя(5);
      КонецПроцедуры

      Процедура Внутренняя(Знач Первый, Знач Второй, Знач Третий)
      КонецПроцедуры
      """);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = documentContext.getSymbolTree().getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then — подсказка только для переданного первого довода.
    assertThat(inlayHints)
      .extracting(SourceDefinedMethodCallInlayHintCollectorTest::labelValue)
      .containsExactly("Первый:");
  }

  @Test
  void testRangeWithoutMethodCallsProducesNoHints() {

    // given — диапазон не охватывает ни одного вызова метода: ссылок нет,
    // поэтому возвращается пустой список.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Внешняя()
          Перем ЛокальнаяПеременная;
      КонецПроцедуры
      """);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, new Range(new Position(0, 0), new Position(0, 0)));

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints).isEmpty();
  }

  @Test
  void testSkippedArgumentShowsDefaultValueHint() {

    // given — пропущенный первый аргумент при включённом показе значений по умолчанию:
    // подсказка содержит имя параметра и его значение по умолчанию.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Внешняя()
          Внутренняя(, 5);
      КонецПроцедуры

      Процедура Внутренняя(Знач Первый = 42, Знач Второй = 0)
      КонецПроцедуры
      """);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = documentContext.getSymbolTree().getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .extracting(SourceDefinedMethodCallInlayHintCollectorTest::labelValue)
      .contains("Первый (42)");
  }

  @Test
  void testConstructorCallInlayHints() {

    // given
    initServerContext(Path.of(CONSTRUCTOR_FIXTURE_DIR).toAbsolutePath());
    oScriptLibraryIndex.reindex(context);

    var documentContext = TestUtils.getDocumentContextFromFile(CONSTRUCTOR_CALLER_PATH, context);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = documentContext.getSymbolTree().getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(2)
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("Имя:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
      .anySatisfy(inlayHint -> {
        assertThat(labelValue(inlayHint)).isEqualTo("Значение:");
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
    ;
  }

  @Test
  void testHintLabelPartLinksToParameterDeclarationEagerly() {

    // given — объявление параметра известно на этапе построения хинта, поэтому
    // ссылка проставляется жадно, а хинт не несёт data (резолв не нужен).
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var symbolTree = documentContext.getSymbolTree();
    var changeHealth = symbolTree.getMethods().stream()
      .filter(method -> "ChangeHealth".equalsIgnoreCase(method.getName()))
      .findFirst()
      .orElseThrow();
    var playersHealthParameter = changeHealth.getParameters().get(1);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = symbolTree.getMethods().getFirst().getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then — у части метки PlayersHealth ссылка указывает на диапазон объявления
    // параметра в сигнатуре вызываемого метода, а data не заполнена.
    assertThat(inlayHints)
      .filteredOn(inlayHint -> "PlayersHealth:".equals(labelValue(inlayHint)))
      .singleElement()
      .satisfies(inlayHint -> {
        InlayHintLabelPart labelPart = inlayHint.getLabel().getRight().getFirst();
        assertThat(labelPart.getLocation()).isNotNull();
        assertThat(labelPart.getLocation().getUri()).isEqualTo(documentContext.getUri().toString());
        assertThat(labelPart.getLocation().getRange()).isEqualTo(playersHealthParameter.getRange());
        assertThat(inlayHint.getData()).isNull();
      });
  }

}
