/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.ls_core.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.ls_core.diagnostics.CoreDiagnostic;
import com.github._1c_syntax.ls_core.diagnostics.metadata.CoreDiagnosticInfo;
import com.github._1c_syntax.ls_core.diagnostics.metadata.DiagnosticParameterInfo;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class BSLDiagnosticInfo extends CoreDiagnosticInfo {

  private static final Map<DiagnosticTag, org.eclipse.lsp4j.DiagnosticTag> diagnosticTagMap = createDiagnosticTagMap();

  public BSLDiagnosticInfo(
    Class<? extends CoreDiagnostic> diagnosticClass,
    LanguageServerConfiguration configuration
  ) {
    super(diagnosticClass, configuration);

    // переинициализация параметров из-за изменения класса аннотации
    setDiagnosticMetadata(diagnosticClass.getAnnotation(annotationClass()));
    setDiagnosticParameters(DiagnosticParameterInfo.createDiagnosticParameters(this));
  }

  /**
   * Для переопределения класса аннотации-метаданных диагностики
   *
   * @return Класс аннотации
   */
  @Override
  protected Class<? extends Annotation> annotationClass() {
    return DiagnosticMetadata.class;
  }

  public String getDescription() {
    String langCode = getConfiguration().getLanguage().getLanguageCode();

    String resourceName = langCode + "/" + getDiagnosticCode().getStringValue() + ".md";
    InputStream descriptionStream = getDiagnosticClass().getResourceAsStream(resourceName);

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


  @SneakyThrows
  public DiagnosticCompatibilityMode getCompatibilityMode() {
    return (DiagnosticCompatibilityMode) getDiagnosticMetadata().getClass().getDeclaredMethod("compatibilityMode")
      .invoke(getDiagnosticMetadata());
  }

  @SneakyThrows
  public DiagnosticScope getScope() {
    return (DiagnosticScope) getDiagnosticMetadata().getClass().getDeclaredMethod("scope")
      .invoke(getDiagnosticMetadata());
  }

  @SneakyThrows
  public ModuleType[] getModules() {
    return (ModuleType[]) getDiagnosticMetadata().getClass().getDeclaredMethod("modules")
      .invoke(getDiagnosticMetadata());
  }

  @SneakyThrows
  public List<DiagnosticTag> getTags() {
    return new ArrayList<>(
      Arrays.asList((DiagnosticTag[]) getDiagnosticMetadata().getClass().getDeclaredMethod("tags")
        .invoke(getDiagnosticMetadata())));
  }

  @Override
  public List<org.eclipse.lsp4j.DiagnosticTag> getLSPTags() {
    return getTags().stream()
      .map(diagnosticTag -> diagnosticTagMap.getOrDefault(diagnosticTag, null))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private static Map<DiagnosticTag, org.eclipse.lsp4j.DiagnosticTag> createDiagnosticTagMap() {
    return Map.of(
      DiagnosticTag.UNUSED, org.eclipse.lsp4j.DiagnosticTag.Unnecessary,
      DiagnosticTag.DEPRECATED, org.eclipse.lsp4j.DiagnosticTag.Deprecated
    );
  }
}
