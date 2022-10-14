/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExtractStructureConstructorSupplierTest {


  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private ExtractStructureConstructorSupplier codeActionSupplier;

  @Test
  void testGetCodeActions() {

    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/extractStructureConstructor.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(1, 21, 23));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate missing regions"));
  }

  @Test
  void getCodeActions() {
  }
}