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
import io.leangen.geantyref.TypeFactory;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom deserializer for Map&lt;String, DiagnosticMetadata&gt;.
 * Converts JSON objects into DiagnosticMetadata annotation instances using geantyref's TypeFactory.
 */
@Slf4j
public class DiagnosticMetadataMapDeserializer extends ValueDeserializer<Map<String, DiagnosticMetadata>> {

  @Override
  public Map<String, DiagnosticMetadata> deserialize(
    JsonParser p,
    DeserializationContext context
  ) {
    
    JsonNode node = context.readTree(p);
    if (node == null || !node.isObject()) {
      return new HashMap<>();
    }

    Map<String, DiagnosticMetadata> result = new HashMap<>();

    for (var entry : node.properties()) {
      String diagnosticCode = entry.getKey();
      JsonNode valueNode = entry.getValue();

      if (valueNode.isObject()) {
        try {
          // Convert JSON object to Map
          Map<String, Object> annotationParams = convertNodeToMap(valueNode);

          // Convert string enum values to proper types
          // IMPORTANT: When adding a new enum or array field to DiagnosticMetadata annotation,
          // add corresponding conversion here and update DiagnosticMetadataMapDeserializerTest
          convertStringToEnum(annotationParams, "type", DiagnosticType.class);
          convertStringToEnum(annotationParams, "severity", DiagnosticSeverity.class);
          convertStringToEnum(annotationParams, "scope", DiagnosticScope.class);
          convertStringToEnum(annotationParams, "compatibilityMode", DiagnosticCompatibilityMode.class);
          convertStringArrayToEnumArray(annotationParams, "tags", DiagnosticTag.class);
          convertStringArrayToEnumArray(annotationParams, "modules", ModuleType.class);

          // Create DiagnosticMetadata instance using TypeFactory
          DiagnosticMetadata metadata = TypeFactory.annotation(DiagnosticMetadata.class, annotationParams);
          result.put(diagnosticCode, metadata);
        } catch (Exception e) {
          LOGGER.warn("Failed to deserialize metadata for diagnostic: {}", diagnosticCode, e);
        }
      }
    }

    return result;
  }
  
  private static <E extends Enum<E>> void convertStringToEnum(Map<String, Object> params, String key, Class<E> enumClass) {
    if (params.containsKey(key) && params.get(key) instanceof String value) {
      params.put(key, Enum.valueOf(enumClass, value));
    }
  }

  private static Map<String, Object> convertNodeToMap(JsonNode node) {
    Map<String, Object> result = new HashMap<>();
    for (var entry : node.properties()) {
      String key = entry.getKey();
      JsonNode value = entry.getValue();
      if (value.isString()) {
        result.put(key, value.stringValue());
      } else if (value.isNumber()) {
        result.put(key, value.numberValue());
      } else if (value.isBoolean()) {
        result.put(key, value.booleanValue());
      } else if (value.isArray()) {
        var list = new ArrayList<>();
        for (JsonNode item : value) {
          if (item.isString()) {
            list.add(item.stringValue());
          } else if (item.isNumber()) {
            list.add(item.numberValue());
          } else if (item.isBoolean()) {
            list.add(item.booleanValue());
          }
        }
        result.put(key, list);
      }
    }
    return result;
  }

  private static <E extends Enum<E>> void convertStringArrayToEnumArray(
    Map<String, Object> params,
    String key,
    Class<E> enumClass
  ) {
    if (params.containsKey(key) && params.get(key) instanceof Iterable) {
      @SuppressWarnings("unchecked")
      var list = (Iterable<Object>) params.get(key);
      var array = java.lang.reflect.Array.newInstance(enumClass, ((Collection<?>) list).size());
      var i = 0;
      for (Object item : list) {
        if (item instanceof String stringItem) {
          java.lang.reflect.Array.set(array, i, Enum.valueOf(enumClass, stringItem));
        } else {
          java.lang.reflect.Array.set(array, i, item);
        }
        i++;
      }
      params.put(key, array);
    }
  }
}
