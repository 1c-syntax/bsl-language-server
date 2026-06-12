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
package com.github._1c_syntax.bsl.languageserver.lsif.writer;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация {@link LsifWriter} для формата JSON (массив элементов).
 * <p>
 * Все LSIF-элементы накапливаются в памяти и записываются как JSON-массив
 * при закрытии writer. Удобно для отладки и просмотра, но требует больше памяти.
 * <p>
 * Пример вывода:
 * <pre>
 * [
 *   {"id":1,"type":"vertex","label":"metaData",...},
 *   {"id":2,"type":"vertex","label":"project",...},
 *   {"id":3,"type":"edge","label":"contains",...}
 * ]
 * </pre>
 */
public class JsonLsifWriter implements LsifWriter {

  private final Path outputFile;
  private final ObjectMapper objectMapper;
  private final List<Object> elements;

  /**
   * Создаёт новый JSON-writer для указанного файла.
   *
   * @param outputFile путь к выходному файлу
   */
  public JsonLsifWriter(Path outputFile) {
    this.outputFile = outputFile;
    this.objectMapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();
    this.elements = new ArrayList<>();
  }

  @Override
  public void write(Object element) {
    elements.add(element);
  }

  @Override
  public void close() throws IOException {
    var json = objectMapper.writeValueAsString(elements);
    Files.writeString(outputFile, json, StandardCharsets.UTF_8);
  }
}
