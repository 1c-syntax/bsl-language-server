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
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LostVariableDiagnosticTest extends AbstractDiagnosticTest<LostVariableDiagnostic> {

  @Test
  void originalExamples() {
    assertThat(getDiagnostics()).extracting(d -> d.getRange().getStart().getLine() + 1)
      .containsExactlyInAnyOrder(3, 4, 5, 6, 10, 24, 32, 54, 70, 81, 83, 92, 96, 100, 102,
        112, 117, 122, 124, 126, 128, 140, 140, 148, 151, 154, 160, 161, 164, 165,
        179, 206, 211, 217, 240, 244, 255, 276, 277,
        298, 306, 312, 315, 319, 337, 345, 346, 352, 355, 377, 381);
  }

  @Test
  void controlFlowAndLoopHeaders() {
    String file = "LostVariableFlow";
    assertThat(locations(getDiagnostics(file), getText(file))).containsExactlyInAnyOrder(
      "4:Х", "7:Х", "8:Выход", "11:Локальный", "34:Результат", "52:кОЛЛекция", "63:Сч",
      "67:ОбщийСчетчик", "70:ОбщийСчетчик", "79:Значение", "85:ITEMS");
  }

  @Test
  void formShadowingUsesOnlyTheOwningFormAndExplicitDeclarations() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
    var root = Path.of(PATH_TO_METADATA);
    var document = context.addDocument(Absolute.uri(root.resolve(
      "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl").toFile()));
    var catalog = context.addDocument(Absolute.uri(root.resolve(
      "Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl").toFile()));
    var content = getText("LostVariableForm");
    context.rebuildDocument(document, content, 1);
    context.rebuildDocument(catalog, content, 1);
    assertThat(locations(getDiagnostics(document), content)).containsExactly("13:НовыйИтог");
    diagnosticInstance.configure(Map.of("checkFormShadowing", true));
    assertThat(locations(getDiagnostics(document), content))
      .containsExactlyInAnyOrder("2:таблицаПодбора", "2:Parameters", "3:Параметры", "13:НовыйИтог");
    assertThat(locations(getDiagnostics(catalog), content))
      .containsExactlyInAnyOrder("2:Parameters", "3:Параметры", "11:ТаблицаПодбора",
        "12:ТаблицаПодбора", "13:НовыйИтог");
    assertThat(getDiagnostics("LostVariableForm")).noneMatch(d -> d.getRange().getStart().getLine() < 3);
  }

  private static List<String> locations(List<Diagnostic> diagnostics, String source) {
    var lines = source.lines().toList();
    return diagnostics.stream().map(d -> {
      var range = d.getRange();
      var start = range.getStart();
      assertThat(range.getEnd().getLine()).isEqualTo(start.getLine());
      return (start.getLine() + 1) + ":" + lines.get(start.getLine())
        .substring(start.getCharacter(), range.getEnd().getCharacter());
    }).toList();
  }
}
