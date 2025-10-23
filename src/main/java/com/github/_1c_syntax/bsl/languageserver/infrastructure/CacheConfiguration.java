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
import com.github._1c_syntax.bsl.languageserver.diagnostics.typo.WordStatus;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Paths;

/**
 * Spring-конфигурация кэширования.
 * <p>
 * Для typoCache используется EhCache с персистентным хранилищем на диске.
 * Для остальных кэшей (например, code lens) используется Caffeine с хранением в памяти.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

  /**
   * Primary cache manager using Caffeine for in-memory caching.
   * Used for all caches except typoCache.
   */
  @Bean
  @Primary
  public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
    var caffeineCacheManager = new CaffeineCacheManager();
    caffeineCacheManager.setCaffeine(caffeine);
    return caffeineCacheManager;
  }

  @Bean
  public Caffeine<Object, Object> caffeineConfig() {
    return Caffeine.newBuilder()
      .maximumSize(10_000);
  }

  /**
   * Dedicated EhCache manager for typoCache with persistent disk storage.
   * Configured programmatically without XML.
   */
  @Bean(destroyMethod = "close")
  public org.ehcache.CacheManager ehcacheManager() {
    // Cache directory in current working directory  
    var cacheDir = Paths.get(System.getProperty("user.dir"), ".bsl-ls-cache");
    
    // Configure EhCache cache with disk persistence
    var cacheConfig = CacheConfigurationBuilder
      .newCacheConfigurationBuilder(
        String.class,
        WordStatus.class,
        ResourcePoolsBuilder.newResourcePoolsBuilder()
          .heap(1000, EntryUnit.ENTRIES)
          .disk(50, MemoryUnit.MB, true)
      )
      .build();

    // Build native EhCache manager with persistence
    return CacheManagerBuilder.newCacheManagerBuilder()
      .with(CacheManagerBuilder.persistence(cacheDir.toFile()))
      .withCache("typoCache", cacheConfig)
      .build(true);
  }

  @Bean
  public CacheManager typoCacheManager(org.ehcache.CacheManager ehcacheManager) {
    var nativeCache = ehcacheManager.getCache("typoCache", String.class, WordStatus.class);
    
    // Wrap the native cache with a custom Spring CacheManager
    var simpleCacheManager = new org.springframework.cache.support.SimpleCacheManager();
    simpleCacheManager.setCaches(java.util.List.of(
      new org.springframework.cache.support.AbstractValueAdaptingCache(false) {
        @Override
        protected Object lookup(Object key) {
          return nativeCache.get((String) key);
        }

        @Override
        public String getName() {
          return "typoCache";
        }

        @Override
        public Object getNativeCache() {
          return nativeCache;
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
          var value = nativeCache.get((String) key);
          if (value != null) {
            return (T) value;
          }
          try {
            T newValue = valueLoader.call();
            if (newValue != null) {
              nativeCache.put((String) key, (WordStatus) newValue);
            }
            return newValue;
          } catch (Exception e) {
            throw new org.springframework.cache.Cache.ValueRetrievalException(key, valueLoader, e);
          }
        }

        @Override
        public void put(Object key, Object value) {
          nativeCache.put((String) key, (WordStatus) value);
        }

        @Override
        public void evict(Object key) {
          nativeCache.remove((String) key);
        }

        @Override
        public void clear() {
          nativeCache.clear();
        }
      }
    ));
    simpleCacheManager.afterPropertiesSet();
    
    return simpleCacheManager;
  }
}
