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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CachePathProviderTest {

  private CachePathProvider cachePathProvider;

  @BeforeEach
  void setUp() {
    cachePathProvider = new CachePathProvider();
  }

  @Test
  void getCachePath_shouldReturnPathInBasePath() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath).isNotNull();
    assertThat(cachePath.toString()).startsWith(basePath);
  }

  @Test
  void getCachePath_shouldContainBslLanguageServerDirectory() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).contains(".bsl-language-server");
  }

  @Test
  void getCachePath_shouldContainCacheSubdirectory() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).contains("cache");
  }

  @Test
  void getCachePath_shouldContainHashSubdirectory() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Verify that path has at least 4 components: basePath, .bsl-language-server, cache, hash
    assertThat(cachePath.getNameCount()).isGreaterThanOrEqualTo(4);
    
    // Get the last component (hash)
    var hashComponent = cachePath.getFileName().toString();
    
    // MD5 hash in hex is 32 characters
    assertThat(hashComponent).hasSize(32);
    assertThat(hashComponent).matches("[0-9a-f]{32}");
  }

  @Test
  void getCachePath_shouldBeDeterministic() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath1 = cachePathProvider.getCachePath(basePath, fullPath);
    var cachePath2 = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    // Same working directory should produce same cache path
    assertThat(cachePath1).isEqualTo(cachePath2);
  }

  @Test
  void getCachePath_shouldProduceExpectedStructure() {
    // given
    var basePath = "/home/user";
    var fullPath = "";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    var basePathObj = Path.of(basePath);
    
    // Check that the path structure is: {basePath}/.bsl-language-server/cache/{hash}
    assertThat(cachePath.getParent().getParent().getParent()).isEqualTo(basePathObj);
    assertThat(cachePath.getParent().getParent().getFileName().toString()).isEqualTo(".bsl-language-server");
    assertThat(cachePath.getParent().getFileName().toString()).isEqualTo("cache");
  }

  @Test
  void getCachePath_shouldUseFullPathWhenProvided() {
    // given
    var basePath = "/home/user";
    var fullPath = "/custom/cache/path";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).isEqualTo(fullPath);
    assertThat(cachePath.toString()).doesNotContain(".bsl-language-server");
  }

  @Test
  void getCachePath_shouldIgnoreBasePathWhenFullPathProvided() {
    // given
    var basePath = "/home/user";
    var fullPath = "/completely/different/path";

    // when
    var cachePath = cachePathProvider.getCachePath(basePath, fullPath);

    // then
    assertThat(cachePath.toString()).isEqualTo(fullPath);
    assertThat(cachePath.toString()).doesNotContain(basePath);
  }

  @Test
  void getCachePath_shouldTreatNullFullPathAsEmpty() {
    // given
    var basePath = "/home/user";
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
}
