/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;

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

    // when
    var references = referenceIndex.getReferencesTo(method);
    var reference= Reference.builder()
      .from(method)
      .symbol(method)
      .uri(uri)
      .selectionRange(Ranges.create(1, 4, 16))
      .build();

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(reference)
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

    // when
    var references = referenceIndex.getReferencesTo(method);
    var reference= Reference.builder()
      .from(method)
      .symbol(method)
      .uri(uri)
      .selectionRange(Ranges.create(1, 4, 16))
      .build();

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(reference)
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

    // when
    var references = referenceIndex.getReferencesTo(calledMethodSymbol);
    var reference= Reference.builder()
      .from(methodSymbol)
      .symbol(calledMethodSymbol)
      .uri(uri)
      .selectionRange(Ranges.create(2, 22, 41))
      .build();

    // then
    assertThat(references)
      .isNotEmpty()
      .contains(reference)
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

    var referenceLocal = Reference.builder()
      .from(localMethodSymbol)
      .symbol(localMethodSymbol)
      .uri(documentContext.getUri())
      .selectionRange(Ranges.create(1, 4, 16))
      .build();
    var referenceCommonModule = Reference.builder()
      .from(localMethodSymbol)
      .symbol(commonModuleMethodSymbol)
      .uri(documentContext.getUri())
      .selectionRange(Ranges.create(2, 22, 41))
      .build();
    var referenceManagerModule = Reference.builder()
      .from(localMethodSymbol)
      .symbol(managerModuleMethodSymbol)
      .uri(documentContext.getUri())
      .selectionRange(Ranges.create(3, 38, 57))
      .build();

    // when
    var references = referenceIndex.getReferencesFrom(localMethodSymbol);

    // then
    assertThat(references)
      .hasSize(3)
      .contains(referenceLocal)
      .contains(referenceCommonModule)
      .contains(referenceManagerModule)
    ;
  }

  @Test
  void testGetReferencesFromCommonModule() {
    // given
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var commonModuleMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("Тест").orElseThrow();

    var managerModuleContext = serverContext.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var managerModuleMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    // when
    var references = referenceIndex.getReferencesFrom(commonModuleMethodSymbol);
    var reference = Reference.builder()
      .from(commonModuleMethodSymbol)
      .symbol(managerModuleMethodSymbol)
      .uri(commonModuleContext.getUri())
      .selectionRange(Ranges.create(55, 38, 57))
      .build();

    // then
    assertThat(references)
      .hasSize(1)
      .contains(reference)
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