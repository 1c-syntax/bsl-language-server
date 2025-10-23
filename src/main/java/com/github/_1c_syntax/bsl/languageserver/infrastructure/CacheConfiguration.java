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

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.cache.Caching;
import java.util.List;

/**
 * Spring-конфигурация кэширования с композитным кэшем (Caffeine + EhCache).
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

  /**
   * Composite cache manager using Caffeine for fast in-memory access
   * and EhCache for persistent disk-backed storage.
   */
  @Bean
  @Primary
  public CacheManager cacheManager(
    CacheManager caffeineCacheManager,
    CacheManager ehCacheCacheManager
  ) {
    var compositeCacheManager = new CompositeCacheManager(
      caffeineCacheManager,
      ehCacheCacheManager
    );
    compositeCacheManager.setFallbackToNoOpCache(false);
    return compositeCacheManager;
  }

  @Bean
  public CacheManager caffeineCacheManager(Caffeine<Object, Object> caffeine) {
    var caffeineCacheManager = new CaffeineCacheManager();
    caffeineCacheManager.setCaffeine(caffeine);
    return caffeineCacheManager;
  }

  @Bean
  public Caffeine<Object, Object> caffeineConfig() {
    return Caffeine.newBuilder()
      .maximumSize(10_000);
  }

  @Bean
  public CacheManager ehCacheCacheManager() {
    try {
      // Use EhCache with configuration from ehcache.xml in classpath
      var cachingProvider = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
      var jsr107Manager = cachingProvider.getCacheManager(
        getClass().getResource("/ehcache.xml").toURI(),
        getClass().getClassLoader()
      );
      
      return new JCacheCacheManager(jsr107Manager);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize EhCache", e);
    }
  }
}
