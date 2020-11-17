/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GenerateStandardRegionsSupplierTest {

  @Autowired
  private CodeActionProvider codeActionProvider;

  @Test
  void testGetCodeActions() {

    // given
    String filePath = "./src/test/resources/suppliers/generateRegion.bsl";
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<Either<Command, CodeAction>> codeActions = codeActionProvider.getCodeActions(params, documentContext);

    System.out.println(codeActions.get(0).getRight().getEdit().getChanges());

    assertThat(codeActions)
      .hasSize(2)
      .extracting(Either::getRight)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate missing regions"));
  }
}