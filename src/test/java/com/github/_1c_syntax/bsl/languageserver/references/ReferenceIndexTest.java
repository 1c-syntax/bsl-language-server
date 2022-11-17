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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexTest {

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/references/ReferenceIndex.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
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
    var documentContext = serverContext.getDocument("Catalog.Справочник1.Form.ФормаСписка", ModuleType.FormModule).orElseThrow();
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
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
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
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(2, 30);

    // when
    var reference = referenceIndex.getReference(uri, position).orElseThrow();

    // then
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(2, 22, 41));
    assertThat(reference.getUri()).isEqualTo(uri);
  }

  @Test
  void getReferencesToCommonModuleMethodFromAssignment() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_Присваивание").orElseThrow();
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяФункция").orElseThrow();

    var uri = documentContext.getUri();

    // when
    final var referencesTo = referenceIndex.getReferencesTo(calledMethodSymbol).stream()
      .filter(reference -> reference.getUri().equals(uri))
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.toLocation().getRange()))
      .collect(Collectors.toList());

    // then
    var reference = referencesTo.get(0);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(8, 26, 45));
    assertThat(reference.getUri()).isEqualTo(uri);

    reference = referencesTo.get(1);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(9, 26, 45));
    assertThat(reference.getUri()).isEqualTo(uri);

    reference = referencesTo.get(2);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(10, 22, 41));
    assertThat(reference.getUri()).isEqualTo(uri);

    assertThat(referencesTo).hasSize(3);
  }

  @Test
  void getReferencesToFullPathModuleMethodFromAssignment() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_ВызовЧерезПолноеИмяОбъекта").orElseThrow();
    var commonModuleContext = serverContext.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("НеУстаревшаяФункция").orElseThrow();

    var uri = documentContext.getUri();

    // when
    final var referencesTo = referenceIndex.getReferencesTo(calledMethodSymbol).stream()
      .filter(reference -> reference.getUri().equals(uri))
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.toLocation().getRange()))
      .collect(Collectors.toList());

    // then
    var reference = referencesTo.get(0);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(22, 42, 61));
    assertThat(reference.getUri()).isEqualTo(uri);

    reference = referencesTo.get(1);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(23, 42, 61));
    assertThat(reference.getUri()).isEqualTo(uri);

    reference = referencesTo.get(2);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(24, 38, 57));
    assertThat(reference.getUri()).isEqualTo(uri);

    assertThat(referencesTo).hasSize(3);
  }

  @Test
  void getReferencesToCommonModuleMethodWithEqualNameWitMethodParam() {

    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("Тест_ИмяПараметр").orElseThrow();

    final var referencesFromLocationRepo = referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(reference -> Ranges.containsRange(methodSymbol.getRange(), reference.getSelectionRange()))
      .collect(Collectors.toList());

    assertThat(referencesFromLocationRepo).isEmpty();
  }

  @Test
  void testCantGetReferenceToNonExportCommonModuleMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var uri = documentContext.getUri();
    var position = new Position(4, 10);

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

    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var managerModuleContext = serverContext.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var managerModuleMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri().toString();
    var locationLocal = new Location(uri, Ranges.create(1, 4, 16));
    var locationCommonModule = new Location(uri, Ranges.create(2, 22, 41));
    var locationManagerModule = new Location(uri, Ranges.create(3, 38, 57));

    // when
    var references = referenceIndex.getReferencesFrom(localMethodSymbol);

    // then
    assertThat(references)
      .hasSize(3)
      .contains(Reference.of(localMethodSymbol, localMethodSymbol, locationLocal))
      .contains(Reference.of(localMethodSymbol, commonModuleMethodSymbol, locationCommonModule))
      .contains(Reference.of(localMethodSymbol, managerModuleMethodSymbol, locationManagerModule))
    ;
  }

  @Test
  void testGetReferencesFromCommonModule() {
    // given
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("Тест").orElseThrow();

    var managerModuleContext = serverContext.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
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
}