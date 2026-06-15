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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Реальное (не синтетическое) описание возврата-{@code Структура} из БСП
 * (ЭлектроннаяПодписьКлиентСервер.НовыеСвойстваПодписи): completion после точки
 * и hover по переменной должны видеть все поля структуры. Поля заданы в JsDoc,
 * часть — с многострочным составным типом (std453 п.5.3), что регрессировало в
 * разборе bsl-parser (см. bsl-parser#390, 0.36.0).
 */
@CleanupContextBeforeClassAndAfterClass
class RealReturnStructureCompletionTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;
  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void completionAfterDotExposesAllStructureFields() {
    // given: переменная из вызова функции с JsDoc-возвратом Структура.
    var dc = realDoc();
    var content = dc.getContent();
    var afterDot = content.indexOf("Свойства.", content.indexOf("X = Свойства.")) + "Свойства.".length();
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(positionAtOffset(content, afterDot));

    // when: автокомплит после точки.
    var labels = completionProvider.getCompletion(dc, params).getItems().stream()
      .map(CompletionItem::getLabel)
      .toList();

    // then: предлагаются все поля структуры.
    assertThat(labels).contains(
      "Подпись",
      "Комментарий",
      "ДатаПодписи",
      "ОписаниеСертификата",
      "РезультатПроверкиПодписиПоМЧД",
      "ТипПодписи");
  }

  @Test
  void hoverOnVariableExposesStructureFields() {
    // given: позиция на переменной-структуре.
    var dc = realDoc();
    var content = dc.getContent();
    var varOffset = content.indexOf("Свойства = НовыеСвойстваПодписи()") + 1;
    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(positionAtOffset(content, varOffset));

    // when: hover по переменной.
    var hoverText = hoverProvider.getHover(dc, params)
      .map(hover -> hover.getContents().getRight().getValue())
      .orElse("");

    // then: поля и их текстовые описания из JsDoc возврата функции.
    assertThat(hoverText)
      .contains("ОписаниеСертификата")
      .contains("РезультатПроверкиПодписиПоМЧД")
      .contains("комментарий, если он был введен при подписании")
      .contains("результат подписания");
  }

  @Test
  void hoverOnFieldExposesFieldDescription() {
    // given: позиция на имени поля в "X = Свойства.Подпись".
    var dc = realDoc();
    var content = dc.getContent();
    var dotField = content.indexOf(".Подпись") + 1;
    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(positionAtOffset(content, dotField));

    // when: hover по полю.
    var hoverText = hoverProvider.getHover(dc, params)
      .map(hover -> hover.getContents().getRight().getValue())
      .orElse("");

    // then: показывается описание поля.
    assertThat(hoverText).contains("результат подписания");
  }

  @Test
  void completionItemCarriesFieldDescription() {
    // given: автокомплит после точки по переменной-структуре.
    var dc = realDoc();
    var content = dc.getContent();
    var afterDot = content.indexOf("Свойства.", content.indexOf("X = Свойства.")) + "Свойства.".length();
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(positionAtOffset(content, afterDot));

    // when: берём элемент поля "Комментарий".
    var commentItem = completionProvider.getCompletion(dc, params).getItems().stream()
      .filter(item -> "Комментарий".equals(item.getLabel()))
      .findFirst()
      .orElseThrow();

    // then: его documentation содержит описание поля из JsDoc.
    var documentation = commentItem.getDocumentation();
    var docText = documentation.isRight()
      ? documentation.getRight().getValue()
      : documentation.getLeft();
    assertThat(docText).contains("комментарий, если он был введен при подписании");
  }

  private DocumentContext realDoc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/RealSignaturePropertiesReturn.bsl");
  }

  private static Position positionAtOffset(String content, int offset) {
    int line = content.substring(0, offset).split("\n", -1).length - 1;
    int charInLine = offset - (content.lastIndexOf('\n', offset - 1) + 1);
    return new Position(line, charInLine);
  }
}
