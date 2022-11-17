/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Режим отправки сообщений об ошибках разработчикам.
 */
public enum SendErrorsMode {
  /**
   * Отправлять всегда.
   */
  @JsonProperty("send")
  SEND,

  /**
   * Отправить один раз, затем спросить.
   */
  @JsonIgnore
  SEND_ONCE,

  /**
   * Никогда не отправлять.
   */
  @JsonProperty("never")
  NEVER,

  /**
   * Спросить разрешения.
   */
  @JsonProperty("ask")
  ASK;

  /**
   * Режим отправки ошибок по умолчанию.
   */
  public static final SendErrorsMode DEFAULT = ASK;
}
