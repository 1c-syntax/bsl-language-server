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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.StringInterner;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DiagnosticInfo {

  private static final Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> severityToLSPSeverityMap
    = createSeverityToLSPSeverityMap();
  private static final Map<DiagnosticTag, org.eclipse.lsp4j.DiagnosticTag> diagnosticTagMap = createDiagnosticTagMap();

  @Getter
  private final Class<? extends BSLDiagnostic> diagnosticClass;
  private final LanguageServerConfiguration configuration;
  private final StringInterner stringInterner;

  private final DiagnosticCode diagnosticCode;
  private final DiagnosticMetadata diagnosticMetadata;
  private final List<DiagnosticParameterInfo> diagnosticParameters;

  private final Optional<Map<String, Object>> metadataOverride;
  private final org.eclipse.lsp4j.DiagnosticSeverity cachedLSPSeverity;

  public DiagnosticInfo(
    Class<? extends BSLDiagnostic> diagnosticClass,
    LanguageServerConfiguration configuration,
    StringInterner stringInterner
  ) {
    this.diagnosticClass = diagnosticClass;
    this.configuration = configuration;
    this.stringInterner = stringInterner;

    diagnosticCode = createDiagnosticCode();
    diagnosticMetadata = diagnosticClass.getAnnotation(DiagnosticMetadata.class);
    diagnosticParameters = DiagnosticParameterInfo.createDiagnosticParameters(this);
    
    // Get metadata override from configuration if exists
    var diagnosticsOptions = configuration.getDiagnosticsOptions();
    metadataOverride = Optional.ofNullable(diagnosticsOptions.getMetadata().get(diagnosticCode.getStringValue()));
    cachedLSPSeverity = computeLSPSeverity();
  }

  public DiagnosticCode getCode() {
    return diagnosticCode;
  }

  public String getDiagnosticCodeDescriptionHref() {
    var language = configuration.getLanguage();
    var useDevSite = configuration.isUseDevSite();

    var siteRoot = configuration.getSiteRoot();
    var devSuffix = useDevSite ? "/dev" : "";
    var languageSuffix = language == Language.EN ? "/en" : "";

    var siteDiagnosticsUrl = String.format(
      "%s%s%s/diagnostics/",
      siteRoot,
      devSuffix,
      languageSuffix
    );

    return stringInterner.intern(siteDiagnosticsUrl + diagnosticCode.getStringValue());
  }

  public String getName() {
    return getResourceString("diagnosticName");
  }

  public String getDescription() {
    var langCode = configuration.getLanguage().getLanguageCode();

    var resourceName = langCode + "/" + diagnosticCode.getStringValue() + ".md";
    var descriptionStream = diagnosticClass.getResourceAsStream(resourceName);

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
    return getResourceString("diagnosticMessage", args);
  }

  public String getResourceString(String key) {
    return Resources.getResourceString(configuration.getLanguage(), diagnosticClass, key);
  }

  public String getResourceString(String key, Object... args) {
    return Resources.getResourceString(configuration.getLanguage(), diagnosticClass, key, args);
  }

  public DiagnosticType getType() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable(map.get("type")))
      .map(value -> value instanceof DiagnosticType ? (DiagnosticType) value : DiagnosticType.valueOf(value.toString()))
      .orElseGet(diagnosticMetadata::type);
  }

  public DiagnosticSeverity getSeverity() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable(map.get("severity")))
      .map(value -> value instanceof DiagnosticSeverity ? (DiagnosticSeverity) value : DiagnosticSeverity.valueOf(value.toString()))
      .orElseGet(diagnosticMetadata::severity);
  }

  public org.eclipse.lsp4j.DiagnosticSeverity getLSPSeverity() {
    return cachedLSPSeverity;
  }
  
  private org.eclipse.lsp4j.DiagnosticSeverity computeLSPSeverity() {
    org.eclipse.lsp4j.DiagnosticSeverity lspSeverity;
    
    // First check if lspSeverity is explicitly set in metadata override from config
    var lspSeverityFromConfig = metadataOverride
      .flatMap(map -> Optional.ofNullable(map.get("lspSeverity")))
      .filter(value -> !value.toString().isEmpty())
      .map(value -> parseLSPSeverity(value.toString()));
    
    if (lspSeverityFromConfig.isPresent()) {
      lspSeverity = lspSeverityFromConfig.get();
    } else {
      // Check if lspSeverity is explicitly set in annotation
      var lspSeverityFromAnnotation = diagnosticMetadata.lspSeverity();
      if (lspSeverityFromAnnotation != null && !lspSeverityFromAnnotation.isEmpty()) {
        lspSeverity = parseLSPSeverity(lspSeverityFromAnnotation);
      } else {
        // Calculate based on type and severity (original logic)
        var type = getType();
        if (type == DiagnosticType.CODE_SMELL) {
          lspSeverity = severityToLSPSeverityMap.get(getSeverity());
        } else if (type == DiagnosticType.SECURITY_HOTSPOT) {
          lspSeverity = org.eclipse.lsp4j.DiagnosticSeverity.Warning;
        } else {
          lspSeverity = org.eclipse.lsp4j.DiagnosticSeverity.Error;
        }
      }
    }
    
    // Apply minimum severity override if configured
    var overrideMinimum = configuration.getDiagnosticsOptions().getOverrideMinimumLSPDiagnosticLevel();
    if (overrideMinimum != null && lspSeverity.getValue() > overrideMinimum.getValue()) {
      lspSeverity = overrideMinimum;
    }
    
    return lspSeverity;
  }
  
  private org.eclipse.lsp4j.DiagnosticSeverity parseLSPSeverity(String value) {
    return switch (value) {
      case "Error" -> org.eclipse.lsp4j.DiagnosticSeverity.Error;
      case "Warning" -> org.eclipse.lsp4j.DiagnosticSeverity.Warning;
      case "Information" -> org.eclipse.lsp4j.DiagnosticSeverity.Information;
      case "Hint" -> org.eclipse.lsp4j.DiagnosticSeverity.Hint;
      default -> throw new IllegalArgumentException("Invalid LSP severity value: " + value);
    };
  }

  public DiagnosticCompatibilityMode getCompatibilityMode() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((DiagnosticCompatibilityMode) map.get("compatibilityMode")))
      .orElseGet(diagnosticMetadata::compatibilityMode);
  }

  public DiagnosticScope getScope() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((DiagnosticScope) map.get("scope")))
      .orElseGet(diagnosticMetadata::scope);
  }

  public ModuleType[] getModules() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((ModuleType[]) map.get("modules")))
      .orElseGet(diagnosticMetadata::modules);
  }

  public int getMinutesToFix() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((Integer) map.get("minutesToFix")))
      .orElseGet(diagnosticMetadata::minutesToFix);
  }

  public boolean isActivatedByDefault() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((Boolean) map.get("activatedByDefault")))
      .orElseGet(diagnosticMetadata::activatedByDefault);
  }

  public List<DiagnosticTag> getTags() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((DiagnosticTag[]) map.get("tags")))
      .map(tags -> new ArrayList<>(Arrays.asList(tags)))
      .orElseGet(() -> new ArrayList<>(Arrays.asList(diagnosticMetadata.tags())));
  }

  public List<org.eclipse.lsp4j.DiagnosticTag> getLSPTags() {
    return getTags().stream()
      .map(diagnosticTag -> diagnosticTagMap.getOrDefault(diagnosticTag, null))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public List<DiagnosticParameterInfo> getParameters() {
    return new ArrayList<>(diagnosticParameters);
  }

  public Optional<DiagnosticParameterInfo> getParameter(String parameterName) {
    return diagnosticParameters.stream().filter(param -> param.getName().equals(parameterName)).findAny();
  }

  public boolean canLocateOnProject() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((Boolean) map.get("canLocateOnProject")))
      .orElseGet(diagnosticMetadata::canLocateOnProject);
  }

  public double getExtraMinForComplexity() {
    return metadataOverride
      .flatMap(map -> Optional.ofNullable((Double) map.get("extraMinForComplexity")))
      .orElseGet(diagnosticMetadata::extraMinForComplexity);
  }

  public Map<String, Object> getDefaultConfiguration() {
    return diagnosticParameters.stream()
      .collect(Collectors.toMap(DiagnosticParameterInfo::getName, DiagnosticParameterInfo::getDefaultValue));
  }

  private DiagnosticCode createDiagnosticCode() {
    String simpleName = diagnosticClass.getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }

    return new DiagnosticCode(stringInterner.intern(simpleName));
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

  private static Map<DiagnosticTag, org.eclipse.lsp4j.DiagnosticTag> createDiagnosticTagMap() {
    return Map.of(
      DiagnosticTag.UNUSED, org.eclipse.lsp4j.DiagnosticTag.Unnecessary,
      DiagnosticTag.DEPRECATED, org.eclipse.lsp4j.DiagnosticTag.Deprecated
    );
  }
}
