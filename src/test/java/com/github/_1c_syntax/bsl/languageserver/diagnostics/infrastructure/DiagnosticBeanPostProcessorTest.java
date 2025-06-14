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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagnosticBeanPostProcessorTest {

  @Test
  void testNormalizeConfigurationTypesWithArrayListToString() {
    // Arrange
    DiagnosticBeanPostProcessor processor = new DiagnosticBeanPostProcessor(null, null);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("authorizedNumbers", new ArrayList<>(Arrays.asList("-1", "0", "1")));
    configuration.put("normalString", "keep-as-is");
    configuration.put("normalInt", 42);
    
    // Act - Use reflection to call the private method
    try {
      var method = DiagnosticBeanPostProcessor.class.getDeclaredMethod("normalizeConfigurationTypes", Map.class);
      method.setAccessible(true);
      Map<String, Object> result = (Map<String, Object>) method.invoke(processor, configuration);
      
      // Assert
      assertEquals("-1,0,1", result.get("authorizedNumbers"));
      assertEquals("keep-as-is", result.get("normalString"));
      assertEquals(42, result.get("normalInt"));
    } catch (Exception e) {
      throw new RuntimeException("Test failed", e);
    }
  }

  @Test
  void testNormalizeConfigurationTypesWithEmptyList() {
    // Arrange
    DiagnosticBeanPostProcessor processor = new DiagnosticBeanPostProcessor(null, null);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("emptyList", new ArrayList<>());
    
    // Act - Use reflection to call the private method
    try {
      var method = DiagnosticBeanPostProcessor.class.getDeclaredMethod("normalizeConfigurationTypes", Map.class);
      method.setAccessible(true);
      Map<String, Object> result = (Map<String, Object>) method.invoke(processor, configuration);
      
      // Assert
      assertEquals("", result.get("emptyList"));
    } catch (Exception e) {
      throw new RuntimeException("Test failed", e);
    }
  }

  @Test 
  void testNormalizeConfigurationTypesReproducesIssue3485() {
    // This test reproduces the exact scenario from issue #3485
    // where configuration contains ArrayList instead of String for "authorizedNumbers"
    
    // Arrange
    DiagnosticBeanPostProcessor processor = new DiagnosticBeanPostProcessor(null, null);
    Map<String, Object> configuration = new HashMap<>();
    // This is the exact configuration that was causing ClassCastException
    configuration.put("authorizedNumbers", new ArrayList<>(Arrays.asList("-1", "0", "1")));
    
    // Act - Use reflection to call the private method 
    try {
      var method = DiagnosticBeanPostProcessor.class.getDeclaredMethod("normalizeConfigurationTypes", Map.class);
      method.setAccessible(true);
      Map<String, Object> result = (Map<String, Object>) method.invoke(processor, configuration);
      
      // Assert - The ArrayList should be converted to comma-separated String
      assertEquals("-1,0,1", result.get("authorizedNumbers"));
      
      // Verify that this normalized configuration can now be safely cast to String
      Object normalizedValue = result.get("authorizedNumbers");
      assertEquals(String.class, normalizedValue.getClass());
      String castedValue = (String) normalizedValue; // This would have thrown ClassCastException before fix
      assertEquals("-1,0,1", castedValue);
      
    } catch (Exception e) {
      throw new RuntimeException("Test failed", e);
    }
  }

  @Test
  void testNormalizeConfigurationTypesWithStringValues() {
    // Arrange
    DiagnosticBeanPostProcessor processor = new DiagnosticBeanPostProcessor(null, null);
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("stringValue", "test-string");
    
    // Act - Use reflection to call the private method
    try {
      var method = DiagnosticBeanPostProcessor.class.getDeclaredMethod("normalizeConfigurationTypes", Map.class);
      method.setAccessible(true);
      Map<String, Object> result = (Map<String, Object>) method.invoke(processor, configuration);
      
      // Assert
      assertEquals("test-string", result.get("stringValue"));
    } catch (Exception e) {
      throw new RuntimeException("Test failed", e);
    }
  }
}