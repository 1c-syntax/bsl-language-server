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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndexFiller;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ModuleReferenceSemanticTokensSupplierTest {

  @Autowired
  private ModuleReferenceSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensLegend legend;

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

  @Autowired
  private ServerContext serverContext;

  @Test
  void testCommonModuleReference() throws IOException {
    // given
    var path = Absolute.path("src/test/resources/metadata/designer");
    serverContext.setConfigurationRoot(path);

    // Load the common module
    var file = new File("src/test/resources/metadata/designer",
      "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl");
    var uri = Absolute.uri(file);
    TestUtils.getDocumentContext(
      uri,
      FileUtils.readFileToString(file, StandardCharsets.UTF_8),
      serverContext
    );

    // Load a document that references the common module
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/references/ReferenceIndexCommonModuleVariable.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    int namespaceTypeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    var namespaceTokens = tokens.stream()
      .filter(t -> t.type() == namespaceTypeIdx)
      .toList();
    // Common module name should be highlighted as namespace
    assertThat(namespaceTokens).isNotEmpty();
  }

  @Test
  void testNoTokensWithoutCommonModuleReference() {
    // given - simple code without common module reference
    String bsl = """
      Процедура Тест()
        Сообщить("Привет");
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);

    // when
    var tokens = supplier.getSemanticTokens(documentContext);

    // then
    assertThat(tokens).isEmpty();
  }
}
