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

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class UnavailableMemberCallDiagnosticTest extends AbstractDiagnosticTest<UnavailableMemberCallDiagnostic> {
  UnavailableMemberCallDiagnosticTest() {
    super(UnavailableMemberCallDiagnostic.class);
  }

  @Test
  void unavailableForOlderTarget() {

    // given — целевая платформа 8.3.10 ниже версии появления ВопросАсинх (8.3.18).
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      // when
      List<Diagnostic> diagnostics = getDiagnostics();

      // then
      assertThat(diagnostics).hasSize(1);
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void availableForTargetAtSinceVersion() {

    // given — целевая платформа равна версии появления члена: член доступен.
    configuration.getV8PlatformOptions().setTargetVersion("8.3.18");
    try {
      List<Diagnostic> diagnostics = getDiagnostics();
      assertThat(diagnostics).isEmpty();
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void availableForNewerTarget() {

    // given — целевая платформа выше версии появления члена: член доступен.
    configuration.getV8PlatformOptions().setTargetVersion("8.3.24");
    try {
      List<Diagnostic> diagnostics = getDiagnostics();
      assertThat(diagnostics).isEmpty();
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }
}
