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
 * Юнит-тесты для {@link InlayHintsConfiguration#supplierIsEnabled(Iterable, Map)}.
 */
class InlayHintsConfigurationTest {

  // Ключи в порядке приоритета, как их отдаёт AbstractMethodCallInlayHintSupplier#getConfigurationKeys().
  private static final List<String> SOURCE_DEFINED_KEYS = List.of("methodCall", "sourceDefinedMethodCall");
  private static final List<String> PLATFORM_KEYS = List.of("methodCall", "platformMethodCall");

  @Test
  void enabledByDefaultWhenNoKeyPresent() {
    assertThat(InlayHintsConfiguration.supplierIsEnabled(SOURCE_DEFINED_KEYS, Map.of()))
      .isTrue();
  }

  @Test
  void unifiedKeySetToFalseDisablesBothSuppliers() {
    // methodCall: false — основной кейс: гасит оба method-call сапплаера сразу.
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(SOURCE_DEFINED_KEYS, parameters)).isFalse();
    assertThat(InlayHintsConfiguration.supplierIsEnabled(PLATFORM_KEYS, parameters)).isFalse();
  }

  @Test
  void unifiedKeyWithNestedSettingsKeepsSupplierEnabled() {
    // methodCall: { ... } — объект-значение означает «включён» (внутри лишь под-настройки).
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forRight(Map.of("showDefaultValues", false))
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(SOURCE_DEFINED_KEYS, parameters))
      .isTrue();
  }

  @Test
  void legacySourceDefinedKeyDisablesOnlySourceDefinedSupplier() {
    // sourceDefinedMethodCall: false без methodCall — гасит только source-defined,
    // платформенный сапплаер не затрагивает (его legacy-ключ другой).
    var parameters = Map.of(
      "sourceDefinedMethodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(SOURCE_DEFINED_KEYS, parameters)).isFalse();
    assertThat(InlayHintsConfiguration.supplierIsEnabled(PLATFORM_KEYS, parameters)).isTrue();
  }

  @Test
  void legacyPlatformKeyDisablesOnlyPlatformSupplier() {
    // platformMethodCall: false без methodCall — гасит только платформенный,
    // source-defined не затрагивает (регрессия на обратную совместимость).
    var parameters = Map.of(
      "platformMethodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(PLATFORM_KEYS, parameters)).isFalse();
    assertThat(InlayHintsConfiguration.supplierIsEnabled(SOURCE_DEFINED_KEYS, parameters)).isTrue();
  }

  @Test
  void unifiedKeyTakesPrecedenceOverLegacyKey() {
    // methodCall присутствует — решает он, legacy-ключ игнорируется.
    var parameters = Map.of(
      "methodCall", Either.<Boolean, Map<String, Object>>forLeft(true),
      "platformMethodCall", Either.<Boolean, Map<String, Object>>forLeft(false)
    );

    assertThat(InlayHintsConfiguration.supplierIsEnabled(PLATFORM_KEYS, parameters))
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
