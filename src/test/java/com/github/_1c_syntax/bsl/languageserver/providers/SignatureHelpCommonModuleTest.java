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
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signature help на вызове метода общего модуля. Ресивер-общий-модуль не
 * резолвится через индекс ссылок, а expressionTypesAt на mid-call дал бы тип
 * возврата вызова — поэтому без receiverTypesAt сигнатура не находилась
 * (тот же класс бага, что #3991 в completion).
 */
@CleanupContextBeforeClassAndAfterClass
class SignatureHelpCommonModuleTest extends AbstractServerContextAwareTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Test
  void signatureHelpResolvesCommonModuleMethodReceiver() {
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CommonModuleSignatureHelp.bsl");

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // строка `\tОбщегоНазначения.ЗначениеВМассиве();` — позиция внутри скобок:
    // таб + 16 символов модуля + точка + 16 символов метода + `(` → char 35
    params.setPosition(new Position(1, 35));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help).isNotNull();
    assertThat(help.getSignatures())
      .as("ресивер-общий-модуль должен резолвиться → сигнатура метода модуля")
      .hasSize(1);
    var signature = help.getSignatures().get(0);
    assertThat(signature.getLabel()).contains("ЗначениеВМассиве(");
    assertThat(signature.getParameters()).hasSize(1);
  }
}
