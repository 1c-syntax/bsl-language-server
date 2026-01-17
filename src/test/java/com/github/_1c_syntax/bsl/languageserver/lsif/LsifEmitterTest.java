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

import com.github._1c_syntax.bsl.languageserver.lsif.dto.LsifConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LsifEmitterTest {

  @TempDir
  Path tempDir;

  @Test
  void testEmitMetaData() throws IOException {
    // given
    Path outputFile = tempDir.resolve("test.lsif");

    // when
    try (var emitter = new LsifEmitter(outputFile)) {
      emitter.emitMetaData("0.6.0", "file:///project", "bsl-language-server", "1.0.0");
    }

    // then
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).contains("\"label\":\"metaData\"");
    assertThat(lsifContent).contains("\"version\":\"0.6.0\"");
    assertThat(lsifContent).contains("\"projectRoot\":\"file:///project\"");
    assertThat(lsifContent).contains("\"name\":\"bsl-language-server\"");
  }

  @Test
  void testEmitProject() throws IOException {
    // given
    Path outputFile = tempDir.resolve("test.lsif");

    // when
    try (var emitter = new LsifEmitter(outputFile)) {
      emitter.emitProject("bsl");
    }

    // then
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).contains("\"label\":\"project\"");
    assertThat(lsifContent).contains("\"kind\":\"bsl\"");
  }

  @Test
  void testEmitDocument() throws IOException {
    // given
    Path outputFile = tempDir.resolve("test.lsif");

    // when
    try (var emitter = new LsifEmitter(outputFile)) {
      emitter.emitDocument("file:///test.bsl", "bsl");
    }

    // then
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).contains("\"label\":\"document\"");
    assertThat(lsifContent).contains("\"uri\":\"file:///test.bsl\"");
    assertThat(lsifContent).contains("\"languageId\":\"bsl\"");
  }

  @Test
  void testEmitMoniker() throws IOException {
    // given
    Path outputFile = tempDir.resolve("test.lsif");

    // when
    try (var emitter = new LsifEmitter(outputFile)) {
      emitter.emitMoniker(
        LsifConstants.MonikerScheme.BSL,
        "catalog.test:objectmodule:testmethod",
        LsifConstants.MonikerKind.EXPORT,
        "scheme"
      );
    }

    // then
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).contains("\"label\":\"moniker\"");
    assertThat(lsifContent).contains("\"scheme\":\"bsl\"");
    assertThat(lsifContent).contains("\"identifier\":\"catalog.test:objectmodule:testmethod\"");
    assertThat(lsifContent).contains("\"kind\":\"export\"");
  }

  @Test
  void testEmitPackageInformation() throws IOException {
    // given
    Path outputFile = tempDir.resolve("test.lsif");

    // when
    try (var emitter = new LsifEmitter(outputFile)) {
      emitter.emitPackageInformation("my-project", "bsl", "1.0.0");
    }

    // then
    var lsifContent = Files.readString(outputFile);
    assertThat(lsifContent).contains("\"label\":\"packageInformation\"");
    assertThat(lsifContent).contains("\"name\":\"my-project\"");
    assertThat(lsifContent).contains("\"manager\":\"bsl\"");
    assertThat(lsifContent).contains("\"version\":\"1.0.0\"");
  }
}
