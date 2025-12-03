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
import org.jspecify.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Метаинформация о диагностике.
 * <p>
 * Содержит полную информацию о диагностике: метаданные из аннотации,
 * описание на разных языках, параметры конфигурации, правила активации.
 * Используется для настройки, фильтрации и отображения диагностик.
 */
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

  private Optional<DiagnosticMetadata> metadataOverride;
  private org.eclipse.lsp4j.DiagnosticSeverity lspSeverity;

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
    metadataOverride = computeMetadataOverride();
    lspSeverity = computeLSPSeverity();
  }

  public void refresh() {
    metadataOverride = computeMetadataOverride();
    lspSeverity = computeLSPSeverity();
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
      .map(DiagnosticMetadata::type)
      .orElseGet(diagnosticMetadata::type);
  }

  public DiagnosticSeverity getSeverity() {
    return metadataOverride
      .map(DiagnosticMetadata::severity)
      .orElseGet(diagnosticMetadata::severity);
  }

  public org.eclipse.lsp4j.DiagnosticSeverity getLSPSeverity() {
    return lspSeverity;
  }
  
  private org.eclipse.lsp4j.DiagnosticSeverity computeLSPSeverity() {
    org.eclipse.lsp4j.DiagnosticSeverity result = null;
    
    // First check if lspSeverity is explicitly set in metadata override from config
    var lspSeverityFromOverride = metadataOverride
      .map(DiagnosticMetadata::lspSeverity)
      .filter(not(String::isEmpty));
    
    if (lspSeverityFromOverride.isPresent()) {
      result = parseLspSeverity(lspSeverityFromOverride.get());
    }
    
    // If not set in config, check if lspSeverity is explicitly set in annotation
    if (result == null) {
      var lspSeverityFromAnnotation = diagnosticMetadata.lspSeverity();
      if (lspSeverityFromAnnotation != null && !lspSeverityFromAnnotation.isEmpty()) {
        result = parseLspSeverity(lspSeverityFromAnnotation);
      }
    }
    
    // If still null, calculate based on type and severity
    if (result == null) {
      var type = getType();
      if (type == DiagnosticType.CODE_SMELL) {
        result = severityToLSPSeverityMap.get(getSeverity());
      } else if (type == DiagnosticType.SECURITY_HOTSPOT) {
        result = org.eclipse.lsp4j.DiagnosticSeverity.Warning;
      } else {
        result = org.eclipse.lsp4j.DiagnosticSeverity.Error;
      }
    }
    
    // Apply minimum severity override if configured
    var overrideMinimum = configuration.getDiagnosticsOptions().getOverrideMinimumLSPDiagnosticLevel();
    if (result != null && overrideMinimum != null && result.getValue() > overrideMinimum.getValue()) {
      result = overrideMinimum;
    }
    
    return result;
  }

  public DiagnosticCompatibilityMode getCompatibilityMode() {
    return metadataOverride
      .map(DiagnosticMetadata::compatibilityMode)
      .orElseGet(diagnosticMetadata::compatibilityMode);
  }

  public DiagnosticScope getScope() {
    return metadataOverride
      .map(DiagnosticMetadata::scope)
      .orElseGet(diagnosticMetadata::scope);
  }

  public ModuleType[] getModules() {
    return metadataOverride
      .map(DiagnosticMetadata::modules)
      .orElseGet(diagnosticMetadata::modules);
  }

  public int getMinutesToFix() {
    return metadataOverride
      .map(DiagnosticMetadata::minutesToFix)
      .orElseGet(diagnosticMetadata::minutesToFix);
  }

  public boolean isActivatedByDefault() {
    return metadataOverride
      .map(DiagnosticMetadata::activatedByDefault)
      .orElseGet(diagnosticMetadata::activatedByDefault);
  }

  public List<DiagnosticTag> getTags() {
    return metadataOverride
      .map(metadata -> new ArrayList<>(Arrays.asList(metadata.tags())))
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
      .map(DiagnosticMetadata::canLocateOnProject)
      .orElseGet(diagnosticMetadata::canLocateOnProject);
  }

  public double getExtraMinForComplexity() {
    return metadataOverride
      .map(DiagnosticMetadata::extraMinForComplexity)
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

  private Optional<DiagnosticMetadata> computeMetadataOverride() {
    var diagnosticsOptions = configuration.getDiagnosticsOptions();
    var metadataFromConfig = diagnosticsOptions.getMetadata().get(diagnosticCode.getStringValue());

    return Optional.ofNullable(metadataFromConfig);
  }

  private static org.eclipse.lsp4j.@Nullable DiagnosticSeverity parseLspSeverity(String severityString) {
    if (severityString == null || severityString.isEmpty()) {
      return null;
    }

    try {
      // DiagnosticSeverity constants are named with capital first letter: Error, Warning, Information, Hint
      var normalized = severityString.substring(0, 1).toUpperCase(Locale.ROOT)
        + severityString.substring(1).toLowerCase(Locale.ROOT);
      return org.eclipse.lsp4j.DiagnosticSeverity.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Unknown LSP severity value: {}, using null", severityString);
      return null;
    }
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
