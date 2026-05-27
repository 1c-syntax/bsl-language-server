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
package com.github._1c_syntax.bsl.languageserver.configuration.oscript;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace-scoped настройки подсистемы OneScript-библиотек:
 * пути к каталогам с библиотеками (где ожидается {@code lib.config} в подкаталогах)
 * и флаг учёта переменной окружения {@code OSCRIPT_LIB_LOCATION}.
 */
@Getter
@Setter
public class OScriptOptions {

  /**
   * Дополнительные корневые каталоги для поиска OneScript-библиотек.
   * Каждый элемент — путь (абсолютный или относительно workspace) к каталогу,
   * в подпапках которого ищется {@code lib.config}.
   */
  @JsonProperty("libRoots")
  private List<String> libRoots = new ArrayList<>();

  /**
   * Если {@code true}, в дополнение к {@link #libRoots} учитываются пути из
   * переменной окружения {@code OSCRIPT_LIB_LOCATION} (по умолчанию выключено,
   * чтобы избежать неожиданного подхвата либ на CI).
   */
  @JsonProperty("useEnvLibLocation")
  private boolean useEnvLibLocation = false;

  /**
   * Если {@code true}, в no-dot completion (включая список после {@code Новый })
   * предлагаются также неявные записи библиотек — необъявленные в {@code lib.config}
   * {@code .os}-файлы, лежащие внутри каталога обнаруженной библиотеки. По
   * умолчанию отключено: потребителю библиотеки не нужно видеть её внутренние
   * сущности в подсказках. Разработчику самой библиотеки удобно включить, чтобы
   * видеть свои internal-классы в completion'е своего же проекта.
   */
  @JsonProperty("showImplicitLibraryEntriesInCompletion")
  private boolean showImplicitLibraryEntriesInCompletion = false;
}
