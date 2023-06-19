/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexReferenceFinderTest {

  @Autowired
  private ReferenceIndexReferenceFinder referenceFinder;

  @Autowired
  private ServerContext serverContext;

  @SpyBean
  private ReferenceIndex referenceIndex;

  private static final String PATH_TO_FILE = "./src/test/resources/references/ReferenceIndexReferenceFinder.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testLocalMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var method = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(1, 10);
    var location = new Location(uri.toString(), Ranges.create(1, 4, 16));

    var expectedReference = Reference.of(method, method, location);
    when(referenceIndex.getReference(uri, position)).thenReturn(Optional.of(expectedReference));

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference).isEqualTo(expectedReference);

    // when
    var optionalReference = referenceFinder.findReference(uri, new Position());

    // then
    assertThat(optionalReference).isEmpty();
  }

  @Test
  void testCommonModuleMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var commonModuleContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(2, 30);

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference.getUri()).isEqualTo(uri);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(2, 22, 41));
  }

  @Test
  void testManagerModuleMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var managerModuleContext = serverContext.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var calledMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(3, 40);

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference.getUri()).isEqualTo(uri);
    assertThat(reference.getFrom()).isEqualTo(methodSymbol);
    assertThat(reference.getSymbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.getSelectionRange()).isEqualTo(Ranges.create(3, 38, 57));
  }

  @Test
  void testCantFindNonExportMethodFromOtherModule() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var uri = documentContext.getUri();
    var position = new Position(4, 25);

    // when
    var reference = referenceFinder.findReference(uri, position);

    // then
    assertThat(reference).isEmpty();
  }

  @Test
  void testUnknownLocationReturnsEmptyReference() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var method = mock(MethodSymbol.class);

    var uri = documentContext.getUri();
    var position = new Position(1, 1);
    var location = new Location(uri.toString(), Ranges.create(1, 1, 2));

    var expectedReference = Reference.of(method, method, location);
    when(referenceIndex.getReference(uri, position)).thenReturn(Optional.of(expectedReference));

    // when
    var optionalReference = referenceFinder.findReference(uri, new Position());

    // then
    assertThat(optionalReference).isEmpty();
  }

}