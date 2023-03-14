/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.configuration.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;

/**
 * Режим для учета настроек правил.
 * <p>
 * См. {@link DiagnosticsOptions#getParameters()}
 */
public enum Mode {

  /**
   * Все диагностики считаются выключенными.
   */
  OFF,

  /**
   * Все диагностики, включенные по умолчанию ({@link DiagnosticMetadata#activatedByDefault()}, считаются включенными.
   * Остальные - в соответствии с конфигурацией настроек правил.
   */
  ON,

  /**
   * Все диагностики, кроме указанных в конфигурации настроек правил, считаются включенными.
   */
  EXCEPT,

  /**
   * Только диагностики, указанные в конфигурации настроек правил, считаются включенными.
   */
  ONLY,

  /**
   * Все диагностики считаются включенными.
   */
  ALL
}
