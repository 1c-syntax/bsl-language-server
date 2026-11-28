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
package com.github._1c_syntax.bsl.languageserver.configuration;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierParametersTest {

  private static final Map<String, Either<Boolean, Map<String, Object>>> PARAMETERS = Map.of(
    "disabled", Either.forLeft(false),
    "enabled", Either.forLeft(true),
    "configured", Either.forRight(Map.of("threshold", 10, "flag", true))
  );

  @Test
  void supplierIsEnabledUnlessExplicitlyDisabled() {
    assertThat(SupplierParameters.isEnabled(PARAMETERS, "disabled")).isFalse();
    assertThat(SupplierParameters.isEnabled(PARAMETERS, "enabled")).isTrue();
    assertThat(SupplierParameters.isEnabled(PARAMETERS, "configured")).isTrue();
    assertThat(SupplierParameters.isEnabled(PARAMETERS, "absent")).isTrue();
  }

  @Test
  void settingIsReadOnlyFromSettingsObject() {
    assertThat(SupplierParameters.setting(PARAMETERS, "configured", "threshold")).contains(10);
    assertThat(SupplierParameters.setting(PARAMETERS, "configured", "flag")).contains(true);
    assertThat(SupplierParameters.setting(PARAMETERS, "configured", "missing")).isEmpty();
    assertThat(SupplierParameters.setting(PARAMETERS, "enabled", "threshold")).isEmpty();
    assertThat(SupplierParameters.setting(PARAMETERS, "absent", "threshold")).isEmpty();
  }
}
