/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ReferenceIndexFillerTest {

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;
  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private ServerContext serverContext;

  @Test
  void testFindCalledMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndexFillerTest.bsl");
    referenceIndexFiller.fill(documentContext);

    // when
    Optional<Reference> referencedSymbol = referenceIndex.getReference(documentContext.getUri(), new Position(4, 0));

    // then
    assertThat(referencedSymbol).isPresent();

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Локальная");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSelectionRange)
      .isEqualTo(Ranges.create(4, 0, 4, 9));
  }

  @Test
  void testFindNotifyDescription() {
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndexNotifyDescription.bsl");
    referenceIndexFiller.fill(documentContext);

    var method = documentContext.getSymbolTree().getMethodSymbol("ОбработчикОписаниеОповещения");
    assertThat(method).isPresent();
    var references = referenceIndex.getReferencesTo(method.get());
    assertThat(references).hasSize(1);

    method = documentContext.getSymbolTree().getMethodSymbol("ОшибкаОписаниеОповещения");
    assertThat(method).isPresent();
    references = referenceIndex.getReferencesTo(method.get());
    assertThat(references).hasSize(1);

    // Проверяем обход дерева в глубину для NewExpression если это описание оповещения
    method = documentContext.getSymbolTree().getMethodSymbol("ДополнительныеПараметрыОповещения");
    assertThat(method).isPresent();
    references = referenceIndex.getReferencesTo(method.get());
    assertThat(references).hasSize(1);
  }

  @Test
  void testFindNotifyDescriptionConfiguration() throws IOException {
    var path = Absolute.path("src/test/resources/metadata/designer");
    serverContext.setConfigurationRoot(path);

    var file = new File("src/test/resources/metadata/designer",
      "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl");
    var uri = Absolute.uri(file);
    TestUtils.getDocumentContext(
      uri,
      FileUtils.readFileToString(file, StandardCharsets.UTF_8),
      serverContext
    );

    file = new File("src/test/resources/metadata/designer",
      "CommonModules/КлиентскийОбщийМодуль/Ext/Module.bsl");
    uri = Absolute.uri(file);
    var documentContext = TestUtils.getDocumentContext(
      uri,
      FileUtils.readFileToString(file, StandardCharsets.UTF_8),
      serverContext
    );

    var method = documentContext.getSymbolTree().getMethodSymbol("ОбработчикОписаниеОповещения");
    assertThat(method).isPresent();
    var references = referenceIndex.getReferencesTo(method.get());
    assertThat(references).hasSize(1);

    method = documentContext.getSymbolTree().getMethodSymbol("ОшибкаОписаниеОповещения");
    assertThat(method).isPresent();
    references = referenceIndex.getReferencesTo(method.get());
    assertThat(references).hasSize(1);
  }

  @Test
  void testFindVariables() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(25, 24)
    );
    assertThat(referencedSymbol).isPresent();

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Первая");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getFrom)
      .extracting(Symbol::getName)
      .isEqualTo("ТретийМетод");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getOccurrenceType)
      .isEqualTo(OccurrenceType.REFERENCE);

    var scopeMethod = documentContext
      .getSymbolTree()
      .getMethodSymbol("ТретийМетод");
    assertThat(scopeMethod).isPresent();
    var references = referenceIndex.getReferencesFrom(scopeMethod.get());
    assertThat(references).hasSize(13);

    var targetVariable = documentContext.getSymbolTree().getVariables().get(0);
    var usage = referenceIndex.getReferencesTo(targetVariable);
    assertThat(usage).hasSize(5);
  }

  @Test
  void testFindVariablesInForStatements() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedForStatementsSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(38, 25)
    );
    assertThat(referencedForStatementsSymbol).isPresent();

    var referencedForEachStatementSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(44, 30)
    );
    assertThat(referencedForEachStatementSymbol).isPresent();
  }

  @Test
  void testFindVariablesRangesCallStatement() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(33, 9)
    );
    assertThat(referencedSymbol).isPresent();
    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Модуль");

    referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(33, 13)
    );
    assertThat(referencedSymbol).isEmpty();
  }

  @Test
  void testFindVariablesRangesComplexIdentifier() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(34, 16)
    );
    assertThat(referencedSymbol).isPresent();
    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Модуль");

    referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(34, 23)
    );
    assertThat(referencedSymbol).isEmpty();
  }

  @Test
  void testErrorVariables() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexFillerErrorVariableTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    var referencedSymbol = referenceIndex.getReference(
      documentContext.getUri(),
      new Position(48, 1)
    );
    assertThat(referencedSymbol).isPresent();

    assertThat(referencedSymbol).get()
      .extracting(Reference::getSymbol)
      .extracting(Symbol::getName)
      .isEqualTo("Запрос");

    assertThat(referencedSymbol).get()
      .extracting(Reference::getOccurrenceType)
      .isEqualTo(OccurrenceType.DEFINITION);
  }

  @Test
  void testRebuildClearReferences() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndexFillerTest.bsl");
    MethodSymbol methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Локальная").orElseThrow();

    // when
    referenceIndexFiller.fill(documentContext);
    List<Reference> referencesTo = referenceIndex.getReferencesTo(methodSymbol);

    // then
    assertThat(referencesTo).hasSize(1);

    // when
    // recalculate
    referenceIndexFiller.fill(documentContext);
    referencesTo = referenceIndex.getReferencesTo(methodSymbol);

    // then
    assertThat(referencesTo).hasSize(1);
  }

  @Test
  void testFindCommonModuleVariableReferences() throws IOException {
    var path = Absolute.path("src/test/resources/metadata/designer");
    serverContext.setConfigurationRoot(path);

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexCommonModuleVariable.bsl"
    );

    // Load the common module that will be referenced
    var file = new File("src/test/resources/metadata/designer",
      "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");
    var uri = Absolute.uri(file);
    var commonModuleContext = TestUtils.getDocumentContext(
      uri,
      FileUtils.readFileToString(file, StandardCharsets.UTF_8),
      serverContext
    );

    referenceIndexFiller.fill(documentContext);

    // Check that exported methods from common module are referenced
    var procMethod = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяПроцедура");
    assertThat(procMethod).isPresent();
    var referencesToProc = referenceIndex.getReferencesTo(procMethod.get());
    // Filter to only references from our test document
    var referencesToProcFromTest = referencesToProc.stream()
      .filter(ref -> ref.getUri().equals(documentContext.getUri()))
      .toList();
    assertThat(referencesToProcFromTest).hasSize(1);

    var funcMethod = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяФункция");
    assertThat(funcMethod).isPresent();
    var referencesToFunc = referenceIndex.getReferencesTo(funcMethod.get());
    // Filter to only references from our test document
    var referencesToFuncFromTest = referencesToFunc.stream()
      .filter(ref -> ref.getUri().equals(documentContext.getUri()))
      .toList();
    // Должно быть 2 вызова: в assignment и в условии
    assertThat(referencesToFuncFromTest).hasSize(2);
  }
}