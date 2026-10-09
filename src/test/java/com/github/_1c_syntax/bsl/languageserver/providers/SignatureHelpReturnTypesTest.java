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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureHelpReturnTypesTest extends AbstractServerContextAwareTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareServerContext() {
    initServerContext();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "RU | Новый Массив | 1 | Массив, Число",
    "RU | 1 | Новый Массив | Массив, Число",
    "EN | Новый Массив | 1 | Array, Number",
    "EN | 1 | Новый Массив | Array, Number",
    "RU | 1 | 2 | Число",
    "EN | 1 | 2 | Number"
  })
  void localMethodSignatureRendersAllInferredReturnTypes(
    Language language, String firstReturn, String secondReturn, String expectedTypes
  ) {
    languageServerConfiguration.setLanguage(language);
    try {
      var content = """
        Функция Выбрать(Условие)
          Если Условие Тогда
            Возврат %s;
          Иначе
            Возврат %s;
          КонецЕсли;
        КонецФункции
        Результат = Выбрать(Истина);
        """.formatted(firstReturn, secondReturn);
      var documentContext = TestUtils.getDocumentContext(content, context);
      var params = new SignatureHelpParams();
      params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
      params.setPosition(new Position(7, "Результат = Выбрать(".length()));

      var inferredTypes = typeService.expressionTypesAt(documentContext, new Position(7, "Результат = ".length() + 1));
      assertThat(inferredTypes.refs()).extracting(TypeRef::qualifiedName)
        .contains("Число");
      if (!firstReturn.equals("1") || !secondReturn.equals("2")) {
        assertThat(inferredTypes.refs()).extracting(TypeRef::qualifiedName)
          .containsExactlyInAnyOrder("Массив", "Число");
      }

      var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

      assertThat(help.getSignatures()).hasSize(1);
      assertThat(help.getSignatures().getFirst().getLabel())
        .isEqualTo("Выбрать(Условие): " + expectedTypes);
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

}
