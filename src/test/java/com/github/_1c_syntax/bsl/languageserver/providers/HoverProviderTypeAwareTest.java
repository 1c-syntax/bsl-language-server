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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты type-aware fallback'а в {@link HoverProvider}: член платформенного
 * типа / namespace без соответствующего source-defined символа.
 */
class HoverProviderTypeAwareTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_FILE = "./src/test/resources/types/EnumAccess.bsl";

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOnNamespaceIdentifier() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    // Кодировка = КодировкаТекста.UTF8;  // строка 0
    var content = documentContext.getContent();
    var params = new HoverParams();
    params.setPosition(new Position(0, content.indexOf("КодировкаТекста") + 1));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("КодировкаТекста");
  }

  @Test
  void hoverOnEnumMember() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var content = documentContext.getContent();
    var params = new HoverParams();
    params.setPosition(new Position(0, content.indexOf("UTF8") + 1));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("UTF8");
    assertThat(value).contains("КодировкаТекста");
  }

  @Test
  void hoverOnChainedMethodCall() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ChainedAccessors.os", context);
    var content = documentContext.getContent();
    // курсор внутри "Количество" в строке "Количество = СоздатьМассив().Количество();"
    var idx = content.indexOf(".Количество()");
    assertThat(idx).isGreaterThan(0);
    // line: считаем строку этого индекса
    var prefix = content.substring(0, idx);
    var line = (int) prefix.chars().filter(c -> c == '\n').count();
    var lineStart = prefix.lastIndexOf('\n') + 1;
    var col = idx - lineStart + 2; // +1 чтобы выйти за точку, попасть на 'К'

    var params = new HoverParams();
    params.setPosition(new Position(line, col));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("Количество");
    assertThat(value).contains("Массив");
  }

  @Test
  void hoverOnMethodWithOverload_picksMatchingSignatureByArity() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MethodOverloads.os", context);
    var content = documentContext.getContent();

    // Hover на 1-арг варианте: Чтение.Разделить("|");
    var idxOne = content.indexOf(".Разделить(\"|\");");
    assertThat(idxOne).isGreaterThan(0);
    var prefixOne = content.substring(0, idxOne);
    var lineOne = (int) prefixOne.chars().filter(c -> c == '\n').count();
    var lineStartOne = prefixOne.lastIndexOf('\n') + 1;
    var colOne = idxOne - lineStartOne + 2; // на 'Р'

    var paramsOne = new HoverParams();
    paramsOne.setPosition(new Position(lineOne, colOne));
    var hoverOne = hoverProvider.getHover(documentContext, paramsOne);
    assertThat(hoverOne).isPresent();
    var textOne = hoverOne.get().getContents().getRight().getValue();
    assertThat(textOne).contains("Разделить(separator)");
    assertThat(textOne).contains("Все варианты вызова");
    // 1-парам вариант должен быть выделен **bold** как выбранный
    assertThat(textOne).contains("**`Разделить(separator)`");
    assertThat(textOne).doesNotContain("Не найдено описание");

    // Hover на 2-арг варианте: Чтение.Разделить("|", "UTF-8");
    var idxTwo = content.indexOf(".Разделить(\"|\", \"UTF-8\")");
    assertThat(idxTwo).isGreaterThan(0);
    var prefixTwo = content.substring(0, idxTwo);
    var lineTwo = (int) prefixTwo.chars().filter(c -> c == '\n').count();
    var lineStartTwo = prefixTwo.lastIndexOf('\n') + 1;
    var colTwo = idxTwo - lineStartTwo + 2; // на 'Р'

    var paramsTwo = new HoverParams();
    paramsTwo.setPosition(new Position(lineTwo, colTwo));
    var hoverTwo = hoverProvider.getHover(documentContext, paramsTwo);
    assertThat(hoverTwo).isPresent();
    var textTwo = hoverTwo.get().getContents().getRight().getValue();
    assertThat(textTwo).contains("Разделить(separator, encoding)");
    assertThat(textTwo).doesNotContain("Не найдено описание");
  }

  @Test
  void hoverOnStrReplaceShowsGlobalFunctionNotStringMember() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/GlobalFunctionHover.os", context);
    var content = documentContext.getContent();

    var idx = content.indexOf("СтрЗаменить");
    assertThat(idx).isGreaterThan(0);
    var prefix = content.substring(0, idx);
    var line = (int) prefix.chars().filter(c -> c == '\n').count();
    var lineStart = prefix.lastIndexOf('\n') + 1;
    var col = idx - lineStart + 1;

    var params = new HoverParams();
    params.setPosition(new Position(line, col));
    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text).contains("СтрЗаменить");
    assertThat(text).contains("_глобальная функция_");
    assertThat(text).doesNotContain("_member of_");
  }

  @Test
  void hoverOnCurrentDateShowsGlobalFunction() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/GlobalFunctionHover.os", context);
    var content = documentContext.getContent();

    var idx = content.indexOf("ТекущаяДата");
    assertThat(idx).isGreaterThan(0);
    var prefix = content.substring(0, idx);
    var line = (int) prefix.chars().filter(c -> c == '\n').count();
    var lineStart = prefix.lastIndexOf('\n') + 1;
    var col = idx - lineStart + 1;

    var params = new HoverParams();
    params.setPosition(new Position(line, col));
    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var text = hover.get().getContents().getRight().getValue();
    assertThat(text).contains("ТекущаяДата");
    assertThat(text).contains("_глобальная функция_");
    assertThat(text).doesNotContain("_member of_");
  }
}
