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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.types.ModuleType;
import io.leangen.geantyref.TypeFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom deserializer for DiagnosticMetadata annotation.
 */
@Slf4j
public class DiagnosticMetadataDeserializer extends JsonDeserializer<DiagnosticMetadata> {

  @Override
  public DiagnosticMetadata deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);
    
    Map<String, Object> annotationParameters = new HashMap<>();
    
    // Only set values that are present in JSON, let annotation defaults handle the rest
    if (node.has("type")) {
      annotationParameters.put("type", DiagnosticType.valueOf(node.get("type").asText()));
    }
    
    if (node.has("severity")) {
      annotationParameters.put("severity", DiagnosticSeverity.valueOf(node.get("severity").asText()));
    }
    
    if (node.has("scope")) {
      annotationParameters.put("scope", DiagnosticScope.valueOf(node.get("scope").asText()));
    }
    
    if (node.has("modules")) {
      JsonNode modulesNode = node.get("modules");
      ModuleType[] modules = new ModuleType[modulesNode.size()];
      for (int i = 0; i < modulesNode.size(); i++) {
        modules[i] = ModuleType.valueOf(modulesNode.get(i).asText());
      }
      annotationParameters.put("modules", modules);
    }
    
    if (node.has("minutesToFix")) {
      annotationParameters.put("minutesToFix", node.get("minutesToFix").asInt());
    }
    
    if (node.has("activatedByDefault")) {
      annotationParameters.put("activatedByDefault", node.get("activatedByDefault").asBoolean());
    }
    
    if (node.has("compatibilityMode")) {
      annotationParameters.put("compatibilityMode", DiagnosticCompatibilityMode.valueOf(node.get("compatibilityMode").asText()));
    }
    
    if (node.has("tags")) {
      JsonNode tagsNode = node.get("tags");
      DiagnosticTag[] tags = new DiagnosticTag[tagsNode.size()];
      for (int i = 0; i < tagsNode.size(); i++) {
        tags[i] = DiagnosticTag.valueOf(tagsNode.get(i).asText());
      }
      annotationParameters.put("tags", tags);
    }
    
    if (node.has("canLocateOnProject")) {
      annotationParameters.put("canLocateOnProject", node.get("canLocateOnProject").asBoolean());
    }
    
    if (node.has("extraMinForComplexity")) {
      annotationParameters.put("extraMinForComplexity", node.get("extraMinForComplexity").asDouble());
    }
    
    try {
      return TypeFactory.annotation(DiagnosticMetadata.class, annotationParameters);
    } catch (Exception e) {
      throw new IOException("Failed to create DiagnosticMetadata annotation", e);
    }
  }
}
