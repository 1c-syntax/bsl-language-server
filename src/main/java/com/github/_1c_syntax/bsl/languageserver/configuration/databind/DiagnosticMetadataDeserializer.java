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
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
    
    Map<String, Object> values = new HashMap<>();
    
    // Only set values that are present in JSON, let annotation defaults handle the rest
    if (node.has("type")) {
      values.put("type", DiagnosticType.valueOf(node.get("type").asText()));
    }
    
    if (node.has("severity")) {
      values.put("severity", DiagnosticSeverity.valueOf(node.get("severity").asText()));
    }
    
    if (node.has("scope")) {
      values.put("scope", DiagnosticScope.valueOf(node.get("scope").asText()));
    }
    
    if (node.has("modules")) {
      JsonNode modulesNode = node.get("modules");
      ModuleType[] modules = new ModuleType[modulesNode.size()];
      for (int i = 0; i < modulesNode.size(); i++) {
        modules[i] = ModuleType.valueOf(modulesNode.get(i).asText());
      }
      values.put("modules", modules);
    }
    
    if (node.has("minutesToFix")) {
      values.put("minutesToFix", node.get("minutesToFix").asInt());
    }
    
    if (node.has("activatedByDefault")) {
      values.put("activatedByDefault", node.get("activatedByDefault").asBoolean());
    }
    
    if (node.has("compatibilityMode")) {
      values.put("compatibilityMode", DiagnosticCompatibilityMode.valueOf(node.get("compatibilityMode").asText()));
    }
    
    if (node.has("tags")) {
      JsonNode tagsNode = node.get("tags");
      DiagnosticTag[] tags = new DiagnosticTag[tagsNode.size()];
      for (int i = 0; i < tagsNode.size(); i++) {
        tags[i] = DiagnosticTag.valueOf(tagsNode.get(i).asText());
      }
      values.put("tags", tags);
    }
    
    if (node.has("canLocateOnProject")) {
      values.put("canLocateOnProject", node.get("canLocateOnProject").asBoolean());
    }
    
    if (node.has("extraMinForComplexity")) {
      values.put("extraMinForComplexity", node.get("extraMinForComplexity").asDouble());
    }
    
    return createAnnotation(DiagnosticMetadata.class, values);
  }
  
  @SuppressWarnings("unchecked")
  private static <T extends Annotation> T createAnnotation(Class<T> annotationType, Map<String, Object> values) {
    return (T) Proxy.newProxyInstance(
      annotationType.getClassLoader(),
      new Class[]{annotationType},
      new AnnotationInvocationHandler(annotationType, values)
    );
  }
  
  private static class AnnotationInvocationHandler implements InvocationHandler {
    private final Class<? extends Annotation> annotationType;
    private final Map<String, Object> values;
    
    public AnnotationInvocationHandler(Class<? extends Annotation> annotationType, Map<String, Object> values) {
      this.annotationType = annotationType;
      this.values = values;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String methodName = method.getName();
      
      if ("annotationType".equals(methodName)) {
        return annotationType;
      }
      
      if ("toString".equals(methodName)) {
        return "@" + annotationType.getName() + values.toString();
      }
      
      if ("hashCode".equals(methodName)) {
        return values.hashCode();
      }
      
      if ("equals".equals(methodName)) {
        return proxy == args[0];
      }
      
      // If value is present in map, return it; otherwise return annotation default
      if (values.containsKey(methodName)) {
        return values.get(methodName);
      } else {
        return method.getDefaultValue();
      }
    }
  }
}
