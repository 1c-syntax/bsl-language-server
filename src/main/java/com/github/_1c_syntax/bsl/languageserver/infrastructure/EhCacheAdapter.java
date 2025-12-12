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

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.util.concurrent.Callable;

/**
 * Адаптер для интеграции нативного EhCache с Spring Cache абстракцией.
 * <p>
 * Оборачивает {@link org.ehcache.Cache} для использования в Spring Cache инфраструктуре,
 * обеспечивая корректное взаимодействие между нативным API EhCache и Spring Cache API.
 * <p>
 * Основные особенности:
 * <ul>
 *   <li>Не допускает хранение null-значений ({@code allowNullValues = false})</li>
 *   <li>Делегирует все операции нативному EhCache</li>
 *   <li>Обеспечивает потокобезопасный доступ через {@link #get(Object, Callable)}</li>
 * </ul>
 *
 * @param <K> тип ключа кэша
 * @param <V> тип значения кэша
 */
public class EhCacheAdapter<K, V> extends AbstractValueAdaptingCache {

  private final org.ehcache.Cache<K, V> nativeCache;
  private final String name;

  /**
   * Создает новый адаптер для нативного EhCache.
   *
   * @param nativeCache нативный кэш EhCache для оборачивания
   * @param name имя кэша для идентификации в Spring Cache
   */
  public EhCacheAdapter(org.ehcache.Cache<K, V> nativeCache, String name) {
    super(false); // не допускаем null-значения
    this.nativeCache = nativeCache;
    this.name = name;
  }

  @Override
  protected Object lookup(Object key) {
    @SuppressWarnings("unchecked")
    var typedKey = (K) key;
    return nativeCache.get(typedKey);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Object getNativeCache() {
    return nativeCache;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(Object key, Callable<T> valueLoader) {
    var typedKey = (K) key;
    var value = nativeCache.get(typedKey);

    if (value != null) { // если нет в кеше, загружаем из valueLoader
      return (T) value;
    }

    try {
      T newValue = valueLoader.call();
      if (newValue != null) {
        nativeCache.put(typedKey, (V) newValue);
      }
      return newValue;
    } catch (Exception e) {
      throw new Cache.ValueRetrievalException(key, valueLoader, e);
    }
  }

  @Override
  public void put(Object key, @Nullable Object value) {
    @SuppressWarnings("unchecked")
    var typedKey = (K) key;
    @SuppressWarnings("unchecked")
    var typedValue = (V) value;
    nativeCache.put(typedKey, typedValue);
  }

  @Override
  public void evict(Object key) {
    @SuppressWarnings("unchecked")
    var typedKey = (K) key;
    nativeCache.remove(typedKey);
  }

  @Override
  public void clear() {
    nativeCache.clear();
  }
}
