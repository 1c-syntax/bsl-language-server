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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class MoreBinaryOpsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void allComparisonOperatorsReturnBoolean() {
    var dc = doc();
    assertThat(qnames(infer(dc, "Сравн1 = 100 < 200", "Сравн1 = ".length())))
      .containsExactly("Булево");
    assertThat(qnames(infer(dc, "Равно1 = 100 = 200", "Равно1 = ".length())))
      .containsExactly("Булево");
    assertThat(qnames(infer(dc, "НеРавно1 = 100 <> 200", "НеРавно1 = ".length())))
      .containsExactly("Булево");
    assertThat(qnames(infer(dc, "БольшеРавно1 = 100 >= 200", "БольшеРавно1 = ".length())))
      .containsExactly("Булево");
    assertThat(qnames(infer(dc, "МеньшеРавно1 = 100 <= 200", "МеньшеРавно1 = ".length())))
      .containsExactly("Булево");
  }

  @Test
  void ternarySameTypeBranchesReturnsThatType() {
    var dc = doc();
    var types = infer(dc, "ТернОднотип = ?(100 > 0, \"A\", \"B\")", "ТернОднотип = ".length());
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void ternaryDifferentTypeBranchesReturnsUnion() {
    var dc = doc();
    var types = infer(dc, "ТернРазнотип = ?(100 > 0, \"A\", 42)", "ТернРазнотип = ".length());
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Число");
  }

  @Test
  void unaryNotReturnsBoolean() {
    var dc = doc();
    var types = infer(dc, "УнНОТ = НЕ Истина", "УнНОТ = ".length());
    assertThat(qnames(types)).containsExactly("Булево");
  }

  @Test
  void logicalAndOrReturnBoolean() {
    var dc = doc();
    assertThat(qnames(infer(dc, "ЛогИ = Истина И Ложь", "ЛогИ = ".length())))
      .containsExactly("Булево");
    assertThat(qnames(infer(dc, "ЛогИЛИ = Истина ИЛИ Ложь", "ЛогИЛИ = ".length())))
      .containsExactly("Булево");
  }

  private TypeSet infer(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MoreBinaryOps.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
