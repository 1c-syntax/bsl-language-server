/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GenericIssueReport {

  private static final String RULETYPE_BUG = "BUG";
  private static final String RULETYPE_CODE_SMELL = "CODE_SMELL";
  private static final String SEVERITY_INFO = "INFO";
  private static final String SEVERITY_CRITICAL = "CRITICAL";
  private static final String SEVERITY_MAJOR = "MAJOR";
  private static final String SEVERITY_MINOR = "MINOR";

  // TODO: пробросить из analyze?
  private static final LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
  private static final DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(configuration);
  private static final Map<DiagnosticSeverity, String> severityMap = new EnumMap<>(DiagnosticSeverity.class);
  private static final Map<DiagnosticSeverity, String> typeMap = new EnumMap<>(DiagnosticSeverity.class);

  static {
    severityMap.put(DiagnosticSeverity.Error, SEVERITY_CRITICAL);
    severityMap.put(DiagnosticSeverity.Hint, SEVERITY_INFO);
    severityMap.put(DiagnosticSeverity.Information, SEVERITY_MINOR);
    severityMap.put(DiagnosticSeverity.Warning, SEVERITY_MAJOR);
  }

  static {
    typeMap.put(DiagnosticSeverity.Error, RULETYPE_BUG);
    typeMap.put(DiagnosticSeverity.Hint, RULETYPE_CODE_SMELL);
    typeMap.put(DiagnosticSeverity.Information, RULETYPE_CODE_SMELL);
    typeMap.put(DiagnosticSeverity.Warning, RULETYPE_CODE_SMELL);
  }

  @Getter
  @JsonProperty("issues")
  private final List<GenericIssueEntry> issues;

  public GenericIssueReport(
    @JsonProperty("issues") List<GenericIssueEntry> issues
  ) {
    this.issues = new ArrayList<>(issues);
  }

  public GenericIssueReport(AnalysisInfo analysisInfo) {
    List<GenericIssueEntry> listGenericIssueEntry = new ArrayList<>();
    for (FileInfo fileInfo : analysisInfo.getFileinfos()) {
      for (Diagnostic diagnostic : fileInfo.getDiagnostics()) {
        GenericIssueEntry entry = new GenericIssueEntry(
          fileInfo.getPath().toString(),
          diagnostic
        );
        listGenericIssueEntry.add(entry);
      }
    }
    issues = listGenericIssueEntry;
  }

  @Value
  static class GenericIssueEntry {

    String engineId;
    String ruleId;
    String severity;
    String type;
    Location primaryLocation;
    int effortMinutes;
    List<Location> secondaryLocations;

    public GenericIssueEntry(
      @JsonProperty("engineId") String engineId,
      @JsonProperty("ruleId") String ruleId,
      @JsonProperty("severity") String severity,
      @JsonProperty("type") String type,
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

    public GenericIssueEntry(String fileName, Diagnostic diagnostic) {
      DiagnosticSeverity localSeverity = diagnostic.getSeverity();


      engineId = diagnostic.getSource();

      ruleId = DiagnosticCode.getStringValue(diagnostic.getCode());
      severity = severityMap.get(localSeverity);
      type = typeMap.get(localSeverity);
      primaryLocation = new Location(fileName, diagnostic);

      Optional<Class<? extends BSLDiagnostic>> diagnosticClass =
        diagnosticSupplier.getDiagnosticClass(diagnostic.getCode());
      if (diagnosticClass.isPresent()) {
        DiagnosticInfo info = new DiagnosticInfo(diagnosticClass.get(), configuration.getDiagnosticsOptions().getLanguage());
        effortMinutes = info.getMinutesToFix();
      } else {
        effortMinutes = 0;
      }

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
      endColumn = endPosition.getCharacter(); // пока без лага

    }

  }

}
