/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CachePathProviderTest {

  private CachePathProvider cachePathProvider;

  @BeforeEach
  void setUp() {
    cachePathProvider = new CachePathProvider();
  }

  @Test
  void getCachePath_shouldReturnPathInBasePath(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath).isNotNull();
    assertThat(cachePath.toString()).startsWith(basePath);
  }

  @Test
  void getCachePath_shouldContainBslLanguageServerDirectory(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).contains(".bsl-language-server");
  }

  @Test
  void getCachePath_shouldContainCacheSubdirectory(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).contains("cache");
  }

  @Test
  void getCachePath_shouldContainHashSubdirectory(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Get the last component (hash)
    var hashComponent = cachePath.getFileName().toString();
    
    // MD5 hash in hex is 32 characters
    assertThat(hashComponent).hasSize(32);
    assertThat(hashComponent).matches("[0-9a-f]{32}");
  }

  @Test
  void getCachePath_shouldBeDeterministic(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath);
    var cachePath2 = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Same working directory should produce same cache path
    assertThat(cachePath1).isEqualTo(cachePath2);
  }

  @Test
  void getCachePath_shouldProduceExpectedStructure(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Check that the path structure ends with: .bsl-language-server/cache/{hash}
    assertThat(cachePath.getParent().getParent().getFileName().toString()).isEqualTo(".bsl-language-server");
    assertThat(cachePath.getParent().getFileName().toString()).isEqualTo("cache");
    
    // Verify the path starts with basePath
    assertThat(cachePath.toString()).startsWith(basePath);
  }

  @Test
  void getCachePath_shouldUseFullPathWhenProvided(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var customPath = tempDir.resolve("custom").resolve("cache").resolve("path");
    var fullPath = customPath.toString();

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).isEqualTo(fullPath);
    assertThat(cachePath.toString()).doesNotContain(".bsl-language-server");
  }

  @Test
  void getCachePath_shouldIgnoreBasePathWhenFullPathProvided(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.resolve("base").toString();
    var customPath = tempDir.resolve("completely").resolve("different").resolve("path");
    var fullPath = customPath.toString();

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).isEqualTo(fullPath);
    assertThat(cachePath.toString()).doesNotContain(basePath);
  }

  @Test
  void getCachePath_shouldTreatNullFullPathAsEmpty(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    String fullPath = null;

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Should behave as if fullPath is empty - compute the path
    assertThat(cachePath.toString()).startsWith(basePath);
    assertThat(cachePath.toString()).contains(".bsl-language-server");
  }

  @Test
  void getCachePath_withRealSystemProperties() {
    // given
    var basePath = System.getProperty("user.home");
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath).isNotNull();
    assertThat(cachePath.toString()).startsWith(basePath);
    assertThat(cachePath.toString()).contains(".bsl-language-server");
    assertThat(cachePath.toString()).contains("cache");
  }

  @Test
  void getCachePath_withInstanceNumber_shouldAddSuffix(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath, 1);
    var cachePath2 = cachePathProvider.getCachePath(basePath, fullPath, 2);

    // then
    assertThat(cachePath1.getFileName().toString()).endsWith("@1");
    assertThat(cachePath2.getFileName().toString()).endsWith("@2");
  }

  @Test
  void getCachePath_withInstanceNumberZero_shouldNotAddSuffix(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath, 0);

    // then
    var fileName = cachePath.getFileName().toString();
    assertThat(fileName).doesNotContain("@");
    assertThat(fileName).hasSize(32); // MD5 hash length
  }

  @Test
  void getCachePath_instanceNumberedPaths_shouldBeDifferent(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath0 = cachePathProvider.getCachePath(basePath, fullPath, 0);
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath, 1);
    var cachePath2 = cachePathProvider.getCachePath(basePath, fullPath, 2);

    // then
    assertThat(cachePath0).isNotEqualTo(cachePath1);
    assertThat(cachePath0).isNotEqualTo(cachePath2);
    assertThat(cachePath1).isNotEqualTo(cachePath2);
  }

  @Test
  void getCachePath_withInstanceNumber_shouldShareParentDirectory(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var fullPath = "";

    // when
    var cachePath0 = cachePathProvider.getCachePath(basePath, fullPath, 0);
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath, 1);
    var cachePath2 = cachePathProvider.getCachePath(basePath, fullPath, 2);

    // then
    // All instances should have the same parent (cache directory)
    assertThat(cachePath0.getParent()).isEqualTo(cachePath1.getParent());
    assertThat(cachePath0.getParent()).isEqualTo(cachePath2.getParent());
  }

  @Test
  void getCachePath_withInstanceNumber_shouldIgnoreWhenFullPathProvided(@TempDir Path tempDir) {
    // given
    var basePath = tempDir.toString();
    var customPath = tempDir.resolve("custom").resolve("cache").resolve("path");
    var fullPath = customPath.toString();

    // when
    var cachePath0 = cachePathProvider.getCachePath(basePath, fullPath, 0);
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath, 1);

    // then
    // When fullPath is provided, instance number should be ignored
    assertThat(cachePath0).isEqualTo(customPath);
    assertThat(cachePath1).isEqualTo(customPath);
    assertThat(cachePath0).isEqualTo(cachePath1);
  }
}
