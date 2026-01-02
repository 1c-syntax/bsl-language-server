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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import com.github._1c_syntax.bsl.languageserver.diagnostics.typo.WordStatus;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Spring-конфигурация кэширования.
 * <p>
 * Для typoCache используется EhCache с персистентным хранилищем на диске.
 * Для остальных кэшей (например, code lens) используется Caffeine с хранением в памяти.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {
  private static final String TYPO_CACHE_NAME = "typoCache";
  private static final int MAX_CACHE_INSTANCES = 10;
  private static final long HEAP_ENTRIES_COUNT = 125_000;
  private static final long DISK_SIZE_MB = 50;

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
   * <p>
   * Кэш размещается в каталоге пользователя, что позволяет избежать захламления git-репозиториев.
   * Путь можно переопределить через свойство {@code app.cache.fullPath}.
   * <p>
   * При запуске нескольких экземпляров в одной директории автоматически используются
   * отдельные каталоги кэша с суффиксами @1, @2 и т.д. Если все каталоги заблокированы
   * (более 10 экземпляров), автоматически используется кэш в памяти без персистентности.
   */
  @Bean(destroyMethod = "close")
  public org.ehcache.CacheManager ehcacheManager(
    CachePathProvider cachePathProvider,
    @Value("${app.cache.basePath}") String basePath,
    @Value("${app.cache.fullPath}") String fullPath
  ) {
    // Try to create cache manager with instance-numbered directories
    // if the primary directory is locked
    return createEhcacheManagerWithRetry(cachePathProvider, basePath, fullPath);
  }

  /**
   * Создаёт менеджер EhCache, пробуя пути с разными номерами экземпляров при блокировке.
   * <p>
   * Пытается использовать основной путь (без суффикса), затем пути с суффиксами @1, @2 и т.д.
   * до максимального количества попыток.
   * <p>
   * Если все попытки исчерпаны (все каталоги заблокированы), автоматически создаётся
   * кэш-менеджер с хранением только в памяти (без персистентности на диске).
   *
   * @param cachePathProvider провайдер путей к кэшу
   * @param basePath базовый путь
   * @param fullPath полный путь (если задан)
   * @return менеджер EhCache
   */
  private static org.ehcache.CacheManager createEhcacheManagerWithRetry(
    CachePathProvider cachePathProvider,
    String basePath,
    String fullPath
  ) {
    for (var instanceNumber = 0; instanceNumber < MAX_CACHE_INSTANCES; instanceNumber++) {
      try {
        var cacheDir = cachePathProvider.getCachePath(basePath, fullPath, instanceNumber);
        return createEhcacheManager(cacheDir);
      } catch (org.ehcache.StateTransitionException e) {
        // This exception indicates the directory is locked by another process
        // Continue to try next instance number
      }
    }
    
    // If we exhausted all attempts, fall back to in-memory cache
    return createInMemoryEhcacheManager();
  }

  /**
   * Создаёт менеджер EhCache для указанного каталога.
   *
   * @param cacheDir каталог для персистентного хранилища
   * @return менеджер EhCache
   */
  private static org.ehcache.CacheManager createEhcacheManager(java.nio.file.Path cacheDir) {
    // Build resource pools with disk persistence
    var resourcePools = ResourcePoolsBuilder.newResourcePoolsBuilder()
      .heap(HEAP_ENTRIES_COUNT, EntryUnit.ENTRIES)
      .disk(DISK_SIZE_MB, MemoryUnit.MB, true);
    
    var cacheConfig = createTypoCacheConfig(resourcePools);

    // Build native EhCache manager with persistence
    return CacheManagerBuilder.newCacheManagerBuilder()
      .with(CacheManagerBuilder.persistence(cacheDir.toFile()))
      .withCache(TYPO_CACHE_NAME, cacheConfig)
      .build(true);
  }

  /**
   * Создаёт менеджер EhCache с хранением только в памяти (без персистентности).
   * <p>
   * Используется как fallback, когда все доступные каталоги кэша заблокированы.
   * Кэш будет очищен при перезапуске приложения.
   *
   * @return менеджер EhCache с in-memory хранилищем
   */
  private static org.ehcache.CacheManager createInMemoryEhcacheManager() {
    // Build resource pools with heap-only storage
    var resourcePools = ResourcePoolsBuilder.newResourcePoolsBuilder()
      .heap(HEAP_ENTRIES_COUNT, EntryUnit.ENTRIES);
    
    var cacheConfig = createTypoCacheConfig(resourcePools);

    // Build native EhCache manager without persistence
    return CacheManagerBuilder.newCacheManagerBuilder()
      .withCache(TYPO_CACHE_NAME, cacheConfig)
      .build(true);
  }

  /**
   * Создаёт конфигурацию кэша для typoCache.
   *
   * @param resourcePoolsBuilder построитель пулов ресурсов (heap, disk и т.д.)
   * @return конфигурация кэша
   */
  private static org.ehcache.config.CacheConfiguration<String, WordStatus> createTypoCacheConfig(
    ResourcePoolsBuilder resourcePoolsBuilder
  ) {
    return CacheConfigurationBuilder
      .newCacheConfigurationBuilder(
        String.class,
        WordStatus.class,
        resourcePoolsBuilder
      )
      .build();
  }

  @Bean
  public CacheManager typoCacheManager(org.ehcache.CacheManager ehcacheManager) {
    var nativeCache = ehcacheManager.getCache(TYPO_CACHE_NAME, String.class, WordStatus.class);
    
    // Wrap the native cache with EhCacheAdapter
    var simpleCacheManager = new SimpleCacheManager();
    simpleCacheManager.setCaches(List.of(
      new EhCacheAdapter<>(nativeCache, TYPO_CACHE_NAME)
    ));
    simpleCacheManager.afterPropertiesSet();
    
    return simpleCacheManager;
  }
}
