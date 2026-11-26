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

import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.context.annotation.ScopedProxyMode;

import java.util.List;
import java.util.Optional;

/**
 * Spring-конфигурация кэширования.
 * <p>
 * EhCache-менеджер ({@code ehcacheManager}) — общий и cache-agnostic: каждый модуль регистрирует
 * свой персистентный кэш через {@link EhcacheRegistrar}, а {@code ehcacheCacheManager} оборачивает
 * их в Spring-{@link CacheManager}. Для остальных кэшей (например, code lens) используется Caffeine
 * с хранением в памяти.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {
  private static final int MAX_CACHE_INSTANCES = 10;
  /**
   * Потолок кэша резолва общих модулей. Кэшируются и промахи, поэтому размер считается по словарю
   * имён-идентификаторов исходного кода (на полной типовой конфигурации SSL 3.2 намерено ~32 000
   * уникальных), а не по числу модулей; берём с запасом. Кэш растёт лениво и ограничен по памяти.
   */
  private static final int COMMON_MODULE_CACHE_SIZE = 1 << 17; // 131072
  private static final long HEAP_ENTRIES_COUNT = 125_000;
  private static final long DISK_SIZE_MB = 50;

  /**
   * Основной менеджер кэша, использующий Caffeine для кэширования в памяти.
   * <p>
   * Помечен как {@code @Primary}, поэтому используется для всех кэшей по умолчанию,
   * если не указан явно другой менеджер кэша (например, {@code ehcacheCacheManager} для typoCache).
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
   * Ограниченный кэш резолва общего модуля по имени для {@code ServerContext.findCommonModule}.
   * <p>
   * Workspace-scoped: один экземпляр на воркспейс (резолв зависит от конфигурации воркспейса,
   * поэтому общий singleton-кэш смешивал бы воркспейсы). Ограничен по размеру, т.к. кэшируются
   * и промахи, а ключи — имена-идентификаторы исходного кода.
   */
  @Bean
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public Cache<String, Optional<CommonModule>> commonModuleCache() {
    return Caffeine.newBuilder()
      .maximumSize(COMMON_MODULE_CACHE_SIZE)
      .build();
  }

  /**
   * Общий менеджер EhCache с персистентным хранением на диске.
   * <p>
   * Cache-agnostic: каждый кэш объявляется отдельным {@link EhcacheRegistrar}. Настроен программно,
   * без XML; при закрытии Spring-контекста вызывается {@code close()}. Кэш размещается в каталоге
   * пользователя (путь переопределяется свойством {@code app.cache.fullPath}). При запуске
   * нескольких экземпляров используются каталоги с суффиксами @1, @2 и т.д.; если все заблокированы
   * (более 10 экземпляров), используется кэш в памяти без персистентности.
   */
  @Bean(destroyMethod = "close")
  public org.ehcache.CacheManager ehcacheManager(
    CachePathProvider cachePathProvider,
    @Value("${app.cache.basePath}") String basePath,
    @Value("${app.cache.fullPath}") String fullPath,
    List<EhcacheRegistrar> registrars
  ) {
    return createEhcacheManagerWithRetry(cachePathProvider, basePath, fullPath, registrars);
  }

  /**
   * Spring-{@link CacheManager} поверх кэшей общего EhCache-менеджера.
   * <p>
   * На него ссылаются потребители через {@code @Cacheable(cacheManager = "ehcacheCacheManager")},
   * когда нужен персистентный кэш, а не {@code @Primary} Caffeine.
   */
  @Bean
  public CacheManager ehcacheCacheManager(
    org.ehcache.CacheManager ehcacheManager,
    List<EhcacheRegistrar> registrars
  ) {
    var simpleCacheManager = new SimpleCacheManager();
    simpleCacheManager.setCaches(
      registrars.stream()
        .map(registrar -> toSpringCache(ehcacheManager, registrar))
        .toList()
    );
    simpleCacheManager.afterPropertiesSet();
    return simpleCacheManager;
  }

  private static org.springframework.cache.Cache toSpringCache(
    org.ehcache.CacheManager ehcacheManager,
    EhcacheRegistrar registrar
  ) {
    var nativeCache = ehcacheManager.getCache(
      registrar.cacheName(), registrar.keyType(), registrar.valueType());
    return new EhCacheAdapter<>(nativeCache, registrar.cacheName());
  }

  /**
   * Создаёт общий менеджер EhCache, пробуя каталоги с разными номерами экземпляров при блокировке.
   * <p>
   * Пытается использовать основной путь (без суффикса), затем пути с суффиксами @1, @2 и т.д. до
   * максимального количества попыток. Если все каталоги заблокированы, создаётся менеджер с
   * хранением только в памяти (без персистентности на диске).
   *
   * @param cachePathProvider провайдер путей к кэшу
   * @param basePath          базовый путь
   * @param fullPath          полный путь (если задан)
   * @param registrars        декларации кэшей, которые нужно зарегистрировать в менеджере
   * @return менеджер EhCache
   */
  private static org.ehcache.CacheManager createEhcacheManagerWithRetry(
    CachePathProvider cachePathProvider,
    String basePath,
    String fullPath,
    List<EhcacheRegistrar> registrars
  ) {
    for (var instanceNumber = 0; instanceNumber < MAX_CACHE_INSTANCES; instanceNumber++) {
      try {
        var cacheDir = cachePathProvider.getCachePath(basePath, fullPath, instanceNumber);
        return createPersistentEhcacheManager(cacheDir, registrars);
      } catch (org.ehcache.StateTransitionException e) {
        // The directory is locked by another process, try the next instance number.
      }
    }

    return createInMemoryEhcacheManager(registrars);
  }

  private static org.ehcache.CacheManager createPersistentEhcacheManager(
    java.nio.file.Path cacheDir,
    List<EhcacheRegistrar> registrars
  ) {
    var resourcePools = ResourcePoolsBuilder.newResourcePoolsBuilder()
      .heap(HEAP_ENTRIES_COUNT, EntryUnit.ENTRIES)
      .disk(DISK_SIZE_MB, MemoryUnit.MB, true);

    var builder = CacheManagerBuilder.newCacheManagerBuilder()
      .with(CacheManagerBuilder.persistence(cacheDir.toFile()));
    for (var registrar : registrars) {
      builder = builder.withCache(registrar.cacheName(), registrar.configuration(resourcePools));
    }
    return builder.build(true);
  }

  private static org.ehcache.CacheManager createInMemoryEhcacheManager(List<EhcacheRegistrar> registrars) {
    var resourcePools = ResourcePoolsBuilder.newResourcePoolsBuilder()
      .heap(HEAP_ENTRIES_COUNT, EntryUnit.ENTRIES);

    var builder = CacheManagerBuilder.newCacheManagerBuilder();
    for (var registrar : registrars) {
      builder = builder.withCache(registrar.cacheName(), registrar.configuration(resourcePools));
    }
    return builder.build(true);
  }
}
