/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GenericIssueReport {

  private static final String RULETYPE_BUG = "BUG";
  private static final String RULETYPE_CODE_SMELL = "CODE_SMELL";
  private static final String SEVERITY_INFO = "INFO";
  private static final String SEVERITY_CRITICAL = "CRITICAL";
  private static final String SEVERITY_MAJOR = "MAJOR";
  private static final String SEVERITY_MINOR = "MINOR";


  @Getter
  @JsonProperty("issues")
  private final List<GenericIssueEntry> issues;

  private static Map<DiagnosticSeverity, String> severityMap = new EnumMap<>(DiagnosticSeverity.class);

  static {

    severityMap.put(DiagnosticSeverity.Error, SEVERITY_CRITICAL);
    severityMap.put(DiagnosticSeverity.Hint, SEVERITY_INFO);
    severityMap.put(DiagnosticSeverity.Information, SEVERITY_MINOR);
    severityMap.put(DiagnosticSeverity.Warning, SEVERITY_MAJOR);

  }

  private static Map<DiagnosticSeverity, String> typeMap = new EnumMap<>(DiagnosticSeverity.class);

  static {
    typeMap.put(DiagnosticSeverity.Error, RULETYPE_BUG);
    typeMap.put(DiagnosticSeverity.Hint, RULETYPE_CODE_SMELL);
    typeMap.put(DiagnosticSeverity.Information, RULETYPE_CODE_SMELL);
    typeMap.put(DiagnosticSeverity.Warning, RULETYPE_CODE_SMELL);
  }

  public GenericIssueReport(
    @JsonProperty("issues") List<GenericIssueEntry> issues
  ) {
    this.issues = new ArrayList<>(issues);
  }

  public GenericIssueReport(AnalysisInfo analysisInfo) {

    List<GenericIssueEntry> listGenericIssueEntry = new ArrayList<>();
    for (FileInfo fileInfo : analysisInfo.getFileinfos()) {
      for (Diagnostic diagnostic : fileInfo.getDiagnostics()) {
        GenericIssueEntry entry = new GenericIssueEntry(fileInfo.getPath().toString(), diagnostic);
        listGenericIssueEntry.add(entry);
      }
    }
    issues = listGenericIssueEntry;
  }

  @Value
  static class GenericIssueEntry {

    private final String engineId;
    private final String ruleId;
    private final String severity;
    private final String type;
    private final PrimaryLocation primaryLocation;
    private final int effortMinutes;

    public GenericIssueEntry(
      @JsonProperty("engineId") String engineId,
      @JsonProperty("ruleId") String ruleId,
      @JsonProperty("severity") String severity,
      @JsonProperty("type") String type,
      @JsonProperty("primaryLocation") PrimaryLocation primaryLocation,
      @JsonProperty("effortMinutes") int effortMinutes
    ) {
      this.engineId = engineId;
      this.ruleId = ruleId;
      this.severity = severity;
      this.type = type;
      this.primaryLocation = primaryLocation;
      this.effortMinutes = effortMinutes;
    }

    public GenericIssueEntry(String fileName, Diagnostic diagnostic) {
      DiagnosticSeverity localSeverity = diagnostic.getSeverity();

      engineId = diagnostic.getSource();
      ruleId = diagnostic.getCode();
      severity = severityMap.get(localSeverity);
      type = typeMap.get(localSeverity);
      primaryLocation = new PrimaryLocation(fileName, diagnostic);
      effortMinutes = 0;

    }
  }

  @Value
  static class PrimaryLocation {

    private final String message;
    private final String filePath;
    private final TextRange textRange;

    public PrimaryLocation(
      @JsonProperty("message") String message,
      @JsonProperty("filePath") String filePath,
      @JsonProperty("textRange") TextRange textRange
    ) {
      this.message = message;
      this.filePath = filePath;
      this.textRange = textRange;
    }

    public PrimaryLocation(String filePath, Diagnostic diagnostic) {
      message = diagnostic.getMessage();
      this.filePath = filePath;
      textRange = new TextRange(diagnostic.getRange());
    }

  }

  @Value
  static class TextRange {

    private final int startLine;
    private final int endLine;
    private final int startColumn;
    private final int endColumn;

    public TextRange(
      @JsonProperty("startLine") int startLine,
      @JsonProperty("endLine") int endLine,
      @JsonProperty("startColumn") int startColumn,
      @JsonProperty("endColumn") int endColumn
    ) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.startColumn = startColumn;
      this.endColumn = endColumn;
    }

    public TextRange(Range range) {

      Position startPosition = range.getStart();
      Position endPosition = range.getEnd();

      startLine = startPosition.getLine() + 1;
      startColumn = startPosition.getCharacter();

      endLine = endPosition.getLine() + 1;
      endColumn = endPosition.getCharacter(); // пока без лага

    }

  }

}
