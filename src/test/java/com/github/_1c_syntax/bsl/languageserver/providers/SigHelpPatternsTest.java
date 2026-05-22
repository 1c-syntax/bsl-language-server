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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SignatureHelp в типичных позициях BSL: один-параметр, несколько-параметров,
 * необязательные, пропущенные, вложенные вызовы.
 */
@CleanupContextBeforeClassAndAfterClass
class SigHelpPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Test
  void sigHelpForTwoArgFunctionFirstParam() {
    var help = signatureAt("Х1 = Сложить(10, 20)", "Х1 = Сложить(".length() + 1);
    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getActiveParameter()).isZero();
  }

  @Test
  void sigHelpForTwoArgFunctionSecondParam() {
    var help = signatureAt("Х1 = Сложить(10, 20)", "Х1 = Сложить(10, ".length() + 1);
    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getActiveParameter()).isEqualTo(1);
  }

  @Test
  void sigHelpForTwoArgFunctionWithBlankArgs() {
    // Х2 = Сложить(, ) — активный параметр должен корректно определиться.
    var help = signatureAt("Х2 = Сложить(, )", "Х2 = Сложить(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForOptionalParameter() {
    // НастроитьКомпонент с 3 параметрами, последний с default.
    var help = signatureAt("НастроитьКомпонент(\"comp2\", Новый Структура, Ложь)",
      "НастроитьКомпонент(\"comp2\", Новый Структура, ".length() + 1);
    assertThat(help.getActiveParameter()).isEqualTo(2);
  }

  @Test
  void sigHelpForNoArgFunction() {
    // БезПарам() — нет аргументов.
    var help = signatureAt("БезПарам()", "БезПарам(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForOnlyOptionalParameter() {
    var help = signatureAt("Х4 = ТолькоНеобязат(100)",
      "Х4 = ТолькоНеобязат(".length() + 1);
    assertThat(help.getSignatures()).isNotEmpty();
  }

  @Test
  void sigHelpForNestedInnerCall() {
    // Х5 = Сложить(Сложить(1, 2), Сложить(3, 4)) — курсор внутри внутреннего.
    var content = "Х5 = Сложить(Сложить(1, 2), Сложить(3, 4))";
    // позиция внутри первого Сложить(1, 2) — после "Сложить(" + "1, " = active=1
    var help = signatureAt(content,
      "Х5 = Сложить(Сложить(1, ".length() + 1);
    assertThat(help.getSignatures()).isNotEmpty();
    // innermost — это inner Сложить с active=1
    assertThat(help.getActiveParameter()).isEqualTo(1);
  }

  @Test
  void sigHelpForGlobalFunctionAsArgumentToOuter() {
    // Сообщить(Сложить(5, 6)) — курсор на 5 внутри Сложить.
    var content = "Сообщить(Сложить(5, 6))";
    var help = signatureAt(content,
      "Сообщить(Сложить(".length() + 1);
    assertThat(help.getSignatures()).isNotEmpty();
  }

  private SignatureHelp signatureAt(String marker, int offsetInMarker) {
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/SigHelpPatterns.bsl");
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, charInLine));
    return signatureHelpProvider.getSignatureHelp(dc, params);
  }
}
