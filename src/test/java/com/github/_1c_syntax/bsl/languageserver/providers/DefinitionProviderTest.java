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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DefinitionProviderTest {

  @Autowired
  private DefinitionProvider definitionProvider;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/definition.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testEmptyDefinition() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new DefinitionParams();
    params.setPosition(new Position(1, 0));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions).isEmpty();
  }

  @Test
  void testDefinitionOfLocalMethod() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяФункции").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(4, 9));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions).hasSize(1);

    var definition = definitions.get(0);

    assertThat(definition.getTargetUri()).isEqualTo(documentContext.getUri().toString());
    assertThat(definition.getTargetSelectionRange()).isEqualTo(methodSymbol.getSelectionRange());
    assertThat(definition.getTargetRange()).isEqualTo(methodSymbol.getRange());
    assertThat(definition.getOriginSelectionRange()).isEqualTo(Ranges.create(4, 0, 10));
  }

  @Test
  void testDefinitionOfCommonModule() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var managerModule = serverContext.getDocument("Catalog.Справочник1", ModuleType.ManagerModule).orElseThrow();
    var methodSymbol = managerModule.getSymbolTree().getMethodSymbol("ТестЭкспортная").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(6, 30));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions).hasSize(1);

    var definition = definitions.get(0);

    assertThat(definition.getTargetUri()).isEqualTo(managerModule.getUri().toString());
    assertThat(definition.getTargetSelectionRange()).isEqualTo(methodSymbol.getSelectionRange());
    assertThat(definition.getTargetRange()).isEqualTo(methodSymbol.getRange());
    assertThat(definition.getOriginSelectionRange()).isEqualTo(Ranges.create(6, 24, 38));
  }
}
