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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github._1c_syntax.bsl.languageserver.utils.Absolute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

@Data
@AllArgsConstructor
@Slf4j
public final class LanguageServerConfiguration {

  public static final DiagnosticLanguage DEFAULT_DIAGNOSTIC_LANGUAGE = DiagnosticLanguage.RU;
  private static final boolean DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS = Boolean.TRUE;
  private static final boolean DEFAULT_SHOW_CYCLOMATIC_COMPLEXITY_CODE_LENS = Boolean.TRUE;
  private static final ComputeDiagnosticsTrigger DEFAULT_COMPUTE_DIAGNOSTICS = ComputeDiagnosticsTrigger.ONSAVE;

  private static final Pattern searchConfiguration = Pattern.compile("Configuration.(xml|mdo)$");

  private DiagnosticLanguage diagnosticLanguage;
  private boolean showCognitiveComplexityCodeLens;
  private boolean showCyclomaticComplexityCodeLens;
  private ComputeDiagnosticsTrigger computeDiagnostics;
  @Nullable
  private File traceLog;
  @JsonDeserialize(using = LanguageServerConfiguration.DiagnosticsDeserializer.class)
  private Map<String, Either<Boolean, Map<String, Object>>> diagnostics;
  @Nullable
  private Path configurationRoot;

  private LanguageServerConfiguration() {
    this(
      DEFAULT_DIAGNOSTIC_LANGUAGE,
      DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS,
      DEFAULT_SHOW_CYCLOMATIC_COMPLEXITY_CODE_LENS,
      DEFAULT_COMPUTE_DIAGNOSTICS,
      null,
      new HashMap<>(),
      null
    );
  }

  private LanguageServerConfiguration(DiagnosticLanguage diagnosticLanguage) {
    this(
      diagnosticLanguage,
      DEFAULT_SHOW_COGNITIVE_COMPLEXITY_CODE_LENS,
      DEFAULT_SHOW_CYCLOMATIC_COMPLEXITY_CODE_LENS,
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
      mapper.enable(ACCEPT_CASE_INSENSITIVE_ENUMS);

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

  public static LanguageServerConfiguration create(DiagnosticLanguage diagnosticLanguage) {
    return new LanguageServerConfiguration(diagnosticLanguage);
  }

  static class DiagnosticsDeserializer extends JsonDeserializer<Map<String, Either<Boolean, Map<String, Object>>>> {

    @Override
    public Map<String, Either<Boolean, Map<String, Object>>> deserialize(
      JsonParser p,
      DeserializationContext context
    ) throws IOException {

      JsonNode diagnostics = p.getCodec().readTree(p);

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

  }

  public static Path getCustomConfigurationRoot(LanguageServerConfiguration configuration, Path srcDir) {
    Path rootPath = null;
    Path pathFromConfiguration = configuration.getConfigurationRoot();
    if (pathFromConfiguration == null) {
      rootPath = Absolute.path(srcDir);
    } else {
      // Проверим, что srcDir = pathFromConfiguration или что pathFromConfiguration находится внутри srcDir
      var absoluteSrcDir = Absolute.path(srcDir);
      var absolutePathFromConfiguration = Absolute.path(pathFromConfiguration);
      if (absolutePathFromConfiguration.startsWith(absoluteSrcDir)) {
        rootPath = absolutePathFromConfiguration;
      }
    }
    if (rootPath != null){
      File fileConfiguration = getConfigurationFile(rootPath);
      if (fileConfiguration != null) {
        rootPath = Paths.get(fileConfiguration.getParent());
      }
    }
    return rootPath;
  }


  private static File getConfigurationFile(Path rootPath) {
    File configurationFile = null;
    List<Path> listPath = new ArrayList<>();
    try (Stream<Path> stream = Files.find(rootPath, 50, (path, basicFileAttributes) ->
      basicFileAttributes.isRegularFile() && searchConfiguration.matcher(path.getFileName().toString()).find())) {
      listPath = stream.collect(Collectors.toList());
    } catch (IOException e) {
      LOGGER.error("Error on read configuration file", e);
    }
    if (!listPath.isEmpty()) {
      configurationFile = listPath.get(0).toFile();
    }
    return configurationFile;
  }

}
