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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.MagicNumberDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class DiagnosticBeanPostProcessorTest {

  @Autowired
  private ConfigurableApplicationContext applicationContext;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void testPostProcessAfterInitializationWithClassCastExceptionShouldNotCrash() throws Exception {
    // given
    var diagnosticBeanPostProcessor = applicationContext.getBean(DiagnosticBeanPostProcessor.class);
    var diagnostic = new MagicNumberDiagnostic();
    
    // Create configuration that will cause ClassCastException
    var diagnosticsOptions = new DiagnosticsOptions();
    var parameters = new HashMap<String, Either<Boolean, Map<String, Object>>>();
    
    Map<String, Object> configMap = new HashMap<>();
    List<String> invalidAuthorizedNumbers = new ArrayList<>();
    invalidAuthorizedNumbers.add("-1");
    invalidAuthorizedNumbers.add("0");
    invalidAuthorizedNumbers.add("1");
    configMap.put("authorizedNumbers", invalidAuthorizedNumbers); // This should be a String but is a List
    
    parameters.put("MagicNumber", Either.forRight(configMap));
    diagnosticsOptions.setParameters(parameters);
    
    // Use reflection to set the diagnosticsOptions in configuration
    Field diagnosticsOptionsField = configuration.getClass().getDeclaredField("diagnosticsOptions");
    diagnosticsOptionsField.setAccessible(true);
    diagnosticsOptionsField.set(configuration, diagnosticsOptions);

    // when/then - should not throw any exception, diagnostic configuration should fail gracefully
    assertDoesNotThrow(() -> {
      // First set the diagnostic info (postProcessBeforeInitialization)
      var result1 = diagnosticBeanPostProcessor.postProcessBeforeInitialization(diagnostic, "testBean");
      
      // Then configure it (postProcessAfterInitialization)
      var result2 = diagnosticBeanPostProcessor.postProcessAfterInitialization(result1, "testBean");
      
      // Verify the diagnostic bean is returned (normal behavior even with configuration error)
      assertThat(result2).isSameAs(diagnostic);
    });
    
    // Verify the diagnostic exists and has info set (basic functionality should work)
    assertThat(diagnostic.getInfo()).isNotNull();
    assertThat(diagnostic.getInfo().getCode()).isNotNull();
  }
}