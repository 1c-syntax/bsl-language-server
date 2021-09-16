/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLLSBindingTest {

  @Test
  void testGetServerContext() {
    // when
    var serverContext = BSLLSBinding.getServerContext();

    // then
    assertThat(serverContext).isNotNull();
  }

  @Test
  void testGetDiagnosticInfos() {
    // when
    var diagnosticInfos = BSLLSBinding.getDiagnosticInfos();

    // then
    assertThat(diagnosticInfos).isNotNull();
  }

  @Test
  void testGetLanguageServerConfiguration() {
    // when
    var languageServerConfiguration = BSLLSBinding.getLanguageServerConfiguration();

    // then
    assertThat(languageServerConfiguration).isNotNull();
  }

  @Test
  void testReactivateContext() {
    // given
    var applicationContext = BSLLSBinding.getApplicationContext();
    applicationContext.close();

    // when
    applicationContext = BSLLSBinding.getApplicationContext();

    // then
    assertThat(applicationContext.isActive()).isTrue();
  }
}