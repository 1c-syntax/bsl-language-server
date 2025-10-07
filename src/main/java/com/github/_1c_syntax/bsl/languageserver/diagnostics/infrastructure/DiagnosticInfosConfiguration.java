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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.utils.StringInterner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@RequiredArgsConstructor
public class DiagnosticInfosConfiguration {

  private final ApplicationContext applicationContext;
  private final LanguageServerConfiguration configuration;
  private final StringInterner stringInterner;

  @SuppressWarnings("unchecked")
  @Bean("diagnosticInfosByCode")
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Map<String, DiagnosticInfo> diagnosticInfosByCode() {
    var beanNames = applicationContext.getBeanNamesForAnnotation(DiagnosticMetadata.class);

    return Arrays.stream(beanNames)
      .map(applicationContext::getType)
      .filter(Objects::nonNull)
      .filter(BSLDiagnostic.class::isAssignableFrom)
      .map(aClass -> (Class<? extends BSLDiagnostic>) aClass)
      .map(this::createDiagnosticInfo)
      .collect(Collectors.toMap(info -> info.getCode().getStringValue(), Function.identity()));
  }

  @Bean("diagnosticInfosByDiagnosticClass")
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> diagnosticInfosByDiagnosticClass() {
    return diagnosticInfosByCode().values().stream()
      .collect(Collectors.toMap(DiagnosticInfo::getDiagnosticClass, Function.identity()));
  }

  @Bean("diagnosticInfos")
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public Collection<DiagnosticInfo> diagnosticInfos() {
    return diagnosticInfosByCode().values();
  }

  @Bean
  @Scope("prototype")
  public DiagnosticInfo diagnosticInfo(@Autowired(required = false) Class<? extends BSLDiagnostic> diagnosticClass) {
    return diagnosticInfosByDiagnosticClass().get(diagnosticClass);
  }

  private DiagnosticInfo createDiagnosticInfo(
    @Autowired(required = false) Class<? extends BSLDiagnostic> diagnosticClass
  ) {
    var diagnosticCode = createDiagnosticCode(diagnosticClass);
    var metadataOverride = configuration.getDiagnosticsOptions().getMetadata().get(diagnosticCode);
    return new DiagnosticInfo(diagnosticClass, configuration, stringInterner, metadataOverride);
  }

  private String createDiagnosticCode(Class<? extends BSLDiagnostic> diagnosticClass) {
    String simpleName = diagnosticClass.getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }
    return simpleName;
  }
}
