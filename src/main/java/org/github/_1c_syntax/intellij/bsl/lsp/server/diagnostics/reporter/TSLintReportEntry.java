/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import lombok.Getter;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import java.util.EnumMap;
import java.util.Map;

class TSLintReportEntry {
  @Getter private EntryPosition endPosition;
  @Getter private String failure;
  @Getter private String name;
  @Getter private String ruleName;
  @Getter private String ruleSeverity;
  @Getter private EntryPosition startPosition;

  private static Map<DiagnosticSeverity, String> severityMap = new EnumMap<>(DiagnosticSeverity.class);

  static {
    severityMap.put(DiagnosticSeverity.Error, "error");
    severityMap.put(DiagnosticSeverity.Hint, "warn");
    severityMap.put(DiagnosticSeverity.Information, "warn");
    severityMap.put(DiagnosticSeverity.Warning, "warn");
  }


  TSLintReportEntry(String fileName, Diagnostic diagnostic) {
    endPosition = new EntryPosition(diagnostic.getRange().getEnd());
    failure = diagnostic.getMessage();
    name = fileName;
    ruleName = diagnostic.getCode();
    ruleSeverity = severityMap.get(diagnostic.getSeverity());
    startPosition = new EntryPosition(diagnostic.getRange().getStart());
  }

  static class EntryPosition {
    @Getter private final int character;
    @Getter private final int line;
    @Getter private final int position;

    EntryPosition(org.eclipse.lsp4j.Position position) {
      line = position.getLine();
      character = position.getCharacter();
      this.position = position.getCharacter();
    }
  }

}
