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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.google.common.collect.Sets;

import java.util.Set;

public enum PreprocessorConstraints {
  SERVER,
  CLIENT,
  THIN_CLIENT,
  MANAGED_THICK_CLIENT,
  ORDINARY_THICK_CLIENT,
  WEB_CLIENT,
  MOBILE_CLIENT,
  MOBILE_APP_CLIENT,
  MOBILE_STANDALONE_SERVER,
  MOBILE_APP_SERVER,
  EXTERNAL_CONNECTION,

  NON_STANDARD;

  public static final Set<PreprocessorConstraints> CLIENT_CONSTRAINTS = Sets.immutableEnumSet(
    ORDINARY_THICK_CLIENT,
    MANAGED_THICK_CLIENT,
    MOBILE_CLIENT,
    THIN_CLIENT,
    WEB_CLIENT);

  public static final Set<PreprocessorConstraints> DEFAULT_CONSTRAINTS = Sets.immutableEnumSet(
    SERVER,
    THIN_CLIENT,
    MANAGED_THICK_CLIENT,
    ORDINARY_THICK_CLIENT,
    WEB_CLIENT,
    MOBILE_CLIENT,
    MOBILE_APP_CLIENT,
    MOBILE_STANDALONE_SERVER,
    MOBILE_APP_SERVER,
    EXTERNAL_CONNECTION);
}
