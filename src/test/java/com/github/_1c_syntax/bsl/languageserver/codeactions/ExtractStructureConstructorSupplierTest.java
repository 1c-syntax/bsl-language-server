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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
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
  private DocumentContext documentContext;
  private CodeActionParams params;

  @Test
  void testGetCodeActions() {

    // given
    setRange(Ranges.create(1, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));

    assertThat((((ArrayList<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .hasSize(4)
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("()"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Структура.Insert(\"а\", а);\n"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Структура.Insert(\"б\", б);\n"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Структура.Insert(\"в\", в);\n"))
    ;
  }

  @Test
  void testGetCodeActionsDouble() {

    // given
    setRange(Ranges.create(6, 14, 17));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));
  }

  @Test
  void testGetCodeActionsFind() {

    // given
    setRange(Ranges.create(10, 76, 78));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));
  }

  @Test
  void testGetCodeActionsArray() {

    // given
    setRange(Ranges.create(12, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));

  }

  @Test
  void testGetCodeActionsNOParams() {

    // given
    setRange(Ranges.create(14, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));

  }

  @Test
  void testGetCodeActionsEmptyParams() {

    // given
    setRange(Ranges.create(14, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));

  }

  @Test
  void testGetCodeActionsIdentifier() {

    // given
    setRange(Ranges.create(15, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0)
      .noneMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));

  }

  @Test
  void testGetCodeActionsObject() {

    // given
    setRange(Ranges.create(17, 21, 23));

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Unwrap constructor"));
    assertThat((((ArrayList<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .hasSize(4)
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("()"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Чтото.Поле.Insert(\"а\", а);\n"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Чтото.Поле.Insert(\"б\", б);\n"))
      .anyMatch(textedit -> ((TextEdit) textedit).getNewText().equals("Чтото.Поле.Insert(\"в\", в);\n"))
    ;

  }

  void setRange(Range range) {
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/extractStructureConstructor.bsl";
    documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    params = new CodeActionParams();
    params.setRange(range);
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);
  }

}