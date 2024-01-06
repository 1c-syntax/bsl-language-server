/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.support.CompatibilityMode;

public enum DiagnosticCompatibilityMode {
  UNDEFINED(0, 0),
  COMPATIBILITY_MODE_8_1_0(1, 0),
  COMPATIBILITY_MODE_8_2_13(2, 13),
  COMPATIBILITY_MODE_8_2_16(2, 16),
  COMPATIBILITY_MODE_8_3_1(3, 1),
  COMPATIBILITY_MODE_8_3_2(3, 2),
  COMPATIBILITY_MODE_8_3_3(3, 3),
  COMPATIBILITY_MODE_8_3_4(3, 4),
  COMPATIBILITY_MODE_8_3_5(3, 5),
  COMPATIBILITY_MODE_8_3_6(3, 6),
  COMPATIBILITY_MODE_8_3_7(3, 7),
  COMPATIBILITY_MODE_8_3_8(3, 8),
  COMPATIBILITY_MODE_8_3_9(3, 9),
  COMPATIBILITY_MODE_8_3_10(3, 10),
  COMPATIBILITY_MODE_8_3_11(3, 11),
  COMPATIBILITY_MODE_8_3_12(3, 12),
  COMPATIBILITY_MODE_8_3_13(3, 13),
  COMPATIBILITY_MODE_8_3_14(3, 14),
  COMPATIBILITY_MODE_8_3_15(3, 15),
  COMPATIBILITY_MODE_8_3_16(3, 16),
  COMPATIBILITY_MODE_8_3_17(3, 17),
  COMPATIBILITY_MODE_8_3_18(3, 18),
  COMPATIBILITY_MODE_8_3_19(3, 19),
  COMPATIBILITY_MODE_8_3_20(3, 20),
  COMPATIBILITY_MODE_8_3_21(3, 21);

  private final CompatibilityMode compatibilityMode;

  DiagnosticCompatibilityMode(int minor, int version) {
    this.compatibilityMode = new CompatibilityMode(minor, version);
  }

  public CompatibilityMode getCompatibilityMode() {
    return compatibilityMode;
  }
}
