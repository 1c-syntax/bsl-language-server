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

import com.github._1c_syntax.bsl.languageserver.diagnostics.typo.WordStatus;
import org.ehcache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigurationTest {

  private CacheConfiguration cacheConfiguration;
  private org.ehcache.CacheManager ehcacheManager;

  @AfterEach
  void tearDown() {
    if (ehcacheManager != null && ehcacheManager.getStatus() != org.ehcache.Status.UNINITIALIZED) {
      ehcacheManager.close();
    }
  }

  @Test
  void testCreateInMemoryEhcacheManager() {
    // given
    cacheConfiguration = new CacheConfiguration();

    // when
    ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
      cacheConfiguration,
      "createInMemoryEhcacheManager"
    );

    // then
    assertThat(ehcacheManager).isNotNull();
    assertThat(ehcacheManager.getStatus()).isEqualTo(org.ehcache.Status.AVAILABLE);

    // Verify cache is created and accessible
    Cache<String, WordStatus> cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
    assertThat(cache).isNotNull();

    // Verify cache works
    cache.put("test", WordStatus.NO_ERROR);
    assertThat(cache.get("test")).isEqualTo(WordStatus.NO_ERROR);
  }

  @Test
  void testCreateEhcacheManagerWithRetry_FirstAttemptSucceeds(@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ALWAYS) Path tempDir) {
    // given
    cacheConfiguration = new CacheConfiguration();
    var cachePathProvider = new CachePathProvider();
    var basePath = tempDir.toString();
    var fullPath = "";

    try {
      // when
      ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
        cacheConfiguration,
        "createEhcacheManagerWithRetry",
        cachePathProvider,
        basePath,
        fullPath
      );

      // then
      assertThat(ehcacheManager).isNotNull();
      assertThat(ehcacheManager.getStatus()).isEqualTo(org.ehcache.Status.AVAILABLE);

      // Verify cache is created
      Cache<String, WordStatus> cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
      assertThat(cache).isNotNull();
    } finally {
      // Ensure cache is closed before cleanup
      if (ehcacheManager != null && ehcacheManager.getStatus() != org.ehcache.Status.UNINITIALIZED) {
        ehcacheManager.close();
        ehcacheManager = null;
        // Give Windows time to release file locks
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Test
  void testCreateEhcacheManagerWithRetry_FallbackToInMemory(@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ALWAYS) Path tempDir) throws IOException {
    // given
    cacheConfiguration = new CacheConfiguration();
    var cachePathProvider = new CachePathProvider();
    var basePath = tempDir.toString();
    var fullPath = "";

    // Create and lock all 10 cache directories to force fallback to in-memory
    org.ehcache.CacheManager[] lockedManagers = new org.ehcache.CacheManager[10];
    try {
      for (int i = 0; i < 10; i++) {
        var cachePath = cachePathProvider.getCachePath(basePath, fullPath, i);
        Files.createDirectories(cachePath);
        
        lockedManagers[i] = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
          cacheConfiguration,
          "createEhcacheManager",
          cachePath
        );
      }

      // when - all directories are locked, should fall back to in-memory
      ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
        cacheConfiguration,
        "createEhcacheManagerWithRetry",
        cachePathProvider,
        basePath,
        fullPath
      );

      // then
      assertThat(ehcacheManager).isNotNull();
      assertThat(ehcacheManager.getStatus()).isEqualTo(org.ehcache.Status.AVAILABLE);

      // Verify cache is created and works
      Cache<String, WordStatus> cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
      assertThat(cache).isNotNull();
      
      cache.put("test", WordStatus.NO_ERROR);
      assertThat(cache.get("test")).isEqualTo(WordStatus.NO_ERROR);

    } finally {
      // Clean up locked managers in reverse order to help with cleanup
      for (int i = lockedManagers.length - 1; i >= 0; i--) {
        if (lockedManagers[i] != null && lockedManagers[i].getStatus() != org.ehcache.Status.UNINITIALIZED) {
          try {
            lockedManagers[i].close();
          } catch (Exception e) {
            // Ignore cleanup errors
          }
        }
      }
      // Give Windows time to release file locks
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void testCreateEhcacheManager_WithValidPath(@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ALWAYS) Path tempDir) throws IOException {
    // given
    cacheConfiguration = new CacheConfiguration();
    var cachePath = tempDir.resolve("cache");
    Files.createDirectories(cachePath);

    try {
      // when
      ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
        cacheConfiguration,
        "createEhcacheManager",
        cachePath
      );

      // then
      assertThat(ehcacheManager).isNotNull();
      assertThat(ehcacheManager.getStatus()).isEqualTo(org.ehcache.Status.AVAILABLE);

      // Verify cache is created with persistence
      Cache<String, WordStatus> cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
      assertThat(cache).isNotNull();

      // Verify cache works
      cache.put("persistentKey", WordStatus.HAS_ERROR);
      assertThat(cache.get("persistentKey")).isEqualTo(WordStatus.HAS_ERROR);
    } finally {
      // Ensure cache is closed before cleanup
      if (ehcacheManager != null && ehcacheManager.getStatus() != org.ehcache.Status.UNINITIALIZED) {
        ehcacheManager.close();
        ehcacheManager = null;
        // Give Windows time to release file locks
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  @Test
  void testInMemoryCacheDoesNotPersist() {
    // given
    cacheConfiguration = new CacheConfiguration();

    // when
    ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
      cacheConfiguration,
      "createInMemoryEhcacheManager"
    );

    // then
    Cache<String, WordStatus> cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
    cache.put("key1", WordStatus.NO_ERROR);
    cache.put("key2", WordStatus.HAS_ERROR);

    assertThat(cache.get("key1")).isEqualTo(WordStatus.NO_ERROR);
    assertThat(cache.get("key2")).isEqualTo(WordStatus.HAS_ERROR);

    // Close and recreate - data should be lost
    ehcacheManager.close();

    ehcacheManager = (org.ehcache.CacheManager) ReflectionTestUtils.invokeMethod(
      cacheConfiguration,
      "createInMemoryEhcacheManager"
    );

    cache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
    
    // Data should not persist
    assertThat(cache.get("key1")).isNull();
    assertThat(cache.get("key2")).isNull();
  }
}
