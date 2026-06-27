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

import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.ResourcePoolsBuilder;

/**
 * Декларация кэша EhCache, который модуль регистрирует в общем менеджере {@code ehcacheManager}.
 * <p>
 * Сам менеджер, его персистентность и retry по каталогам принадлежат инфраструктуре; модуль лишь
 * объявляет имя кэша, типы ключа/значения и конфигурацию поверх переданных пулов ресурсов (heap
 * либо heap+disk — в зависимости от доступности персистентного хранилища). Бин-реализация этого
 * интерфейса автоматически подхватывается при сборке менеджера.
 */
public interface EhcacheRegistrar {

  /**
   * @return имя (alias) кэша
   */
  String cacheName();

  /**
   * @return тип ключа кэша
   */
  Class<?> keyType();

  /**
   * @return тип значения кэша
   */
  Class<?> valueType();

  /**
   * @param resourcePools пулы ресурсов, согласованные с режимом менеджера (heap или heap+disk)
   * @return конфигурация кэша
   */
  CacheConfiguration<?, ?> configuration(ResourcePoolsBuilder resourcePools);
}
