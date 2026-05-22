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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests: конструкторы платформенных классов oscript-stdlib
 * прокидываются в SignatureHelp / Hover / Completion.
 */
@CleanupContextBeforeClassAndAfterClass
class PlatformConstructorIntegrationTest extends AbstractServerContextAwareTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Autowired
  private HoverProvider hoverProvider;

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void signatureHelpOnPlatformConstructor() {
    initServerContext();
    var content = "А = Новый Массив();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.indexOf('(') + 1));

    var help = signatureHelpProvider.getSignatureHelp(dc, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).isNotEmpty();
  }

  @Test
  void hoverOnPlatformClassNameMatchesArity() {
    initServerContext();
    var content = "А = Новый Массив(2, 3);\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    // позиция на имени класса
    params.setPosition(new Position(0, content.indexOf("Массив") + 1));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("Новый Массив(");
    // 2 аргумента не подходят ни под один вариант (0/1/1) — должен быть disclaimer
    assertThat(value).contains("Не найдено описание");
  }

  @Test
  void hoverOnPlatformClassNameZeroArity() {
    initServerContext();
    var content = "А = Новый Массив();\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.indexOf("Массив") + 1));

    var hover = hoverProvider.getHover(dc, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("Новый Массив(");
    assertThat(value).doesNotContain("Не найдено описание");
  }

  @Test
  void completionAfterNewIncludesPlatformClassDetail() {
    initServerContext();
    var content = "А = Новый Масс";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(0, content.length()));

    var items = completionProvider.getCompletion(dc, params).getItems();

    assertThat(items)
      .extracting(org.eclipse.lsp4j.CompletionItem::getLabel)
      .contains("Массив");
    var massiv = items.stream()
      .filter(i -> "Массив".equals(i.getLabel()))
      .findFirst()
      .orElseThrow();
    // detail должно быть проставлено хотя бы для конструктора с параметрами
    // первый вариант — "По умолчанию" с пустыми параметрами => "()"
    assertThat(massiv.getDetail()).isNotNull();
  }
}
