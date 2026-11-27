/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

/**
 * Поиск и чтение файлов конфигурации BSL Language Server.
 * <p>
 * Единственное место, где задан порядок поиска файла конфигурации и политика ошибок его чтения.
 */
@Slf4j
@UtilityClass
public class ConfigurationFiles {

  private static final JsonMapper MAPPER = JsonMapper.builder()
    .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
    .build();

  /**
   * Найти файл конфигурации.
   * <p>
   * Кандидаты проверяются по порядку, побеждает первый существующий файл (не каталог):
   * <ol>
   *   <li>{@code configurationPath} как есть (абсолютный путь или путь относительно рабочего каталога);</li>
   *   <li>{@code configurationPath} относительно корня рабочей области, если он задан;</li>
   *   <li>{@code globalConfigurationPath}.</li>
   * </ol>
   *
   * @param configurationPath       Путь к файлу конфигурации ({@code app.configuration.path})
   * @param workspaceRoot           Корень рабочей области или {@code null}, если поиск в ней не нужен
   * @param globalConfigurationPath Путь к глобальному файлу конфигурации ({@code app.globalConfiguration.path})
   * @return Найденный файл конфигурации
   */
  public static Optional<File> locate(
    String configurationPath,
    @Nullable Path workspaceRoot,
    String globalConfigurationPath
  ) {
    var workspaceCandidate = Optional.ofNullable(workspaceRoot)
      .map(root -> root.resolve(configurationPath).toFile())
      .stream();

    return Stream.of(
        Stream.of(new File(configurationPath)),
        workspaceCandidate,
        Stream.of(new File(globalConfigurationPath))
      )
      .flatMap(candidates -> candidates)
      .filter(File::isFile)
      .findFirst();
  }

  /**
   * Прочитать файл конфигурации.
   * <p>
   * Перечисления читаются без учёта регистра. Ошибки чтения файла и разбора JSON (в том числе
   * значения неподходящего типа) не пробрасываются: они логируются, а результат пуст — вызывающая
   * сторона сохраняет текущие настройки.
   *
   * @param file Файл конфигурации
   * @param type Класс, в который читается конфигурация
   * @param <T>  Тип конфигурации
   * @return Прочитанная конфигурация или пустой результат при ошибке
   */
  public static <T> Optional<T> read(File file, Class<T> type) {
    try (var inputStream = Files.newInputStream(file.toPath())) {
      return Optional.of(MAPPER.readValue(inputStream, type));
    } catch (IOException | JacksonException e) {
      LOGGER.error("Can't deserialize configuration file {}", file, e);
      return Optional.empty();
    }
  }
}
