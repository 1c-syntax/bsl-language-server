/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;

@Value
@AllArgsConstructor
@JsonClassDescription("Static Analysis Results Format (SARIF) Version 2.1.0 JSON Schema: " +
  "a standard format for the output of static analysis tools.")
public class SarifReport {

  @JsonProperty(required = true)
  @JsonPropertyDescription("The SARIF format version of this log file.")
  String version;
  @JsonProperty(required = true)
  @JsonPropertyDescription("The set of runs contained in this log file.")
  List<Run> runs;

  @NonFinal
  @Getter(AccessLevel.NONE)
  @JsonIgnore
  AnalysisInfo analysisInfo;

  @NonFinal
  @Getter(AccessLevel.NONE)
  @JsonIgnore
  LanguageServerConfiguration configuration;

  public SarifReport(AnalysisInfo analysisInfo, LanguageServerConfiguration configuration) {
    this.analysisInfo = analysisInfo;
    this.configuration = configuration;
    version = "2.1.0";
    runs = List.of(new Run());
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("Describes a single run of an analysis tool, and contains the reported output of that run.")
  class Run {

    @JsonProperty(required = true)
    @JsonPropertyDescription("Information about the tool or tool pipeline that generated the results in this run. " +
      "A run can only contain results produced by a single tool or tool pipeline. " +
      "A run can aggregate results from multiple log files, as long as context around the tool run " +
      "(tool command-line arguments and the like) is identical for all aggregated files.")
    Tool tool;

    @JsonPropertyDescription("The set of results contained in an SARIF log. " +
      "The results array can be omitted when a run is solely exporting rules metadata. " +
      "It must be present (but may be empty) if a log file represents an actual scan.")
    List<Result> results;

    public Run() {
      tool = new Tool();
      results = new ArrayList<>();
      for (var fileInfo : analysisInfo.getFileinfos()) {
        for (var diagnostic : fileInfo.getDiagnostics()) {
          var result = new Result(fileInfo, diagnostic);
          results.add(result);
        }

      }

    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("The analysis tool that was run.")
  class Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The analysis tool that was run.")
    ToolComponent driver;

    public Tool() {
      driver = new ToolComponent();
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A component, such as a plug-in or the driver, of the analysis tool that was run.")
  class ToolComponent {

    @JsonProperty(required = true)
    @JsonPropertyDescription("The name of the tool component.")
    String name;

    @JsonPropertyDescription("The language of the messages emitted into the log file during this run " +
      "(expressed as an ISO 639-1 two-letter lowercase language code) and an optional region " +
      "(expressed as an ISO 3166-1 two-letter uppercase subculture code associated with a country or region). " +
      "The casing is recommended but not required (in order for this data to conform to RFC5646).")
    String language;

    public ToolComponent() {
      name = "BSL Language Server";
      language = configuration.getLanguage().getLanguageCode();
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A result produced by an analysis tool.")
  class Result {

    @JsonProperty(required = true)
    @JsonPropertyDescription("A message that describes the result. " +
      "The first sentence of the message only will be displayed when visible space is limited.")
    Message message;

    @JsonPropertyDescription("The stable, unique identifier of the rule, if any, to which this result is relevant.")
    String ruleId;

    // todo: ruleIndex? rule?

    @JsonPropertyDescription("Identifies the artifact that the analysis tool was instructed to scan. " +
      "This need not be the same as the artifact where the result actually occurred.")
    ArtifactLocation analysisTarget;

    @JsonPropertyDescription("The set of locations where the result was detected. " +
      "Specify only one location unless the problem indicated by the result can only be corrected by making a change " +
      "at every specified location.")
    List<Location> locations;

    public Result(FileInfo fileInfo, Diagnostic diagnostic) {
      var uri = fileInfo.getPath().toUri().toString();

      message = new Message(diagnostic.getMessage());
      ruleId = DiagnosticCode.getStringValue(diagnostic.getCode());
      analysisTarget = new ArtifactLocation(uri);
      locations = List.of(new Location(uri, diagnostic.getRange()));
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("Encapsulates a message intended to be read by the end user.")
  static class Message {

    @JsonProperty(required = true)
    @JsonPropertyDescription("A plain text message string.")
    String text;
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("Specifies the location of an artifact.")
  static class ArtifactLocation {

    @JsonPropertyDescription("A string containing a valid relative or absolute URI.")
    @JsonFormat(pattern = "uri-reference")
    String uri;

  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A location within a programming artifact.")
  class Location {

    // todo: message?

    @JsonPropertyDescription("Identifies the artifact and region.")
    PhysicalLocation physicalLocation;

    public Location(String uri, Range range) {
      physicalLocation = new PhysicalLocation(uri, range);
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A physical location relevant to a result. " +
    "Specifies a reference to a programming artifact together with " +
    "a range of bytes or characters within that artifact.")
  class PhysicalLocation {

    @JsonPropertyDescription("The location of the artifact.")
    ArtifactLocation artifactLocation;

    @JsonPropertyDescription("Specifies a portion of the artifact.")
    Region region;

    public PhysicalLocation(String uri, Range range) {
      artifactLocation = new ArtifactLocation(uri);
      region = new Region(range);
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A region within an artifact where a result was detected.")
  static class Region {

    @JsonPropertyDescription("The line number of the first character in the region.")
    int startLine;

    @JsonPropertyDescription("The column number of the first character in the region.")
    int startColumn;

    @JsonPropertyDescription("The line number of the last character in the region.")
    int endLine;

    @JsonPropertyDescription("The column number of the character following the end of the region.")
    int endColumn;

    public Region(Range range) {
      startLine = range.getStart().getLine() + 1;
      startColumn = range.getStart().getCharacter() + 1;
      endLine = range.getEnd().getLine() + 1;
      endColumn = range.getEnd().getCharacter() + 1;
    }
  }
}
