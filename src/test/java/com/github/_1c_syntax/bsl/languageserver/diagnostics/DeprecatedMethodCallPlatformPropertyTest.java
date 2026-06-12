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
 * Срабатывание диагностики на доступ к устаревшему свойству платформенного
 * типа (вне глобального контекста). Версия устаревания приходит из HBK
 * через bsl-context — JSON-fallback свойств типов не содержит, поэтому тест
 * запускается только с реальным синтакс-помощником.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С: версии устаревания свойств типов берутся из bsl-context")
class DeprecatedMethodCallPlatformPropertyTest
  extends AbstractDiagnosticTest<DeprecatedMethodCallDiagnostic> {

  DeprecatedMethodCallPlatformPropertyTest() {
    super(DeprecatedMethodCallDiagnostic.class);
  }

  @Test
  void deprecatedPropertyOnSpreadsheetDocument() {
    // ТабличныйДокумент.ИмяПараметровПечати устарело с 8.2; target >= 8.2
    // должен подсвечивать оба обращения (присваивание + чтение).
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallPlatformProperty");
      assertThat(diagnostics).hasSize(2);
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void deprecatedPropertyNotReportedForTargetBelowThreshold() {
    // Негативная граница: для target ниже семейства устаревания (8.2.x)
    // диагностика молчит. 8.1.99 — последний патч 8.1, гарантированно ниже
    // нормализованного «устарел с 8.2» → 8.2.99 (см. PlatformMemberVersions).
    configuration.getV8PlatformOptions().setTargetVersion("8.1.99");
    try {
      List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallPlatformProperty");
      assertThat(diagnostics).isEmpty();
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }
}
