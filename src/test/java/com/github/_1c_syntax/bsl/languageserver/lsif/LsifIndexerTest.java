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
package com.github._1c_syntax.bsl.languageserver.lsif;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LsifIndexerTest {

  @Autowired
  private LsifIndexer lsifIndexer;

  @TempDir
  Path tempDir;

  @Test
  void testIndex() throws IOException {
    // given
    Path srcDir = Path.of("./src/test/resources/lsif");
    Path outputFile = tempDir.resolve("dump.lsif");

    // when
    lsifIndexer.index(srcDir, outputFile, "test-version");

    // then
    assertThat(outputFile).exists();
    
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).isNotEmpty();
    
    // Check for metaData vertex
    assertThat(lsifContent).contains("\"label\":\"metaData\"");
    assertThat(lsifContent).contains("\"version\":\"0.6.0\"");
    assertThat(lsifContent).contains("\"toolInfo\"");
    
    // Check for project vertex
    assertThat(lsifContent).contains("\"label\":\"project\"");
    
    // Check for document vertex
    assertThat(lsifContent).contains("\"label\":\"document\"");
    
    // Check for range vertices
    assertThat(lsifContent).contains("\"label\":\"range\"");
    
    // Check for edges
    assertThat(lsifContent).contains("\"type\":\"edge\"");
    assertThat(lsifContent).contains("\"label\":\"contains\"");
    assertThat(lsifContent).contains("\"label\":\"belongsTo\"");
  }

  @Test
  void testIndexEmptyDir() throws IOException {
    // given
    Path emptyDir = tempDir.resolve("empty");
    Files.createDirectories(emptyDir);
    Path outputFile = tempDir.resolve("empty.lsif");

    // when
    lsifIndexer.index(emptyDir, outputFile, "test-version");

    // then
    assertThat(outputFile).exists();
    
    var lsifContent = Files.readString(outputFile);
    // Should contain at least metaData and project
    assertThat(lsifContent).contains("\"label\":\"metaData\"");
    assertThat(lsifContent).contains("\"label\":\"project\"");
  }
}
