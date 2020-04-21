/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.configuration.diagnostics;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Режим пропуска подсчета диагностик в зависимости от режима поддержки модуля
 */
public enum SkipSupport {
  /**
   * Пропуск файлов на поддержке без возможности изменения.
   */
  @JsonProperty("withSupportLocked")
  WITH_SUPPORT_LOCKED,

  /**
   * Пропуск файлов на поддержке без возможности изменения и с возможностью изменения с сохранением поддержки.
   */
  @JsonProperty("withSupport")
  WITH_SUPPORT,

  /**
   * Никогда не пропускать файлы.
   */
  @JsonProperty("never")
  NEVER
}
