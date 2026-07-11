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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Узел конфигурации или расширения в дереве конфигурации: содержит объекты метаданных
 * верхнего уровня соответствующего контейнера.
 *
 * @param name       Имя конфигурации или расширения.
 * @param synonym    Синоним конфигурации или расширения (пустая строка, если синоним не задан).
 * @param kind       Вид контейнера: {@link #KIND_CONFIGURATION} или {@link #KIND_EXTENSION}.
 * @param purpose    Назначение расширения (например, {@code CUSTOMIZATION}, {@code ADD_ON},
 *                   {@code PATCH}). {@code null} для основной конфигурации.
 * @param namePrefix Префикс имён объектов расширения. {@code null} для основной конфигурации.
 * @param objects    Объекты метаданных верхнего уровня, принадлежащие контейнеру.
 */
public record MdClassNode(
  String name,
  String synonym,
  String kind,
  @Nullable String purpose,
  @Nullable String namePrefix,
  List<MetadataObjectNode> objects
) {

  /**
   * Значение {@link #kind} для основной конфигурации.
   */
  public static final String KIND_CONFIGURATION = "CONFIGURATION";

  /**
   * Значение {@link #kind} для расширения конфигурации.
   */
  public static final String KIND_EXTENSION = "EXTENSION";
}
