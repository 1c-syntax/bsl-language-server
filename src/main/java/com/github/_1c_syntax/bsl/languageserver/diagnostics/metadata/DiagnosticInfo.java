/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.DiagnosticLanguage;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.utils.UTF8Control;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class DiagnosticInfo {

  private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticInfo.class.getSimpleName());

  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> severityToLSPSeverityMap
    = createSeverityToLSPSeverityMap();

  private final Class<? extends BSLDiagnostic> diagnosticClass;
  private final DiagnosticLanguage diagnosticLanguage;

  private final String diagnosticCode;
  private DiagnosticMetadata diagnosticMetadata;
  private List<DiagnosticParameterInfo> diagnosticParameters;

  public DiagnosticInfo(Class<? extends BSLDiagnostic> diagnosticClass, DiagnosticLanguage diagnosticLanguage) {
    this.diagnosticClass = diagnosticClass;
    this.diagnosticLanguage = diagnosticLanguage;

    diagnosticCode = createDiagnosticCode();
    diagnosticMetadata = diagnosticClass.getAnnotation(DiagnosticMetadata.class);
    diagnosticParameters = DiagnosticParameterInfo.createDiagnosticParameters(this);
  }

  public DiagnosticInfo(Class<? extends BSLDiagnostic> diagnosticClass) {
    this(diagnosticClass, LanguageServerConfiguration.DEFAULT_DIAGNOSTIC_LANGUAGE);
  }

  public Class<? extends BSLDiagnostic> getDiagnosticClass() {
    return diagnosticClass;
  }

  public String getCode() {
    return diagnosticCode;
  }

  public String getName() {
    return getResourceString("diagnosticName");
  }

  public String getDescription() {
    String langCode = diagnosticLanguage.getLanguageCode();

    String resourceName = langCode + "/" + diagnosticCode + ".md";
    InputStream descriptionStream = diagnosticClass.getResourceAsStream(resourceName);

    if (descriptionStream == null) {
      LOGGER.error("Can't find resource {}", resourceName);
      return "";
    }

    try {
      return IOUtils.toString(descriptionStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Can't read diagnostic description", e);
      return "";
    }
  }

  public String getMessage() {
    return getResourceString("diagnosticMessage");
  }

  public String getMessage(Object... args) {
    return String.format(getMessage(), args).intern();
  }

  public String getResourceString(String key) {
    String languageCode = diagnosticLanguage.getLanguageCode();
    Locale locale = Locale.forLanguageTag(languageCode);
    return ResourceBundle.getBundle(diagnosticClass.getName(), locale, new UTF8Control()).getString(key).intern();
  }

  public DiagnosticType getType() {
    return diagnosticMetadata.type();
  }

  public DiagnosticSeverity getSeverity() {
    return diagnosticMetadata.severity();
  }

  public org.eclipse.lsp4j.DiagnosticSeverity getLSPSeverity() {
    return severityToLSPSeverityMap.get(getSeverity());
  }

  public DiagnosticCompatibilityMode getCompatibilityMode() {
    return diagnosticMetadata.compatibilityMode();
  }

  public DiagnosticScope getScope() {
    return diagnosticMetadata.scope();
  }

  public int getMinutesToFix() {
    return diagnosticMetadata.minutesToFix();
  }

  public boolean isActivatedByDefault() {
    return diagnosticMetadata.activatedByDefault();
  }

  public List<DiagnosticTag> getTags() {
    return new ArrayList<>(Arrays.asList(diagnosticMetadata.tags()));
  }

  public List<DiagnosticParameterInfo> getParameters() {
    return new ArrayList<>(diagnosticParameters);
  }

  public Optional<DiagnosticParameterInfo> getParameter(String parameterName) {
    return diagnosticParameters.stream().filter(param -> param.getName().equals(parameterName)).findAny();
  }

  public Map<String, Object> getDefaultConfiguration() {
    return diagnosticParameters.stream()
      .collect(Collectors.toMap(DiagnosticParameterInfo::getName, DiagnosticParameterInfo::getDefaultValue));
  }

  private String createDiagnosticCode() {
    String simpleName = diagnosticClass.getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }

    return simpleName;
  }

  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> createSeverityToLSPSeverityMap() {
    Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> map = new EnumMap<>(DiagnosticSeverity.class);
    map.put(DiagnosticSeverity.INFO, org.eclipse.lsp4j.DiagnosticSeverity.Hint);
    map.put(DiagnosticSeverity.MINOR, org.eclipse.lsp4j.DiagnosticSeverity.Information);
    map.put(DiagnosticSeverity.MAJOR, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.CRITICAL, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.BLOCKER, org.eclipse.lsp4j.DiagnosticSeverity.Warning);

    return map;
  }
}
