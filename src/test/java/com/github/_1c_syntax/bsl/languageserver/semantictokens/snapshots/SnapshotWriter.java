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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import lombok.SneakyThrows;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Записывает снапшот в каталог выгрузки Sentry Snapshots: {@code <name>.png} и
 * {@code <name>.json} с метаданными.
 */
public class SnapshotWriter {

  private final JsonMapper mapper = JsonMapper.builder()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build();

  /**
   * Записывает изображение и его метаданные, создавая недостающие каталоги.
   *
   * @param outputDir корневой каталог снапшотов
   * @param name      имя снапшота без расширения; может содержать подкаталоги через {@code /}
   * @param image     изображение
   * @param metadata  метаданные
   */
  @SneakyThrows
  public void write(Path outputDir, String name, BufferedImage image, SnapshotMetadata metadata) {
    var png = outputDir.resolve(name + ".png");
    Files.createDirectories(png.getParent());
    ImageIO.write(image, "png", png.toFile());
    mapper.writeValue(outputDir.resolve(name + ".json").toFile(), metadata);
  }
}
