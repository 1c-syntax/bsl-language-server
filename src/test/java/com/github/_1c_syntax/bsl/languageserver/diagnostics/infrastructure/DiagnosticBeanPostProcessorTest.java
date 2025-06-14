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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticBeanPostProcessorTest {

  @Mock
  private LanguageServerConfiguration configuration;

  @Mock
  private Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> diagnosticInfos;

  @Mock
  private Resources resources;

  @Mock
  private DiagnosticInfo diagnosticInfo;

  @Mock
  private DiagnosticCode diagnosticCode;

  @Mock
  private DiagnosticsOptions diagnosticsOptions;

  private DiagnosticBeanPostProcessor diagnosticBeanPostProcessor;

  @BeforeEach
  void setUp() {
    diagnosticBeanPostProcessor = new DiagnosticBeanPostProcessor(configuration, diagnosticInfos, resources);
  }

  @Test
  void testPostProcessAfterInitializationWithClassCastExceptionShouldNotCrash() {
    // given
    var diagnostic = new MagicNumberDiagnostic();
    
    // Mock DiagnosticInfo and DiagnosticCode
    lenient().when(diagnosticCode.getStringValue()).thenReturn("MagicNumber");
    lenient().when(diagnosticInfo.getCode()).thenReturn(diagnosticCode);
    lenient().when(diagnosticInfos.get(MagicNumberDiagnostic.class)).thenReturn(diagnosticInfo);
    
    // Mock configuration that will cause ClassCastException
    var parameters = new HashMap<String, Either<Boolean, Map<String, Object>>>();
    
    // Create a configuration that will cause ClassCastException
    Map<String, Object> configMap = new HashMap<>();
    List<String> invalidAuthorizedNumbers = new ArrayList<>();
    invalidAuthorizedNumbers.add("-1");
    invalidAuthorizedNumbers.add("0");
    invalidAuthorizedNumbers.add("1");
    configMap.put("authorizedNumbers", invalidAuthorizedNumbers); // This should be a String but is a List
    
    parameters.put("MagicNumber", Either.forRight(configMap));
    when(diagnosticsOptions.getParameters()).thenReturn(parameters);
    when(configuration.getDiagnosticsOptions()).thenReturn(diagnosticsOptions);
    lenient().when(resources.getResourceString(any(Class.class), anyString(), anyString(), anyString()))
      .thenReturn("Test error message");

    // when/then - should not throw any exception, diagnostic configuration should fail gracefully
    assertDoesNotThrow(() -> {
      // First set the diagnostic info (simulating postProcessBeforeInitialization)
      diagnostic.setInfo(diagnosticInfo);
      
      // Then configure it (postProcessAfterInitialization)
      var result = diagnosticBeanPostProcessor.postProcessAfterInitialization(diagnostic, "testBean");
      
      // Verify the diagnostic bean is returned (normal behavior even with configuration error)
      assert result == diagnostic;
    });
  }
}