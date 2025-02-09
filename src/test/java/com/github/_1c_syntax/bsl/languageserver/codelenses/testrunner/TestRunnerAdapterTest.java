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
package com.github._1c_syntax.bsl.languageserver.codelenses.testrunner;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TestRunnerAdapterTest {

  @Autowired
  private TestRunnerAdapter testRunnerAdapter;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void whenComputeTestsByLanguageServer_thenContainsTests() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("src/test/resources/codelenses/testrunner/TestRunnerAdapter.os");
    configuration.getCodeLensOptions().getTestRunnerAdapterOptions().setGetTestsByTestRunner(false);

    // when
    var testIds = testRunnerAdapter.getTestIds(documentContext);

    // then
    assertThat(testIds).hasSize(1);
    assertThat(testIds.getFirst()).isEqualTo("Тест1");
  }
}