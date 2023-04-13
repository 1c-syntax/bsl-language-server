/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.documentlink.DocumentLinkOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.formating.FormattingOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.inlayhints.InlayHintOptions;
import com.github._1c_syntax.utils.Absolute;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@NoArgsConstructor
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class LanguageServerConfiguration {

  private static final Pattern searchConfiguration = Pattern.compile("Configuration\\.(xml|mdo)$");

  private Language language = Language.DEFAULT_LANGUAGE;

  @JsonProperty("diagnostics")
  @Setter(value = AccessLevel.NONE)
  private DiagnosticsOptions diagnosticsOptions = new DiagnosticsOptions();

  @JsonProperty("codeLens")
  @Setter(value = AccessLevel.NONE)
  private CodeLensOptions codeLensOptions = new CodeLensOptions();

  @JsonProperty("documentLink")
  @Setter(value = AccessLevel.NONE)
  private DocumentLinkOptions documentLinkOptions = new DocumentLinkOptions();

  @JsonProperty("inlayHint")
  @Setter(value = AccessLevel.NONE)
  private InlayHintOptions inlayHintOptions = new InlayHintOptions();

  @JsonProperty("formatting")
  @Setter(value = AccessLevel.NONE)
  private FormattingOptions formattingOptions = new FormattingOptions();

  private String siteRoot = "https://1c-syntax.github.io/bsl-language-server";
  private boolean useDevSite;

  private SendErrorsMode sendErrors = SendErrorsMode.DEFAULT;

  @Nullable
  private File traceLog;

  @Nullable
  private Path configurationRoot;

  @JsonIgnore
  @Setter(value = AccessLevel.NONE)
  private File configurationFile;

  @Value("${app.configuration.path:.bsl-language-server.json}")
  @JsonIgnore
  private String configurationFilePath;

  @Value(("${app.globalConfiguration.path:${user.home}/.bsl-language-server.json}"))
  @JsonIgnore
  private String globalConfigPath;

  @PostConstruct
  private void init() {
    configurationFile = new File(configurationFilePath);
    if (configurationFile.exists()) {
      loadConfigurationFile(configurationFile);
      return;
    }
    var configuration = new File(globalConfigPath);
    if (configuration.exists()) {
      loadConfigurationFile(configuration);
    }
  }

  public void update(File configurationFile) {
    loadConfigurationFile(configurationFile);
  }

  public void reset() {
    copyPropertiesFrom(new LanguageServerConfiguration());
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

  private void loadConfigurationFile(File configurationFile) {
    if (!configurationFile.exists()) {
      return;
    }

    LanguageServerConfiguration configuration;

    var mapper = JsonMapper.builder()
      .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build();

    try {
      configuration = mapper.readValue(configurationFile, LanguageServerConfiguration.class);
    } catch (IOException e) {
      LOGGER.error("Can't deserialize configuration file", e);
      return;
    }

    this.configurationFile = configurationFile;

    copyPropertiesFrom(configuration);
  }

  @SneakyThrows
  private void copyPropertiesFrom(LanguageServerConfiguration configuration) {
    // todo: refactor
    PropertyUtils.copyProperties(this, configuration);
    PropertyUtils.copyProperties(this.inlayHintOptions, configuration.inlayHintOptions);
    PropertyUtils.copyProperties(this.codeLensOptions, configuration.codeLensOptions);
    PropertyUtils.copyProperties(this.diagnosticsOptions, configuration.diagnosticsOptions);
    PropertyUtils.copyProperties(this.documentLinkOptions, configuration.documentLinkOptions);
    PropertyUtils.copyProperties(this.formattingOptions, configuration.formattingOptions);
  }

}
