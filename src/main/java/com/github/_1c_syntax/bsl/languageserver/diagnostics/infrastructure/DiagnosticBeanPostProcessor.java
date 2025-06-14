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
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
@Slf4j
public class DiagnosticBeanPostProcessor implements BeanPostProcessor {

  private final LanguageServerConfiguration configuration;
  private final Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> diagnosticInfos;

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (!BSLDiagnostic.class.isAssignableFrom(bean.getClass())) {
      return bean;
    }

    var diagnostic = (BSLDiagnostic) bean;

    var info = diagnosticInfos.get(diagnostic.getClass());
    diagnostic.setInfo(info);

    return diagnostic;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {

    if (!BSLDiagnostic.class.isAssignableFrom(bean.getClass())) {
      return bean;
    }

    BSLDiagnostic diagnostic = (BSLDiagnostic) bean;

    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnosticsOptions().getParameters().get(diagnostic.getInfo().getCode().getStringValue());

    if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
      var configurationMap = diagnosticConfiguration.getRight();
      var normalizedConfiguration = normalizeConfigurationTypes(configurationMap);
      diagnostic.configure(normalizedConfiguration);
    }

    return diagnostic;
  }

  /**
   * Normalizes configuration values to handle type conversions that may cause ClassCastException.
   * Specifically handles conversion of List to String by joining with commas.
   * 
   * @param configuration Original configuration map
   * @return Normalized configuration map with converted types
   */
  private Map<String, Object> normalizeConfigurationTypes(Map<String, Object> configuration) {
    Map<String, Object> normalizedConfig = new HashMap<>();
    
    for (Map.Entry<String, Object> entry : configuration.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      
      try {
        if (value instanceof List<?>) {
          // Convert List to comma-separated String
          List<?> listValue = (List<?>) value;
          String stringValue = listValue.stream()
            .map(Object::toString)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
          normalizedConfig.put(key, stringValue);
          LOGGER.debug("Converted List configuration parameter '{}' to String: '{}'", key, stringValue);
        } else {
          // Keep the value as-is
          normalizedConfig.put(key, value);
        }
      } catch (Exception e) {
        // If any conversion fails, log warning and keep original value
        LOGGER.warn("Failed to normalize configuration parameter '{}' with value '{}': {}. Using original value.", 
                   key, value, e.getMessage());
        normalizedConfig.put(key, value);
      }
    }
    
    return normalizedConfig;
  }

}
