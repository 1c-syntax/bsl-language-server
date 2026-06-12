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
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Реализация {@link LsifWriter} для формата NDJSON (Newline Delimited JSON).
 * <p>
 * Каждый LSIF-элемент записывается на отдельной строке как JSON-объект.
 * Это рекомендуемый формат для больших проектов, так как позволяет
 * потоковую обработку файла.
 * <p>
 * Пример вывода:
 * <pre>
 * {"id":1,"type":"vertex","label":"metaData",...}
 * {"id":2,"type":"vertex","label":"project",...}
 * {"id":3,"type":"edge","label":"contains",...}
 * </pre>
 *
 * @see <a href="https://github.com/ndjson/ndjson-spec">NDJSON Specification</a>
 */
public class NdJsonLsifWriter implements LsifWriter {

  private final BufferedWriter writer;
  private final ObjectMapper objectMapper;

  /**
   * Создаёт новый NDJSON-writer для указанного файла.
   *
   * @param outputFile путь к выходному файлу
   * @throws IOException если не удалось создать файл
   */
  public NdJsonLsifWriter(Path outputFile) throws IOException {
    this.writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
    this.objectMapper = JsonMapper.builder().build();
  }

  @Override
  public void write(Object element) throws IOException {
    writer.write(objectMapper.writeValueAsString(element));
    writer.newLine();
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
