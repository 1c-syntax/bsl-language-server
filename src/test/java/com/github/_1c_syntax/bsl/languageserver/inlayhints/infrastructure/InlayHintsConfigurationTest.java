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
package com.github._1c_syntax.bsl.languageserver.inlayhints.infrastructure;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты для {@link InlayHintsConfiguration#supplierIsEnabled(List, Map)}.
 */
class InlayHintsConfigurationTest {

  private static final List<String> METHOD_CALL_KEYS = List.of("methodCall", "sourceDefinedMethodCall");

  @Test
  void enabledByDefaultWhenNoKeyPresent() {
    assertThat(InlayHintsConfiguration.supplierIsEnabled(METHOD_CALL_KEYS, Map.of()))
      .isTrue();
  }

  @Test
  void unifiedKeySetToFalseDisablesSupplier() {
    // methodCall: false — основной кейс: гасит оба method-call сапплаера.
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(METHOD_CALL_KEYS, parameters))
      .isFalse();
  }

  @Test
  void unifiedKeyWithNestedSettingsKeepsSupplierEnabled() {
    // methodCall: { ... } — объект-значение означает «включён» (внутри лишь под-настройки).
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forRight(Map.of("showDefaultValues", false))
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(METHOD_CALL_KEYS, parameters))
      .isTrue();
  }

  @Test
  void legacyKeySetToFalseDisablesSupplierWhenUnifiedKeyAbsent() {
    // Совместимость: sourceDefinedMethodCall: false без methodCall — тоже выключает.
    var parameters = Map.of(
      "sourceDefinedMethodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(METHOD_CALL_KEYS, parameters))
      .isFalse();
  }

  @Test
  void unifiedKeyTakesPrecedenceOverLegacyKey() {
    // methodCall присутствует — решает он, legacy-ключ игнорируется.
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forLeft(true),
      "sourceDefinedMethodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(METHOD_CALL_KEYS, parameters))
      .isTrue();
  }

  @Test
  void ownIdKeyDisablesSingleKeySupplier() {
    // Сапплаер с единственным ключом (= getId()) выключается этим ключом.
    var parameters = Map.of(
      "cognitiveComplexity", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(List.of("cognitiveComplexity"), parameters))
      .isFalse();
  }
}
