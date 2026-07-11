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
package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Узел конфигурации или расширения в дереве конфигурации: содержит объекты метаданных
 * верхнего уровня соответствующего контейнера.
 */
@Value
public class MdClassNode {

  /**
   * Значение {@link #kind} для основной конфигурации.
   */
  public static final String KIND_CONFIGURATION = "CONFIGURATION";

  /**
   * Значение {@link #kind} для расширения конфигурации.
   */
  public static final String KIND_EXTENSION = "EXTENSION";

  /**
   * Имя конфигурации или расширения.
   */
  String name;

  /**
   * Синоним конфигурации или расширения (пустая строка, если синоним не задан).
   */
  String synonym;

  /**
   * Вид контейнера: {@link #KIND_CONFIGURATION} или {@link #KIND_EXTENSION}.
   */
  String kind;

  /**
   * Назначение расширения (например, <code>CUSTOMIZATION</code>, <code>ADD_ON</code>,
   * <code>PATCH</code>). {@code null} для основной конфигурации.
   */
  @Nullable
  String purpose;

  /**
   * Префикс имён объектов расширения. {@code null} для основной конфигурации.
   */
  @Nullable
  String namePrefix;

  /**
   * Объекты метаданных верхнего уровня, принадлежащие контейнеру.
   */
  List<MetadataObjectNode> objects;
}
