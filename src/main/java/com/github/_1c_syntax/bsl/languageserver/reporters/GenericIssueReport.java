/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenericIssueReport {

  private static final Map<DiagnosticType, RuleType> diagnosticTypeRuleTypeMap = Map.of(
    DiagnosticType.ERROR, RuleType.BUG,
    DiagnosticType.CODE_SMELL, RuleType.CODE_SMELL,
    DiagnosticType.SECURITY_HOTSPOT, RuleType.SECURITY_HOTSPOT,
    DiagnosticType.VULNERABILITY, RuleType.VULNERABILITY
  );

  @Getter
  @JsonProperty("issues")
  private final List<GenericIssueEntry> issues;

  public GenericIssueReport(
    @JsonProperty("issues") List<GenericIssueEntry> issues
  ) {
    this.issues = new ArrayList<>(issues);
  }

  public GenericIssueReport(AnalysisInfo analysisInfo, Map<String, DiagnosticInfo> diagnosticInfos) {
    issues = new ArrayList<>();
    for (FileInfo fileInfo : analysisInfo.getFileinfos()) {
      for (Diagnostic diagnostic : fileInfo.getDiagnostics()) {
        GenericIssueEntry entry = new GenericIssueEntry(
          fileInfo.getPath().toString(),
          diagnostic,
          diagnosticInfos.get(DiagnosticCode.getStringValue(diagnostic.getCode()))
        );
        issues.add(entry);
      }
    }
  }

  @Value
  static class GenericIssueEntry {

    String engineId;
    String ruleId;
    String severity;
    RuleType type;
    Location primaryLocation;
    int effortMinutes;
    List<Location> secondaryLocations;

    public GenericIssueEntry(
      @JsonProperty("engineId") String engineId,
      @JsonProperty("ruleId") String ruleId,
      @JsonProperty("severity") String severity,
      @JsonProperty("type") RuleType type,
      @JsonProperty("primaryLocation") Location primaryLocation,
      @JsonProperty("effortMinutes") int effortMinutes,
      @JsonProperty("secondaryLocations") List<Location> secondaryLocations
    ) {
      this.engineId = engineId;
      this.ruleId = ruleId;
      this.severity = severity;
      this.type = type;
      this.primaryLocation = primaryLocation;
      this.effortMinutes = effortMinutes;
      this.secondaryLocations = new ArrayList<>(secondaryLocations);
    }

    public GenericIssueEntry(String fileName, Diagnostic diagnostic, DiagnosticInfo diagnosticInfo) {
      engineId = diagnostic.getSource();
      ruleId = diagnosticInfo.getCode().getStringValue();
      severity = diagnosticInfo.getSeverity().name();
      type = diagnosticTypeRuleTypeMap.get(diagnosticInfo.getType());
      primaryLocation = new Location(fileName, diagnostic);
      effortMinutes = diagnosticInfo.getMinutesToFix();

      List<DiagnosticRelatedInformation> relatedInformation = diagnostic.getRelatedInformation();
      if (relatedInformation == null) {
        secondaryLocations = new ArrayList<>();
      } else {
        secondaryLocations = relatedInformation.stream()
          .map(Location::new)
          .collect(Collectors.toList());
      }
    }
  }

  @Value
  static class Location {

    String message;
    String filePath;
    TextRange textRange;

    public Location(
      @JsonProperty("message") String message,
      @JsonProperty("filePath") String filePath,
      @JsonProperty("textRange") TextRange textRange
    ) {
      this.message = message;
      this.filePath = filePath;
      this.textRange = textRange;
    }

    public Location(String filePath, Diagnostic diagnostic) {
      message = diagnostic.getMessage();
      this.filePath = filePath;
      textRange = new TextRange(diagnostic.getRange());
    }

    public Location(DiagnosticRelatedInformation relatedInformation) {
      message = relatedInformation.getMessage();
      filePath = Paths.get(URI.create(relatedInformation.getLocation().getUri())).toString();
      textRange = new TextRange(relatedInformation.getLocation().getRange());
    }
  }

  @Value
  static class TextRange {
    int startLine;
    int endLine;
    int startColumn;
    int endColumn;

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
      endColumn = endPosition.getCharacter();
    }

  }

  enum RuleType {
    BUG,
    CODE_SMELL,
    SECURITY_HOTSPOT,
    VULNERABILITY
  }
}
