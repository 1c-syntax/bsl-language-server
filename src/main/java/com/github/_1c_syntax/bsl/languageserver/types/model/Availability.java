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
package com.github._1c_syntax.bsl.languageserver.types.model;

/**
 * Контекст исполнения, в котором доступен член платформенного типа
 * (метод/свойство/событие) или системное перечисление.
 * <p>
 * LS-сторонний enum зеркалит {@code com.github._1c_syntax.bsl.context.api.Availability}
 * из библиотеки {@code bsl-context}, чтобы model-слой типов не зависел напрямую
 * от чужого API. Маппинг bsl-context → LS делается в адаптерах
 * ({@code BslContextPlatformTypesProvider}, JSON-fallback).
 */
public enum Availability {
  /** Тонкий клиент. */
  THIN_CLIENT,
  /** Веб-клиент. */
  WEB_CLIENT,
  /** Мобильный клиент. */
  MOBILE_CLIENT,
  /** Сервер. */
  SERVER,
  /** Толстый клиент. */
  THICK_CLIENT,
  /** Внешнее соединение. */
  EXTERNAL_CONNECTION,
  /** Мобильное приложение, клиент. */
  MOBILE_APPLICATION_CLIENT,
  /** Мобильное приложение, сервер. */
  MOBILE_APPLICATION_SERVER,
  /** Мобильный автономный сервер. */
  MOBILE_STANDALONE_SERVER
}
