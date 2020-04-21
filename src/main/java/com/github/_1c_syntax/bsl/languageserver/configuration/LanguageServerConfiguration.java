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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.utils.Absolute;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

/**
 * Корневой класс конфигурации BSL Language Server.
 * <p>
 * В обычном режиме работы провайдеры и прочие классы могут расчитывать на единственность объекта конфигурации
 * и безопасно сохранять ссылку на конфигурацию или ее части.
 */
@Data
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LanguageServerConfiguration {

  private static final Pattern searchConfiguration = Pattern.compile("Configuration\\.(xml|mdo)$");

  private Language language;

  @JsonProperty("diagnostics")
  private final DiagnosticsOptions diagnosticsOptions;

  @JsonProperty("codeLens")
  private final CodeLensOptions codeLensOptions;

  @Nullable
  private File traceLog;

  @Nullable
  private Path configurationRoot;

  private LanguageServerConfiguration() {
    this(
      Language.DEFAULT_LANGUAGE,
      new DiagnosticsOptions(),
      new CodeLensOptions(),
      null,
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

    if (rootPath != null) {
      File fileConfiguration = getConfigurationFile(rootPath);
      if (fileConfiguration != null) {
        if (fileConfiguration.getAbsolutePath().endsWith(".mdo")) {
          rootPath = Optional.of(fileConfiguration.toPath())
            .map(Path::getParent)
            .map(Path::getParent)
            .map(Path::getParent)
            .orElse(null);
        } else {
          rootPath = Optional.of(fileConfiguration.toPath())
            .map(Path::getParent)
            .orElse(null);
        }
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
