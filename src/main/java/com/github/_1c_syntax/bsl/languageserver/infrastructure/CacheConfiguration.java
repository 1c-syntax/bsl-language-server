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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

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
   * Основной менеджер кэша, использующий Caffeine для кэширования в памяти.
   * <p>
   * Помечен как {@code @Primary}, поэтому используется для всех кэшей по умолчанию,
   * если не указан явно другой менеджер кэша (например, {@code typoCacheManager} для typoCache).
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
    return Caffeine.newBuilder();
  }

  /**
   * Выделенный менеджер EhCache для typoCache с персистентным хранением на диске.
   * <p>
   * Настроен программно, без использования XML-конфигурации.
   * При закрытии Spring-контекста вызывается метод {@code close()} для корректного завершения работы кэша.
   */
  @Bean(destroyMethod = "close")
  public org.ehcache.CacheManager ehcacheManager(
    @Value("${app.cache.path}") String cacheDirPath
  ) {
    var cacheDir = Path.of(cacheDirPath);
    
    // Configure EhCache cache with disk persistence
    var cacheConfig = CacheConfigurationBuilder
      .newCacheConfigurationBuilder(
        String.class,
        WordStatus.class,
        ResourcePoolsBuilder.newResourcePoolsBuilder()
          .heap(125_000, EntryUnit.ENTRIES)
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
    
    // Wrap the native cache with EhCacheAdapter
    var simpleCacheManager = new SimpleCacheManager();
    simpleCacheManager.setCaches(List.of(
      new EhCacheAdapter<>(nativeCache, "typoCache")
    ));
    simpleCacheManager.afterPropertiesSet();
    
    return simpleCacheManager;
  }
}
