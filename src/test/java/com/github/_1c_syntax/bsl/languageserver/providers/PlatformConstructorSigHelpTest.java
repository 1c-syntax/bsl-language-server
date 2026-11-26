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
 * SignatureHelp на конструкторах платформенных типов в разных позициях
 * — supplier не должен падать на каждой из них.
 */
@CleanupContextBeforeClassAndAfterClass
class PlatformConstructorSigHelpTest extends AbstractServerContextAwareTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Test
  void sigHelpForArrayConstructorSingleArgDoesNotCrash() {
    var help = signatureAt("М1 = Новый Массив(10)", "М1 = Новый Массив(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForArrayConstructorMultiDimDoesNotCrash() {
    var help = signatureAt("М2 = Новый Массив(3, 5)", "М2 = Новый Массив(3, ".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForDateConstructorNumericDoesNotCrash() {
    var help = signatureAt("Д1 = Новый Дата(2020, 1, 1)",
      "Д1 = Новый Дата(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForDateConstructor6ArgsDoesNotCrash() {
    var help = signatureAt("Д2 = Новый Дата(2020, 6, 15, 12, 30, 45)",
      "Д2 = Новый Дата(2020, 6, 15, 12, 30, ".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForTypeDescriptionConstructorDoesNotCrash() {
    var help = signatureAt("ОТ1 = Новый ОписаниеТипов(\"Число\")",
      "ОТ1 = Новый ОписаниеТипов(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForUuidConstructorDoesNotCrash() {
    var help = signatureAt(
      "УИД2 = Новый УникальныйИдентификатор(\"00000000-0000-0000-0000-000000000000\")",
      "УИД2 = Новый УникальныйИдентификатор(".length() + 1);
    assertThat(help).isNotNull();
  }

  @Test
  void sigHelpForBinaryDataConstructorDoesNotCrash() {
    var help = signatureAt("ДД2 = Новый ДвоичныеДанные(\"/tmp/file.bin\")",
      "ДД2 = Новый ДвоичныеДанные(".length() + 1);
    assertThat(help).isNotNull();
  }

  private SignatureHelp signatureAt(String marker, int offsetInMarker) {
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/PlatformConstructorSigs.bsl");
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
