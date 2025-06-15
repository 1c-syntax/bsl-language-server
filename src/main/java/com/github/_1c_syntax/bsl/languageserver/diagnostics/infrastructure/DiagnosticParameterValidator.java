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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
@Slf4j
@CacheConfig(cacheNames = "diagnosticSchemaValidation")
public class DiagnosticParameterValidator {

  private final Resources resources;
  private final ObjectMapper objectMapper;
  
  private JsonSchema parametersSchema;
  private final Map<String, JsonSchema> diagnosticSchemas = new ConcurrentHashMap<>();

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * Сбрасывает кеш валидации схем при изменении конфигурации.
   *
   * @param event Событие
   */
  @EventListener
  @CacheEvict(allEntries = true)
  public void handleLanguageServerConfigurationChange(LanguageServerConfigurationChangedEvent event) {
    // No-op. Служит для сброса кеша при изменении конфигурации
  }

  /**
   * Cached validation of diagnostic configuration against JSON schema.
   * Results are cached per diagnostic class and configuration to improve performance for prototype beans.
   * 
   * @param diagnosticCode Diagnostic code
   * @param configuration Configuration map to validate
   */
  @Cacheable
  public void validateDiagnosticConfiguration(String diagnosticCode, Map<String, Object> configuration) {
    try {
      var schema = getDiagnosticSchema(diagnosticCode);
      if (schema != null) {
        var configNode = objectMapper.valueToTree(configuration);
        Set<ValidationMessage> errors = schema.validate(configNode);
        
        if (!errors.isEmpty()) {
          var errorMessages = errors.stream()
            .map(ValidationMessage::getMessage)
            .reduce((msg1, msg2) -> msg1 + "; " + msg2)
            .orElse("Unknown validation error");
          
          var localizedMessage = resources.getResourceString(DiagnosticBeanPostProcessor.class, "diagnosticSchemaValidationError", 
                                                           diagnosticCode, errorMessages);
          LOGGER.warn(localizedMessage);
        }
      }
    } catch (Exception e) {
      // Schema validation failed, but don't prevent diagnostic configuration
      LOGGER.debug("Schema validation failed for diagnostic '{}': {}", diagnosticCode, e.getMessage());
    }
  }

  private JsonSchema getDiagnosticSchema(String diagnosticCode) {
    return diagnosticSchemas.computeIfAbsent(diagnosticCode, this::loadDiagnosticSchema);
  }

  private JsonSchema loadDiagnosticSchema(String diagnosticCode) {
    try {
      if (parametersSchema == null) {
        parametersSchema = loadParametersSchema();
      }
      
      if (parametersSchema != null) {
        // Extract the specific diagnostic schema from the main schema
        var schemaNode = parametersSchema.getSchemaNode();
        var definitionsNode = schemaNode.get("definitions");
        if (definitionsNode != null && definitionsNode.has(diagnosticCode)) {
          var diagnosticSchemaNode = definitionsNode.get(diagnosticCode);
          var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
          return factory.getSchema(diagnosticSchemaNode);
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Failed to load schema for diagnostic '{}': {}", diagnosticCode, e.getMessage());
    }
    return null;
  }

  private JsonSchema loadParametersSchema() {
    try {
      var schemaResource = new ClassPathResource("com/github/_1c_syntax/bsl/languageserver/configuration/parameters-schema.json");
      if (schemaResource.exists()) {
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        return factory.getSchema(schemaResource.getInputStream());
      }
    } catch (IOException e) {
      LOGGER.warn("Failed to load parameters schema: {}", e.getMessage());
    }
    return null;
  }
}