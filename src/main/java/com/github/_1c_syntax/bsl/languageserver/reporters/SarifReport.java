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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Value
@RequiredArgsConstructor
@JsonClassDescription("Static Analysis Results Format (SARIF) Version 2.1.0 JSON Schema: " +
  "a standard format for the output of static analysis tools.")
public class SarifReport {

  static Map<DiagnosticSeverity, Level> severityToLevel = Map.of(
    DiagnosticSeverity.Error, Level.ERROR,
    DiagnosticSeverity.Warning, Level.WARNING,
    DiagnosticSeverity.Information, Level.NOTE,
    DiagnosticSeverity.Hint, Level.NONE
  );

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

  @NonFinal
  @Getter(AccessLevel.NONE)
  @JsonIgnore
  Collection<DiagnosticInfo> diagnosticInfos;

  public SarifReport(
    AnalysisInfo analysisInfo,
    LanguageServerConfiguration configuration,
    Collection<DiagnosticInfo> diagnosticInfos
  ) {
    this.analysisInfo = analysisInfo;
    this.configuration = configuration;
    this.diagnosticInfos = diagnosticInfos;

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

    @JsonPropertyDescription("The organization or company that produced the tool component.")
    String organization;

    @JsonPropertyDescription("The absolute URI at which information about this version " +
      "of the tool component can be found.")
    @JsonFormat(pattern = "uri")
    String informationUri;

    @JsonPropertyDescription("The language of the messages emitted into the log file during this run " +
      "(expressed as an ISO 639-1 two-letter lowercase language code) and an optional region " +
      "(expressed as an ISO 3166-1 two-letter uppercase subculture code associated with a country or region). " +
      "The casing is recommended but not required (in order for this data to conform to RFC5646).")
    String language;

    @JsonPropertyDescription("An array of reportingDescriptor objects relevant to the analysis performed " +
      "by the tool component.")
    List<ReportingDescriptor> rules;

    public ToolComponent() {
      name = "BSL Language Server";
      organization = "1c-syntax";
      informationUri = configuration.getSiteRoot();
      language = configuration.getLanguage().getLanguageCode();
      rules = diagnosticInfos.stream()
        .map(ReportingDescriptor::new)
        .collect(Collectors.toList());
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

    @JsonPropertyDescription("A value specifying the severity level of the result.")
    Level level;

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
      level = severityToLevel.get(diagnostic.getSeverity());
      analysisTarget = new ArtifactLocation(uri);
      locations = List.of(new Location(uri, diagnostic.getRange()));
    }
  }

  enum Level {
    @JsonProperty("none")
    NONE,

    @JsonProperty("note")
    NOTE,

    @JsonProperty("warning")
    WARNING,

    @JsonProperty("error")
    ERROR
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

  @Value
  @AllArgsConstructor
  @JsonClassDescription("Metadata that describes a specific report produced by the tool, " +
    "as part of the analysis it provides or its runtime reporting.")
  static class ReportingDescriptor {

    @JsonProperty(required = true)
    @JsonPropertyDescription("A stable, opaque identifier for the report.")
    String id;

    @JsonPropertyDescription("A report identifier that is understandable to an end user.")
    String name;

    @JsonPropertyDescription("A description of the report. Should, as far as possible, provide details sufficient " +
      "to enable resolution of any problem indicated by the result.")
    MultiformatMessageString fullDescription;

    // todo: defaultConfiguration

    @JsonPropertyDescription("A URI where the primary documentation for the report can be found.")
    @JsonFormat(pattern = "uri")
    String helpUri;

    @JsonPropertyDescription("Key/value pairs that provide additional information about the report.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    PropertyBag properties;

    public ReportingDescriptor(DiagnosticInfo diagnosticInfo) {
      id = diagnosticInfo.getCode().getStringValue();
      name = diagnosticInfo.getName();
      fullDescription = new MultiformatMessageString(diagnosticInfo);
      helpUri = diagnosticInfo.getDiagnosticCodeDescriptionHref();
      properties = new PropertyBag();

      diagnosticInfo.getTags().forEach(tag -> properties.tags.add(tag.name()));
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("A message string or message format string rendered in multiple formats.")
  static class MultiformatMessageString {

    @JsonProperty(required = true)
    @JsonPropertyDescription("A plain text message string or format string.")
    String text;

    @JsonPropertyDescription("A Markdown message string or format string.")
    String markdown;

    public MultiformatMessageString(DiagnosticInfo diagnosticInfo) {
      text = diagnosticInfo.getDescription();
      markdown = text;
    }
  }

  @Value
  @AllArgsConstructor
  @JsonClassDescription("Key/value pairs that provide additional information about the object.")
  static class PropertyBag {

    @JsonPropertyDescription("A set of distinct strings that provide additional information.")
    List<String> tags;

    public PropertyBag() {
      tags = new ArrayList<>();
    }
  }


}
