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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CachePathProviderTest {

  @Test
  void getCachePath_shouldReturnPathInUserHome() {
    // when
    var cachePath = CachePathProvider.getCachePath();

    // then
    var userHome = System.getProperty("user.home");
    assertThat(cachePath).isNotNull();
    assertThat(cachePath.toString()).startsWith(userHome);
  }

  @Test
  void getCachePath_shouldContainBslLanguageServerDirectory() {
    // when
    var cachePath = CachePathProvider.getCachePath();

    // then
    assertThat(cachePath.toString()).contains(".bsl-language-server");
  }

  @Test
  void getCachePath_shouldContainCacheSubdirectory() {
    // when
    var cachePath = CachePathProvider.getCachePath();

    // then
    assertThat(cachePath.toString()).contains("cache");
  }

  @Test
  void getCachePath_shouldContainHashSubdirectory() {
    // when
    var cachePath = CachePathProvider.getCachePath();

    // then
    // Verify that path has at least 4 components: user.home, .bsl-language-server, cache, hash
    assertThat(cachePath.getNameCount()).isGreaterThanOrEqualTo(4);
    
    // Get the last component (hash)
    var hashComponent = cachePath.getFileName().toString();
    
    // MD5 hash in hex is 32 characters
    assertThat(hashComponent).hasSize(32);
    assertThat(hashComponent).matches("[0-9a-f]{32}");
  }

  @Test
  void getCachePath_shouldBeDeterministic() {
    // when
    var cachePath1 = CachePathProvider.getCachePath();
    var cachePath2 = CachePathProvider.getCachePath();

    // then
    // Same working directory should produce same cache path
    assertThat(cachePath1).isEqualTo(cachePath2);
  }

  @Test
  void getCachePath_shouldProduceExpectedStructure() {
    // when
    var cachePath = CachePathProvider.getCachePath();

    // then
    var userHome = Path.of(System.getProperty("user.home"));
    
    // Check that the path structure is: {user.home}/.bsl-language-server/cache/{hash}
    assertThat(cachePath.getParent().getParent().getParent()).isEqualTo(userHome);
    assertThat(cachePath.getParent().getParent().getFileName().toString()).isEqualTo(".bsl-language-server");
    assertThat(cachePath.getParent().getFileName().toString()).isEqualTo("cache");
  }
}
