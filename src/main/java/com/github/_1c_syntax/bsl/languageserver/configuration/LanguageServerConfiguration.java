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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Data
@AllArgsConstructor
@JsonDeserialize(using = LanguageServerConfiguration.LanguageServerConfigurationDeserializer.class)
public final class LanguageServerConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerConfiguration.class.getSimpleName());
  private static final DiagnosticLanguage DEFAULT_DIAGNOSTIC_LANGUAGE = DiagnosticLanguage.RU;
  private static final boolean DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS = Boolean.TRUE;
  private static final ComputeDiagnosticsTrigger DEFAULT_COMPUTE_DIAGNOSTICS = ComputeDiagnosticsTrigger.ONSAVE;

  private DiagnosticLanguage diagnosticLanguage;
  private boolean showCognitiveComplexityCodeLens;
  private ComputeDiagnosticsTrigger computeDiagnostics;
  @Nullable
  private File traceLog;
  private Map<String, Either<Boolean, Map<String, Object>>> diagnostics;
  @Nullable
  private Path configurationRoot;

  private LanguageServerConfiguration() {
    this(
      DEFAULT_DIAGNOSTIC_LANGUAGE,
      DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS,
      DEFAULT_COMPUTE_DIAGNOSTICS,
      null,
      new HashMap<>(),
      null
    );
  }

  public static LanguageServerConfiguration create(File configurationFile) {
    LanguageServerConfiguration configuration = null;
    if (configurationFile.exists()) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        configuration = mapper.readValue(configurationFile, LanguageServerConfiguration.class);
      } catch (IOException e) {
        LOGGER.error("Can't deserialize configuration file", e);
      }
    }

    if (configuration == null) {
      configuration = create();
    }
    return configuration;
  }

  public static LanguageServerConfiguration create() {
    return new LanguageServerConfiguration();
  }

  static class LanguageServerConfigurationDeserializer extends JsonDeserializer<LanguageServerConfiguration> {

    @Override
    public LanguageServerConfiguration deserialize(JsonParser jp, DeserializationContext context) throws IOException {
      JsonNode node = jp.getCodec().readTree(jp);

      DiagnosticLanguage diagnosticLanguage = getDiagnosticLanguage(node);
      boolean showCognitiveComplexityCodeLens = getShowCognitiveComplexityCodeLens(node);
      ComputeDiagnosticsTrigger computeDiagnostics = getComputeDiagnostics(node);
      File traceLog = getTraceLog(node);
      Map<String, Either<Boolean, Map<String, Object>>> diagnosticsMap = getDiagnostics(node);
      Path configurationRoot = getConfigurationRoot(node);

      return new LanguageServerConfiguration(
        diagnosticLanguage,
        showCognitiveComplexityCodeLens,
        computeDiagnostics,
        traceLog,
        diagnosticsMap,
        configurationRoot
      );
    }

    private static Map<String, Either<Boolean, Map<String, Object>>> getDiagnostics(JsonNode node) {
      JsonNode diagnostics = node.get("diagnostics");

      if (diagnostics == null) {
        return Collections.emptyMap();
      }

      ObjectMapper mapper = new ObjectMapper();
      Map<String, Either<Boolean, Map<String, Object>>> diagnosticsMap = new HashMap<>();

      Iterator<Map.Entry<String, JsonNode>> diagnosticsNodes = diagnostics.fields();
      diagnosticsNodes.forEachRemaining((Map.Entry<String, JsonNode> entry) -> {
        JsonNode diagnosticConfig = entry.getValue();
        if (diagnosticConfig.isBoolean()) {
          diagnosticsMap.put(entry.getKey(), Either.forLeft(diagnosticConfig.asBoolean()));
        } else {
          Map<String, Object> diagnosticConfiguration = getDiagnosticConfiguration(mapper, entry.getValue());

          diagnosticsMap.put(entry.getKey(), Either.forRight(diagnosticConfiguration));
        }
      });

      return diagnosticsMap;
    }

    private static Map<String, Object> getDiagnosticConfiguration(
      ObjectMapper mapper,
      JsonNode diagnosticConfig
    ) {
      Map<String, Object> diagnosticConfiguration;
      try {
        JavaType type = mapper.getTypeFactory().constructType(new TypeReference<Map<String, Object>>() {
        });
        diagnosticConfiguration = mapper.readValue(mapper.treeAsTokens(diagnosticConfig), type);
      } catch (IOException e) {
        LOGGER.error("Can't deserialize diagnostic configuration", e);
        return null;
      }
      return diagnosticConfiguration;
    }

    private static DiagnosticLanguage getDiagnosticLanguage(JsonNode node) {
      DiagnosticLanguage diagnosticLanguage;
      if (node.get("diagnosticLanguage") != null) {
        String diagnosticLanguageValue = node.get("diagnosticLanguage").asText();
        diagnosticLanguage = DiagnosticLanguage.valueOf(diagnosticLanguageValue.toUpperCase(Locale.ENGLISH));
      } else {
        diagnosticLanguage = DEFAULT_DIAGNOSTIC_LANGUAGE;
      }
      return diagnosticLanguage;
    }

    private static boolean getShowCognitiveComplexityCodeLens(JsonNode node) {
      boolean showCognitiveComplexityCodeLens = DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS;
      if (node.get("showCognitiveComplexityCodeLens") != null) {
        showCognitiveComplexityCodeLens = node.get("showCognitiveComplexityCodeLens").asBoolean();
      }
      return showCognitiveComplexityCodeLens;
    }

    private static ComputeDiagnosticsTrigger getComputeDiagnostics(JsonNode node) {
      ComputeDiagnosticsTrigger computeDiagnostics = DEFAULT_COMPUTE_DIAGNOSTICS;
      if (node.get("computeDiagnostics") != null) {
        String computeDiagnosticsValue = node.get("computeDiagnostics").asText();
        computeDiagnostics = ComputeDiagnosticsTrigger.valueOf(computeDiagnosticsValue.toUpperCase(Locale.ENGLISH));
      }
      return computeDiagnostics;
    }

    private static File getTraceLog(JsonNode node) {
      File traceLog = null;
      if (node.get("traceLog") != null) {
        String traceLogValue = node.get("traceLog").asText();
        traceLog = new File(traceLogValue);
      }
      return traceLog;
    }

    private static Path getConfigurationRoot(JsonNode node) {
      Path configurationRoot = null;
      if (node.get("configurationRoot") != null) {
        String configurationRootValue = node.get("configurationRoot").asText();
        configurationRoot = Paths.get(configurationRootValue).toAbsolutePath();
      }
      return configurationRoot;
    }
  }

  public static Path getCustomConfigurationRoot(LanguageServerConfiguration configuration, Path srcDir) {
    Path rootPath = null;

    Path pathFromConfiguration = configuration.getConfigurationRoot();
    if (pathFromConfiguration == null) {
      rootPath = srcDir;
    } else {
      // TODO: проверим, что srcDir = pathFromConfiguration или что pathFromConfiguration находится внутри srcDir
      rootPath = pathFromConfiguration;
    }

    if (rootPath != null){
      // TODO: ищем рекурсивно вниз по дереву каталогов, где лежим Configuration.xml(mdo)
      // если не нашли rootPath = null
    }

    return rootPath;
  }

}
