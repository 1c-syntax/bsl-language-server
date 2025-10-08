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
package com.github._1c_syntax.bsl.languageserver.configuration.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import io.leangen.geantyref.TypeFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom deserializer for Map&lt;String, DiagnosticMetadata&gt;.
 * Converts JSON objects into DiagnosticMetadata annotation instances using geantyref's TypeFactory.
 */
@Slf4j
public class DiagnosticMetadataMapDeserializer extends JsonDeserializer<Map<String, DiagnosticMetadata>> {

  @Override
  public Map<String, DiagnosticMetadata> deserialize(
    JsonParser p,
    DeserializationContext context
  ) throws IOException {
    
    JsonNode node = p.getCodec().readTree(p);
    if (node == null || !node.isObject()) {
      return new HashMap<>();
    }

    Map<String, DiagnosticMetadata> result = new HashMap<>();
    ObjectMapper mapper = (ObjectMapper) p.getCodec();

    var fields = node.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      String diagnosticCode = entry.getKey();
      JsonNode valueNode = entry.getValue();

      if (valueNode.isObject()) {
        try {
          // Convert JSON object to Map
          @SuppressWarnings("unchecked")
          Map<String, Object> annotationParams = mapper.convertValue(valueNode, Map.class);
          
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
}
