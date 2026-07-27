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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Срабатывание диагностики на конструирование типа, которого нет в целевой
 * версии платформы. Версия появления типа приходит с его главной страницы в
 * синтакс-помощнике через bsl-context — JSON-fallback метаданных типов не
 * содержит, поэтому тест запускается только с реальным СП.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С: версии появления типов берутся из bsl-context")
class UnavailableMemberCallPlatformTypeTest
  extends AbstractDiagnosticTest<UnavailableMemberCallDiagnostic> {

  UnavailableMemberCallPlatformTypeTest() {
    super(UnavailableMemberCallDiagnostic.class);
  }

  @Test
  void typeUnavailableForOlderTarget() {
    // ЧтениеJSON появился в 8.3.6 — для целевой 8.3.5 конструктор недоступен.
    configuration.getV8PlatformOptions().setTargetVersion("8.3.5");
    try {
      List<Diagnostic> diagnostics = getDiagnostics("UnavailableMemberCallPlatformType");
      assertThat(diagnostics).hasSize(1);
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void typeAvailableForNewerTarget() {
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      List<Diagnostic> diagnostics = getDiagnostics("UnavailableMemberCallPlatformType");
      assertThat(diagnostics).isEmpty();
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }
}
