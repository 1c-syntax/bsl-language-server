/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import lombok.Getter;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import java.util.EnumMap;
import java.util.Map;

class TSLintReportEntry {
  @Getter
  private final EntryPosition startPosition;
  @Getter
  private final EntryPosition endPosition;
  @Getter
  private final String failure;
  @Getter
  private final String name;
  @Getter
  private final String ruleName;
  @Getter
  private final String ruleSeverity;

  private static final Map<DiagnosticSeverity, String> severityMap = new EnumMap<>(DiagnosticSeverity.class);

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
    ruleName = DiagnosticCode.getStringValue(diagnostic.getCode());
    ruleSeverity = severityMap.get(diagnostic.getSeverity());
    startPosition = new EntryPosition(diagnostic.getRange().getStart());
  }

  public TSLintReportEntry(
    @JsonProperty("startPosition") EntryPosition startPosition,
    @JsonProperty("endPosition") EntryPosition endPosition,
    @JsonProperty("failure") String failure,
    @JsonProperty("name") String name,
    @JsonProperty("ruleName") String ruleName,
    @JsonProperty("rileSeverity") String ruleSeverity
  ) {
    this.startPosition = startPosition;
    this.endPosition = endPosition;
    this.failure = failure;
    this.name = name;
    this.ruleName = ruleName;
    this.ruleSeverity = ruleSeverity;
  }

  static class EntryPosition {
    @Getter
    private final int character;
    @Getter
    private final int line;
    @Getter
    private final int position;

    EntryPosition(org.eclipse.lsp4j.Position position) {
      line = position.getLine();
      character = position.getCharacter();
      this.position = position.getCharacter();
    }

    public EntryPosition(
      @JsonProperty("character") int character,
      @JsonProperty("line") int line,
      @JsonProperty("position") int position
    ) {
      this.character = character;
      this.line = line;
      this.position = position;
    }
  }

}
