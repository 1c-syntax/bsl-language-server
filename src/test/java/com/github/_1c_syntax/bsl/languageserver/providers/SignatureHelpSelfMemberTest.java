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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Неквалифицированный вызов платформенного метода self-типа текущего модуля
 * (см. {@link com.github._1c_syntax.bsl.languageserver.types.TypeService#findSelfMember}) должен
 * получать signature help наравне с локальным методом документа и глобальной функцией.
 */
class SignatureHelpSelfMemberTest extends AbstractServerContextAwareTest {

  private static final URI OBJECT_MODULE_URI = Path.of(
    "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Test
  void unqualifiedCallToSelfPlatformMethodGetsSignatureHelp() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Записать();
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Записать(") + "Записать(".length();
    params.setPosition(new Position(1, col));

    // "Записать" здесь не объявлен ни локально, ни как глобальная функция —
    // это платформенный метод self-типа СправочникОбъект.Справочник1
    // (см. TypeService#findSelfMember), и без self-fallback'а сигнатура вообще
    // не находилась бы.
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getSignatures().get(0).getLabel()).startsWith("Записать(");
  }
}
