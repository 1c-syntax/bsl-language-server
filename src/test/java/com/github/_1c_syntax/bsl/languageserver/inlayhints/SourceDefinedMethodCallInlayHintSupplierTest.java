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
class SourceDefinedMethodCallInlayHintSupplierTest extends AbstractServerContextAwareTest {

  private final static String FILE_PATH = "./src/test/resources/inlayhints/SourceDefinedMethodCallInlayHintSupplier.bsl";

  private static final String CONSTRUCTOR_FIXTURE_DIR =
    "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CONSTRUCTOR_CALLER_PATH = CONSTRUCTOR_FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private SourceDefinedMethodCallInlayHintSupplier supplier;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private OScriptLibraryIndex oScriptLibraryIndex;

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
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("PlayersHealth:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Amount:"));
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
      supplier.getId(),
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
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Player:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 14));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Player**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("PlayersHealth:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Amount:"));
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
      .filter(hint -> hint.getLabel().isLeft())
      .filter(hint -> {
        var label = hint.getLabel().getLeft();
        return "PlayersHealth:".equals(label) || "Amount:".equals(label);
      })
      .toList();
    assertThat(changeHealthHints).hasSize(16);
    assertThat(changeHealthHints)
      .extracting(InlayHint::getPosition)
      .doesNotHaveDuplicates();
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
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Имя:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Значение:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
      })
    ;
  }


}
