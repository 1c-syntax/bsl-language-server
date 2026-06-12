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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrenceRepository;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Path;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private SymbolOccurrenceRepository symbolOccurrenceRepository;

  private static final String PATH_TO_FILE = "./src/test/resources/references/ReferenceIndex.bsl";

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
  }

  @Test
  void getReferencesToLocalMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var method = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var uri = documentContext.getUri();
    var location = new Location(uri.toString(), Ranges.create(1, 4, 16));

    // when
    var references = referenceIndex.getReferencesTo(method);

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(Reference.of(method, method, location))
    ;
  }

  @Test
  void getReferencesToLocalMethodFromFormModule() {
    // given
    var documentContext = context
      .getDocument("Catalog.Справочник1.Form.ФормаСписка", ModuleType.FormModule)
      .orElseThrow();
    var method = documentContext.getSymbolTree().getMethodSymbol("ЛокальнаяПроцедура").orElseThrow();
    var module = documentContext.getSymbolTree().getModule();

    var uri = documentContext.getUri();
    var location = new Location(uri.toString(), Ranges.create(4, 0, 18));

    // when
    var references = referenceIndex.getReferencesTo(method);

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(Reference.of(module, method, location))
    ;
  }

  @Test
  void getReferencesToCommonModuleMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var method = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var uri = documentContext.getUri();
    var location = new Location(uri.toString(), Ranges.create(1, 4, 16));

    // when
    var references = referenceIndex.getReferencesTo(method);

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(Reference.of(method, method, location))
    ;
  }

  @Test
  void testGetReferenceToLocalMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var location = new Location(uri.toString(), Ranges.create(2, 22, 41));

    // when
    var references = referenceIndex.getReferencesTo(calledMethodSymbol);

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(Reference.of(methodSymbol, calledMethodSymbol, location))
    ;
  }

  @Test
  void testGetReferenceToCommonModuleMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(2, 30);

    // when
    var reference = referenceIndex.getReference(uri, position).orElseThrow();

    // then
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(2, 22, 41));
    assertThat(reference.uri()).isEqualTo(uri);
  }

  @Test
  void getReferencesToCommonModuleMethodFromAssignment() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_Присваивание").orElseThrow();
    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяФункция").orElseThrow();

    var uri = documentContext.getUri();

    // when
    final var referencesTo = referenceIndex.getReferencesTo(calledMethodSymbol).stream()
      .filter(reference -> reference.uri().equals(uri))
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.toLocation().getRange()))
      .collect(Collectors.toList());

    // then
    var reference = referencesTo.getFirst();
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(8, 26, 45));
    assertThat(reference.uri()).isEqualTo(uri);

    reference = referencesTo.get(1);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(9, 26, 45));
    assertThat(reference.uri()).isEqualTo(uri);

    reference = referencesTo.get(2);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(10, 22, 41));
    assertThat(reference.uri()).isEqualTo(uri);

    assertThat(referencesTo).hasSize(3);
  }

  @Test
  void getReferencesToFullPathModuleMethodFromAssignment() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_ВызовЧерезПолноеИмяОбъекта").orElseThrow();
    var commonModuleContext = context.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяФункция").orElseThrow();

    var uri = documentContext.getUri();

    // when
    final var referencesTo = referenceIndex.getReferencesTo(calledMethodSymbol).stream()
      .filter(reference -> reference.uri().equals(uri))
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.toLocation().getRange()))
      .collect(Collectors.toList());

    // then
    var reference = referencesTo.getFirst();
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(22, 42, 61));
    assertThat(reference.uri()).isEqualTo(uri);

    reference = referencesTo.get(1);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(23, 42, 61));
    assertThat(reference.uri()).isEqualTo(uri);

    reference = referencesTo.get(2);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(24, 38, 57));
    assertThat(reference.uri()).isEqualTo(uri);

    assertThat(referencesTo).hasSize(3);
  }

  @Test
  void getReferencesToCommonModuleMethodWithEqualNameWitMethodParam() {

    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_ИмяПараметр").orElseThrow();

    final var referencesFromLocationRepo = referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.selectionRange()))
      .collect(Collectors.toList());

    assertThat(referencesFromLocationRepo).isEmpty();
  }

  @Test
  void testCantGetReferenceToNonExportCommonModuleMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var uri = documentContext.getUri();
    // Position on "Тест" method name (non-export method)
    var position = new Position(4, 24);

    // when
    var reference = referenceIndex.getReference(uri, position);

    // then
    assertThat(reference).isEmpty();
  }

  @Test
  void testGetReferencesFromLocalMethodSymbol() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var localMethodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();
    var commonModuleSymbol = commonModuleContext.getSymbolTree().getModule();

    var managerModuleContext = context.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var managerModuleMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri().toString();
    var locationLocal = new Location(uri, Ranges.create(1, 4, 16));
    var locationCommonModuleName1 = new Location(uri, Ranges.create(2, 4, 21)); // ПервыйОбщийМодуль on line 3
    var locationCommonModule = new Location(uri, Ranges.create(2, 22, 41));
    var locationManagerModule = new Location(uri, Ranges.create(3, 38, 57));
    var locationCommonModuleName2 = new Location(uri, Ranges.create(4, 4, 21)); // ПервыйОбщийМодуль on line 5

    // when
    var references = referenceIndex.getReferencesFrom(localMethodSymbol);

    // then
    // 5 references from ReferenceIndex.bsl:
    // - line 2: local method call ИмяПроцедуры()
    // - line 3: module name ПервыйОбщийМодуль
    // - line 3: method УстаревшаяПроцедура in common module
    // - line 4: method УстаревшаяПроцедура in manager module
    // - line 5: module name ПервыйОбщийМодуль
    assertThat(references)
      .hasSize(5)
      .contains(Reference.of(localMethodSymbol, localMethodSymbol, locationLocal))
      .contains(Reference.of(localMethodSymbol, commonModuleSymbol, locationCommonModuleName1))
      .contains(Reference.of(localMethodSymbol, commonModuleMethodSymbol, locationCommonModule))
      .contains(Reference.of(localMethodSymbol, managerModuleMethodSymbol, locationManagerModule))
      .contains(Reference.of(localMethodSymbol, commonModuleSymbol, locationCommonModuleName2))
    ;
  }

  @Test
  void getReferencesToCommonModule() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleSymbol = commonModuleContext.getSymbolTree().getModule();

    var uri = documentContext.getUri().toString();
    var firstModuleNameLocation = new Location(uri, Ranges.create(2, 4, 21)); // ПервыйОбщийМодуль on line 3
    var secondModuleNameLocation = new Location(uri, Ranges.create(4, 4, 21)); // ПервыйОбщийМодуль on line 5

    // when
    var references = referenceIndex.getReferencesTo(commonModuleSymbol);

    // then
    assertThat(references)
      .contains(Reference.of(methodSymbol, commonModuleSymbol, firstModuleNameLocation))
      .contains(Reference.of(methodSymbol, commonModuleSymbol, secondModuleNameLocation))
    ;
  }

  @Test
  void testGetReferencesFromCommonModule() {
    // given
    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("Тест").orElseThrow();

    var managerModuleContext = context.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var managerModuleMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = commonModuleContext.getUri().toString();
    var locationCommonModule = new Location(uri, Ranges.create(55, 38, 57));

    // when
    var references = referenceIndex.getReferencesFrom(commonModuleMethodSymbol);

    // then
    assertThat(references)
      .hasSize(1)
      .contains(Reference.of(commonModuleMethodSymbol, managerModuleMethodSymbol, locationCommonModule))
    ;
  }

  /**
   * Тесты, мутирующие ReferenceIndex (addMethodCall, clearReferences). Чтобы
   * мутации не попадали в read-only тесты внешнего класса (которые делят
   * workspace через {@link #initServerContextOnce}), вынесены в @Nested и
   * получают свежий workspace на каждый метод через
   * {@link #initServerContext(Path)} (это сбрасывает флаг cleanupAfterClass).
   */
  @Nested
  class MutatingTests {

    @BeforeEach
    void freshWorkspace() {
      initServerContext(Path.of(PATH_TO_METADATA));
    }

    @Test
    @DirtiesContext
    void clearReferences() {
      // given
      var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

      var uri = documentContext.getUri();
      var position = new Position(1, 10);

      // when
      referenceIndex.clearReferences(documentContext.getUri());

      // then
      var reference = referenceIndex.getReference(uri, position);

      assertThat(reference).isEmpty();
    }

    @Test
    @DirtiesContext
    void crossWorkspaceIsolation() {
      // given - workspace 1 is already initialized with PATH_TO_METADATA in @BeforeEach
      // Manually add a reference to workspace 1's repos to verify isolation
      var workspace1Uri = context.getDocuments().keySet().iterator().next();
      referenceIndex.addMethodCall(
        workspace1Uri, "CommonModule.TestModule", ModuleType.CommonModule, "TestMethod",
        Ranges.create(0, 0, 10)
      );

      // Build the same Symbol key used for both workspaces
      var symbolDto = com.github._1c_syntax.bsl.languageserver.references.model.Symbol.builder()
        .mdoRef("CommonModule.TestModule")
        .moduleType(ModuleType.CommonModule)
        .scopeName("")
        .symbolKind(SymbolKind.Method)
        .symbolName("testmethod")
        .build();

      // Workspace-scoped bean should contain the occurrence we just added
      var occurrences = symbolOccurrenceRepository.getAllBySymbol(symbolDto);
      assertThat(occurrences).hasSize(1);

      // Verify clearing works
      referenceIndex.clearReferences(workspace1Uri);
      assertThat(symbolOccurrenceRepository.getAllBySymbol(symbolDto)).isEmpty();
    }
  }
}