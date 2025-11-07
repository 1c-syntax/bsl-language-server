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

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache.ValueRetrievalException;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EhCacheAdapterTest {

  private CacheManager cacheManager;
  private Cache<String, String> nativeCache;
  private EhCacheAdapter<String, String> ehCacheAdapter;

  @BeforeEach
  void setUp() {
    cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
      .withCache("testCache",
        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
          ResourcePoolsBuilder.heap(100)))
      .build(true);

    nativeCache = cacheManager.getCache("testCache", String.class, String.class);
    ehCacheAdapter = new EhCacheAdapter<>(nativeCache, "testCache");
  }

  @AfterEach
  void tearDown() {
    if (cacheManager != null) {
      cacheManager.close();
    }
  }

  @Test
  void testGetName() {
    // when
    String name = ehCacheAdapter.getName();

    // then
    assertThat(name).isEqualTo("testCache");
  }

  @Test
  void testGetNativeCache() {
    // when
    Object nativeCacheObject = ehCacheAdapter.getNativeCache();

    // then
    assertThat(nativeCacheObject).isSameAs(nativeCache);
  }

  @Test
  void testGetWithExistingKey() {
    // given
    nativeCache.put("key1", "value1");

    // when
    org.springframework.cache.Cache.ValueWrapper result = ehCacheAdapter.get("key1");

    // then
    assertThat(result).isNotNull();
    assertThat(result.get()).isEqualTo("value1");
  }

  @Test
  void testGetWithNonExistingKey() {
    // when
    org.springframework.cache.Cache.ValueWrapper result = ehCacheAdapter.get("nonExistingKey");

    // then
    assertThat(result).isNull();
  }

  @Test
  void testGetWithTypeExistingKey() {
    // given
    nativeCache.put("key1", "value1");

    // when
    String result = ehCacheAdapter.get("key1", String.class);

    // then
    assertThat(result).isEqualTo("value1");
  }

  @Test
  void testGetWithTypeNonExistingKey() {
    // when
    String result = ehCacheAdapter.get("nonExistingKey", String.class);

    // then
    assertThat(result).isNull();
  }

  @Test
  void testGetWithCallableWhenKeyExists() {
    // given
    nativeCache.put("key1", "existingValue");
    Callable<String> valueLoader = () -> "newValue";

    // when
    String result = ehCacheAdapter.get("key1", valueLoader);

    // then
    assertThat(result).isEqualTo("existingValue");
  }

  @Test
  void testGetWithCallableWhenKeyDoesNotExist() {
    // given
    Callable<String> valueLoader = () -> "loadedValue";

    // when
    String result = ehCacheAdapter.get("key2", valueLoader);

    // then
    assertThat(result).isEqualTo("loadedValue");
    assertThat(nativeCache.get("key2")).isEqualTo("loadedValue");
  }

  @Test
  void testGetWithCallableThrowsException() {
    // given
    Callable<String> valueLoader = () -> {
      throw new RuntimeException("Loader failed");
    };

    // when / then
    assertThatThrownBy(() -> ehCacheAdapter.get("key3", valueLoader))
      .isInstanceOf(ValueRetrievalException.class)
      .hasCauseInstanceOf(RuntimeException.class)
      .cause()
      .hasMessageContaining("Loader failed");
  }

  @Test
  void testPut() {
    // when
    ehCacheAdapter.put("key1", "value1");

    // then
    assertThat(nativeCache.get("key1")).isEqualTo("value1");
  }

  @Test
  void testPutOverwritesExistingValue() {
    // given
    nativeCache.put("key1", "oldValue");

    // when
    ehCacheAdapter.put("key1", "newValue");

    // then
    assertThat(nativeCache.get("key1")).isEqualTo("newValue");
  }

  @Test
  void testPutNullValue() {
    // when-then
    assertThatThrownBy(() -> ehCacheAdapter.put("key1", null))
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  void testEvict() {
    // given
    nativeCache.put("key1", "value1");

    // when
    ehCacheAdapter.evict("key1");

    // then
    assertThat(nativeCache.get("key1")).isNull();
  }

  @Test
  void testEvictNonExistingKey() {
    // when / then - should not throw exception
    ehCacheAdapter.evict("nonExistingKey");
  }

  @Test
  void testClear() {
    // given
    nativeCache.put("key1", "value1");
    nativeCache.put("key2", "value2");
    nativeCache.put("key3", "value3");

    // when
    ehCacheAdapter.clear();

    // then
    assertThat(nativeCache.get("key1")).isNull();
    assertThat(nativeCache.get("key2")).isNull();
    assertThat(nativeCache.get("key3")).isNull();
  }

  @Test
  void testMultipleOperations() {
    // Test a sequence of operations
    // put
    ehCacheAdapter.put("key1", "value1");
    assertThat(ehCacheAdapter.get("key1", String.class)).isEqualTo("value1");

    // update
    ehCacheAdapter.put("key1", "value2");
    assertThat(ehCacheAdapter.get("key1", String.class)).isEqualTo("value2");

    // evict
    ehCacheAdapter.evict("key1");
    assertThat(ehCacheAdapter.get("key1", String.class)).isNull();

    // put multiple
    ehCacheAdapter.put("key1", "value1");
    ehCacheAdapter.put("key2", "value2");
    assertThat(ehCacheAdapter.get("key1", String.class)).isEqualTo("value1");
    assertThat(ehCacheAdapter.get("key2", String.class)).isEqualTo("value2");

    // clear all
    ehCacheAdapter.clear();
    assertThat(ehCacheAdapter.get("key1", String.class)).isNull();
    assertThat(ehCacheAdapter.get("key2", String.class)).isNull();
  }

  @Test
  void testGetWithCallableReturnsNull() {
    // given
    Callable<String> valueLoader = () -> null;

    // when
    String result = ehCacheAdapter.get("key1", valueLoader);

    // then
    assertThat(result).isNull();
    assertThat(nativeCache.get("key1")).isNull();
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    // Test that adapter handles concurrent access correctly
    Thread t1 = new Thread(() -> {
      for (int i = 0; i < 100; i++) {
        ehCacheAdapter.put("key" + i, "value" + i);
      }
    });

    Thread t2 = new Thread(() -> {
      for (int i = 0; i < 100; i++) {
        ehCacheAdapter.get("key" + i, String.class);
      }
    });

    t1.start();
    t2.start();
    t1.join();
    t2.join();

    // Verify some values were stored
    assertThat(nativeCache.get("key0")).isNotNull();
  }
}
