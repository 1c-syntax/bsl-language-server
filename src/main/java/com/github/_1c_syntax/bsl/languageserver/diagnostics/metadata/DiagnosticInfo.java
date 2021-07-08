/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
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

  private final Class<? extends BSLDiagnostic> diagnosticClass;
  private final LanguageServerConfiguration configuration;

  private final DiagnosticCode diagnosticCode;
  private final DiagnosticMetadata diagnosticMetadata;
  private final List<DiagnosticParameterInfo> diagnosticParameters;

  public DiagnosticInfo(
    Class<? extends BSLDiagnostic> diagnosticClass,
    LanguageServerConfiguration configuration
  ) {
    this.diagnosticClass = diagnosticClass;
    this.configuration = configuration;

    diagnosticCode = createDiagnosticCode();
    diagnosticMetadata = diagnosticClass.getAnnotation(DiagnosticMetadata.class);
    diagnosticParameters = DiagnosticParameterInfo.createDiagnosticParameters(this);
  }

  public Class<? extends BSLDiagnostic> getDiagnosticClass() {
    return diagnosticClass;
  }

  public DiagnosticCode getCode() {
    return diagnosticCode;
  }

  public String getDiagnosticCodeDescriptionHref() {
    var language = configuration.getLanguage();
    boolean useDevSite = configuration.isUseDevSite();

    var siteRoot = configuration.getSiteRoot();
    var devSuffix = useDevSite ? "/dev" : "";
    var languageSuffix = language == Language.EN ? "/en" : "";

    var siteDiagnosticsUrl = String.format(
      "%s%s%s/diagnostics/",
      siteRoot,
      devSuffix,
      languageSuffix
    );

    return siteDiagnosticsUrl + diagnosticCode.getStringValue();
  }

  public String getName() {
    return getResourceString("diagnosticName");
  }

  public String getDescription() {
    String langCode = configuration.getLanguage().getLanguageCode();

    String resourceName = langCode + "/" + diagnosticCode.getStringValue() + ".md";
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
    return Resources.getResourceString(configuration.getLanguage(), diagnosticClass, key);
  }

  public String getResourceString(String key, Object... args) {
    return Resources.getResourceString(configuration.getLanguage(), diagnosticClass, key, args);
  }

  public DiagnosticType getType() {
    return diagnosticMetadata.type();
  }

  public DiagnosticSeverity getSeverity() {
    return diagnosticMetadata.severity();
  }

  public org.eclipse.lsp4j.DiagnosticSeverity getLSPSeverity() {
    var type = getType();
    if (type == DiagnosticType.CODE_SMELL) {
      return severityToLSPSeverityMap.get(getSeverity());
    } else if (type == DiagnosticType.SECURITY_HOTSPOT) {
      return org.eclipse.lsp4j.DiagnosticSeverity.Warning;
    } else {
      return org.eclipse.lsp4j.DiagnosticSeverity.Error;
    }
  }

  public DiagnosticCompatibilityMode getCompatibilityMode() {
    return diagnosticMetadata.compatibilityMode();
  }

  public DiagnosticScope getScope() {
    return diagnosticMetadata.scope();
  }

  public ModuleType[] getModules() {
    return diagnosticMetadata.modules();
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

  public Map<String, Object> getDefaultConfiguration() {
    return diagnosticParameters.stream()
      .collect(Collectors.toMap(DiagnosticParameterInfo::getName, DiagnosticParameterInfo::getDefaultValue));
  }

  private DiagnosticCode createDiagnosticCode() {
    String simpleName = diagnosticClass.getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }

    return new DiagnosticCode(simpleName.intern());
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
