/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.configuration.databind;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to ensure DiagnosticMetadataMapDeserializer handles all DiagnosticMetadata annotation fields
 */

class DiagnosticMetadataMapDeserializerTest {

  /**
   * This test validates that all enum/array fields in DiagnosticMetadata annotation
   * are handled in DiagnosticMetadataMapDeserializer.convertStringToEnum/convertStringArrayToEnumArray.
   * <p/>
   * If you add a new enum field to DiagnosticMetadata, you must:
   * 1. Add corresponding convertStringToEnum call in DiagnosticMetadataMapDeserializer
   * 2. Update the expectedHandledFields set in this test
   * <p/>
   * Primitive fields (like lspSeverity) don't need conversion and are excluded from this check.
   */
  @Test
  void testAllEnumFieldsAreHandledInDeserializer() {
    // Get all methods from DiagnosticMetadata annotation
    Method[] methods = DiagnosticMetadata.class.getDeclaredMethods();
    
    // Fields that should be handled by convertStringToEnum or convertStringArrayToEnumArray
    Set<String> expectedHandledFields = Set.of(
      "type",           // DiagnosticType enum
      "severity",       // DiagnosticSeverity enum
      "scope",          // DiagnosticScope enum
      "compatibilityMode", // DiagnosticCompatibilityMode enum
      "tags",           // DiagnosticTag[] array
      "modules"         // ModuleType[] array
    );
    
    // Fields that return String or primitives don't need conversion
    Set<String> excludedFields = Set.of(
      "lspSeverity",    // String - no conversion needed
      "minutesToFix",   // int - primitive
      "activatedByDefault", // boolean - primitive
      "canLocateOnProject", // boolean - primitive
      "extraMinForComplexity" // double - primitive
    );
    
    // Get all enum and array return types from annotation
    Set<String> enumAndArrayFields = Arrays.stream(methods)
      .filter(method -> {
        Class<?> returnType = method.getReturnType();
        return returnType.isEnum() || returnType.isArray();
      })
      .map(Method::getName)
      .collect(Collectors.toSet());
    
    // Get all fields
    Set<String> allFields = Arrays.stream(methods)
      .map(Method::getName)
      .collect(Collectors.toSet());
    
    // Verify all enum/array fields are in our expectedHandledFields
    var unhandledFields = enumAndArrayFields.stream()
      .filter(field -> !expectedHandledFields.contains(field))
      .collect(Collectors.toSet());
    
    assertThat(unhandledFields)
      .as("""
        Found enum or array fields in DiagnosticMetadata that are not handled in DiagnosticMetadataMapDeserializer!
        
        Please add conversion for these fields in DiagnosticMetadataMapDeserializer.deserialize():
        - Add convertStringToEnum(annotationParams, "fieldName", EnumClass.class) for enum fields
        - Add convertStringArrayToEnumArray(annotationParams, "fieldName", EnumClass.class) for array fields
        
        Then update expectedHandledFields set in this test.
        """)
      .isEmpty();
    
    // Also verify we're not tracking fields that don't exist anymore
    var obsoleteHandledFields = expectedHandledFields.stream()
      .filter(field -> !enumAndArrayFields.contains(field))
      .collect(Collectors.toSet());
    
    assertThat(obsoleteHandledFields)
      .as("Fields in expectedHandledFields that no longer exist in DiagnosticMetadata. Please remove them from the test.")
      .isEmpty();
    
    var obsoleteExcludedFields = excludedFields.stream()
      .filter(field -> !allFields.contains(field))
      .collect(Collectors.toSet());
    
    assertThat(obsoleteExcludedFields)
      .as("Fields in excludedFields that no longer exist in DiagnosticMetadata. Please remove them from the test.")
      .isEmpty();
    
    // Verify all fields are either handled or excluded
    var uncategorizedFields = allFields.stream()
      .filter(field -> !expectedHandledFields.contains(field) && !excludedFields.contains(field))
      .collect(Collectors.toSet());
    
    assertThat(uncategorizedFields)
      .as("""
        Found fields in DiagnosticMetadata that are neither in expectedHandledFields nor in excludedFields!
        
        Please categorize these fields:
        - If it's an enum or array that needs conversion, add it to expectedHandledFields and implement conversion
        - If it's a String or primitive that doesn't need conversion, add it to excludedFields
        """)
      .isEmpty();
  }

  /**
   * Test deserialization of enum arrays (tags and modules) from JSON configuration
   */
  @Test
  void testEnumArrayDeserialization() throws Exception {
    // given
    String json = """
      {
        "EmptyCodeBlock": {
          "type": "ERROR",
          "severity": "BLOCKER",
          "scope": "BSL",
          "compatibilityMode": "UNDEFINED",
          "tags": ["ERROR", "BADPRACTICE"],
          "modules": ["CommonModule", "FormModule"]
        }
      }
      """;

    // when
    Map<String, DiagnosticMetadata> result = deserializeMetadata(json);

    // then
    assertThat(result).containsKey("EmptyCodeBlock");
    
    DiagnosticMetadata metadata = result.get("EmptyCodeBlock");
    assertThat(metadata.type()).isEqualTo(DiagnosticType.ERROR);
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.BLOCKER);
    assertThat(metadata.scope()).isEqualTo(DiagnosticScope.BSL);
    assertThat(metadata.compatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED);
    
    // Verify enum arrays are properly deserialized
    assertThat(metadata.tags())
      .hasSize(2)
      .containsExactlyInAnyOrder(DiagnosticTag.ERROR, DiagnosticTag.BADPRACTICE);
    
    assertThat(metadata.modules())
      .hasSize(2)
      .containsExactlyInAnyOrder(ModuleType.CommonModule, ModuleType.FormModule);
  }

  /**
   * Test deserialization with empty enum arrays
   */
  @Test
  void testEmptyEnumArrayDeserialization() throws Exception {
    // given
    String json = """
      {
        "TestDiagnostic": {
          "tags": [],
          "modules": []
        }
      }
      """;

    // when
    Map<String, DiagnosticMetadata> result = deserializeMetadata(json);

    // then
    assertThat(result).containsKey("TestDiagnostic");
    
    DiagnosticMetadata metadata = result.get("TestDiagnostic");
    
    // Empty arrays should be preserved (not null)
    assertThat(metadata.tags()).isEmpty();
    assertThat(metadata.modules()).isEmpty();
  }

  /**
   * Test deserialization with partial enum arrays (only tags)
   */
  @Test
  void testPartialEnumArrayDeserialization() throws Exception {
    // given
    String json = """
      {
        "TestDiagnostic": {
          "tags": ["DESIGN", "UNPREDICTABLE"]
        }
      }
      """;

    // when
    Map<String, DiagnosticMetadata> result = deserializeMetadata(json);

    // then
    assertThat(result).containsKey("TestDiagnostic");
    
    DiagnosticMetadata metadata = result.get("TestDiagnostic");
    
    // tags should be set
    assertThat(metadata.tags())
      .hasSize(2)
      .containsExactlyInAnyOrder(DiagnosticTag.DESIGN, DiagnosticTag.UNPREDICTABLE);
    
    // modules should use annotation default value (empty array)
    assertThat(metadata.modules()).isEmpty();
  }

  private Map<String, DiagnosticMetadata> deserializeMetadata(String json) throws Exception {
    var mapper = new JsonMapper();

    String wrappedJson = "{\"metadata\": " + json + "}";
    TestWrapper wrapper = mapper.readValue(wrappedJson, TestWrapper.class);

    return wrapper.metadata;
  }

  /**
   * Wrapper class to test deserialization with the @JsonDeserialize annotation.
   */
  private static class TestWrapper {
    @JsonDeserialize(using = DiagnosticMetadataMapDeserializer.class)
    public Map<String, DiagnosticMetadata> metadata;
  }
}
