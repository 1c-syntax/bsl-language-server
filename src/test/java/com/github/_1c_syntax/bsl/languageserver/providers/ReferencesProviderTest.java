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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ReferencesProviderTest {

  @Autowired
  private ReferencesProvider referencesProvider;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/references.bsl";

  @BeforeEach
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testEmptyReferences() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new ReferenceParams();
    params.setPosition(new Position(1, 0));

    // when
    var references = referencesProvider.getReferences(documentContext, params);

    // then
    assertThat(references).isEmpty();
  }

  @Test
  void testLocalMethods() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new ReferenceParams();
    params.setPosition(new Position(0, 10));

    // when
    var references = referencesProvider.getReferences(documentContext, params);

    // then
    assertThat(references).hasSize(1);

    var reference = references.get(0);

    assertThat(reference.getUri()).isEqualTo(documentContext.getUri().toString());
    assertThat(reference.getRange()).isEqualTo(Ranges.create(4, 0, 10));
  }

  @Test
  void testMethodsFromManagerModule() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var managerModule = serverContext.getDocument("Catalog.Справочник1", ModuleType.ManagerModule).orElseThrow();

    var params = new ReferenceParams();
    params.setPosition(new Position(24, 15));

    // when
    var references = referencesProvider.getReferences(managerModule, params);

    // then
    var location = new Location(documentContext.getUri().toString(), Ranges.create(6, 24, 38));

    assertThat(references)
      .isNotEmpty()
      .contains(location);
  }

}
