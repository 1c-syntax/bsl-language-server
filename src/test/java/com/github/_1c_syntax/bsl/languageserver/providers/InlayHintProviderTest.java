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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.inlayhints.CognitiveComplexityInlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class InlayHintProviderTest {

  @Autowired
  private InlayHintProvider provider;
  @Autowired
  private LanguageServerConfiguration configuration;
  @Autowired
  private CognitiveComplexityInlayHintSupplier supplier;
  @Autowired
  @Qualifier("enabledInlayHintSuppliers")
  private ObjectProvider<List<InlayHintSupplier>> enabledInlayHintSuppliersProvider;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    String filePath = "./src/test/resources/providers/inlayHints.bsl";
    documentContext = TestUtils.getDocumentContextFromFile(filePath);
  }

  @Test
  void getInlayHint() {

    // given
    var params = new InlayHintParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setRange(Ranges.create(documentContext.getAst()));

    // when
    var inlayHints = provider.getInlayHint(documentContext, params);

    // then
    assertThat(inlayHints)
      .contains(getTestHint());
  }

  @Test
  void testDefaultEnabledSuppliers() {

    // given
    // default config

    // when
    List<InlayHintSupplier> suppliers = enabledInlayHintSuppliersProvider.getObject();

    // then
    assertThat(suppliers).contains(supplier);
  }

  @Test
  void testDisabledSupplierIsNotEnabled() {

    // given
    configuration.getInlayHintOptions().getParameters().put(supplier.getId(), Either.forLeft(false));

    // when
    List<InlayHintSupplier> suppliers = enabledInlayHintSuppliersProvider.getObject();

    // then
    assertThat(suppliers).doesNotContain(supplier);

  }

  private static InlayHint getTestHint() {
    return new InlayHint(new Position(0, 0), Either.forLeft("test hint"));
  }

  @TestConfiguration
  static class Configuration {
    @Bean
    InlayHintSupplier inlayHintSupplier() {
      return new TestInlayHintSupplier();
    }
  }

  static class TestInlayHintSupplier implements InlayHintSupplier {
    @Override
    public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
      var inlayHint = getTestHint();
      return List.of(inlayHint);
    }
  }

}