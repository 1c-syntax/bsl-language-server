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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemberAccessUnavailableOneScriptTest extends AbstractDiagnosticTest<MemberAccessUnavailableDiagnostic> {
  MemberAccessUnavailableOneScriptTest() {
    super(MemberAccessUnavailableDiagnostic.class);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Windows | Linux | true", "Linux | Windows | true", "Windows | Windows | false",
    "Windows И MONO | Windows | false", "Windows | Windows И MONO | true",
    "Linux И НЕ MONO | Linux И MONO | true", "MacOS | MacOS | false",
    "Windows И Linux | Linux | false", "CustomFlag | Linux | false", "Windows | CustomFlag | false"
  }, delimiter = '|')
  void callsRespectTheOperatingSystemAndMono(String caller, String target, boolean unavailable) {
    initServerContext(Path.of("src/test/resources/metadata/designer"));
    var source = "#Если " + target + " Тогда\nПроцедура ТолькоДляЦели()\nКонецПроцедуры\n#КонецЕсли\n"
      + "#Если " + caller + " Тогда\nПроцедура Вызов()\n  ТолькоДляЦели();\nКонецПроцедуры\n#КонецЕсли\n";
    var document = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, source, context);
    var diagnostics = getDiagnostics(document);

    if (unavailable) {
      assertThat(diagnostics).isNotEmpty().allSatisfy(problem ->
        assertThat(problem.getMessage().getLeft()).contains("ТолькоДляЦели"));
    } else {
      assertThat(diagnostics).isEmpty();
    }
  }
}
