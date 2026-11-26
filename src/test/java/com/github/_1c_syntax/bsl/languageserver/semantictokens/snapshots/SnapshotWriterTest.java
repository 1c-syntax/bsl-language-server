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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotWriterTest {

  @TempDir
  private Path outputDir;

  private final SnapshotWriter writer = new SnapshotWriter();

  @Test
  void writesImageAsPng() throws IOException {
    // given
    var image = new BufferedImage(7, 3, BufferedImage.TYPE_INT_RGB);

    // when
    writer.write(outputDir, "sample/light", image, metadata());

    // then
    var written = ImageIO.read(outputDir.resolve("sample/light.png").toFile());
    assertThat(written).isNotNull();
    assertThat(written.getWidth()).isEqualTo(7);
    assertThat(written.getHeight()).isEqualTo(3);
  }

  @Test
  void writesMetadataNextToImageInSentryFormat() {
    // given
    var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    // when
    writer.write(outputDir, "sample/light", image, metadata());

    // then
    var json = JsonMapper.builder().build().readTree(outputDir.resolve("sample/light.json").toFile());
    assertThat(json.get("display_name").asString()).isEqualTo("sample.bsl (light)");
    assertThat(json.get("group").asString()).isEqualTo("semantic-tokens");
    assertThat(json.get("canvas_theme").asString()).isEqualTo("light");
    assertThat(json.get("tags").get("language").asString()).isEqualTo("bsl");
    assertThat(json.get("context").get("tokens").asInt()).isEqualTo(42);
  }

  private static SnapshotMetadata metadata() {
    return new SnapshotMetadata(
      "sample.bsl (light)",
      "semantic-tokens",
      "light",
      Map.of("language", "bsl"),
      Map.of("tokens", 42)
    );
  }
}
