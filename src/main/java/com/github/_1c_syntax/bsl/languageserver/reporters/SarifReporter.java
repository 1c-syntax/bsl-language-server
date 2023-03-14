/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.contrastsecurity.sarif.ArtifactLocation;
import com.contrastsecurity.sarif.ConfigurationOverride;
import com.contrastsecurity.sarif.Invocation;
import com.contrastsecurity.sarif.Location;
import com.contrastsecurity.sarif.Message;
import com.contrastsecurity.sarif.MultiformatMessageString;
import com.contrastsecurity.sarif.PhysicalLocation;
import com.contrastsecurity.sarif.PropertyBag;
import com.contrastsecurity.sarif.Region;
import com.contrastsecurity.sarif.ReportingConfiguration;
import com.contrastsecurity.sarif.ReportingDescriptor;
import com.contrastsecurity.sarif.ReportingDescriptorReference;
import com.contrastsecurity.sarif.Result;
import com.contrastsecurity.sarif.Run;
import com.contrastsecurity.sarif.SarifSchema210;
import com.contrastsecurity.sarif.Tool;
import com.contrastsecurity.sarif.ToolComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Репортер в формат SARIF.
 *
 * @see <a href="https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html">SARIF specification</a>.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SarifReporter implements DiagnosticReporter {

  private static final Map<DiagnosticSeverity, Result.Level> severityToResultLevel = Map.of(
    DiagnosticSeverity.Error, Result.Level.ERROR,
    DiagnosticSeverity.Warning, Result.Level.WARNING,
    DiagnosticSeverity.Information, Result.Level.NOTE,
    DiagnosticSeverity.Hint, Result.Level.NONE
  );

  private static final Map<DiagnosticSeverity, ReportingConfiguration.Level> severityToReportLevel = Map.of(
    DiagnosticSeverity.Error, ReportingConfiguration.Level.ERROR,
    DiagnosticSeverity.Warning, ReportingConfiguration.Level.WARNING,
    DiagnosticSeverity.Information, ReportingConfiguration.Level.NOTE,
    DiagnosticSeverity.Hint, ReportingConfiguration.Level.NONE
  );

  private final LanguageServerConfiguration configuration;
  private final Collection<DiagnosticInfo> diagnosticInfos;
  private final ServerInfo serverInfo;

  @Override
  public String key() {
    return "sarif";
  }

  @Override
  @SneakyThrows
  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    var report = createReport(analysisInfo);

    var mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    var reportFile = new File(outputDir.toFile(), "./bsl-ls.sarif");
    mapper.writeValue(reportFile, report);
    LOGGER.info("SARIF report saved to {}", reportFile.getAbsolutePath());
  }

  private SarifSchema210 createReport(AnalysisInfo analysisInfo) {
    var schema = URI.create(
      "https://json.schemastore.org/sarif-2.1.0.json"
    );
    var run = createRun(analysisInfo);

    return new SarifSchema210()
      .with$schema(schema)
      .withVersion(SarifSchema210.Version._2_1_0)
      .withRuns(List.of(run));
  }

  private Run createRun(AnalysisInfo analysisInfo) {
    var tool = createTool();
    var invocation = createInvocation();
    var results = createResults(analysisInfo);

    return new Run()
      .withTool(tool)
      .withInvocations(List.of(invocation))
      .withLanguage(configuration.getLanguage().getLanguageCode())
      .withDefaultEncoding("UTF-8")
      .withDefaultSourceLanguage("BSL")
      .withResults(results);
  }

  private Invocation createInvocation() {
    Set<ConfigurationOverride> ruleConfigurationOverrides = new HashSet<>();
    var diagnosticsOptions = configuration.getDiagnosticsOptions();
    diagnosticsOptions.getParameters().forEach((String key, Either<Boolean, Map<String, Object>> option) -> {
      var descriptor = new ReportingDescriptorReference().withId(key);
      var configurationOverride = new ConfigurationOverride().withDescriptor(descriptor);
      var reportingConfiguration = new ReportingConfiguration();
      if (option.isLeft()) {
        reportingConfiguration.setEnabled(option.getLeft());
      } else {
        var parameters = new PropertyBag();
        var diagnosticParameters = option.getRight();
        diagnosticParameters.forEach(parameters::setAdditionalProperty);
        reportingConfiguration.setParameters(parameters);
      }
      configurationOverride.withConfiguration(reportingConfiguration);
      ruleConfigurationOverrides.add(configurationOverride);
    });
    ArtifactLocation workingDirectory = new ArtifactLocation()
      .withUri(Absolute.uri(new File(".").toURI()).toString());

    return new Invocation()
      .withExecutionSuccessful(true)
      .withRuleConfigurationOverrides(ruleConfigurationOverrides)
      .withWorkingDirectory(workingDirectory)
      .withProcessId((int) ProcessHandle.current().pid())
      ;
  }

  private Tool createTool() {
    var name = serverInfo.getName();
    var organization = "1c-syntax";
    var version = serverInfo.getVersion();
    var informationUri = URI.create(configuration.getSiteRoot());
    var language = configuration.getLanguage().getLanguageCode();
    var rules = diagnosticInfos.stream()
      .map(SarifReporter::createReportingDescriptor)
      .collect(Collectors.toSet());

    var driver = new ToolComponent()
      .withName(name)
      .withOrganization(organization)
      .withVersion(version)
      .withInformationUri(informationUri)
      .withLanguage(language)
      .withRules(rules);

    return new Tool()
      .withDriver(driver);
  }

  private static ReportingDescriptor createReportingDescriptor(DiagnosticInfo diagnosticInfo) {
    var id = diagnosticInfo.getCode().getStringValue();
    var name = diagnosticInfo.getName();
    var fullDescription = new MultiformatMessageString()
      .withText(diagnosticInfo.getDescription())
      .withMarkdown(diagnosticInfo.getDescription());
    var helpUri = URI.create(diagnosticInfo.getDiagnosticCodeDescriptionHref());

    var parameters = new PropertyBag();
    diagnosticInfo.getParameters().forEach(parameterInfo ->
      parameters.withAdditionalProperty(parameterInfo.getName(), parameterInfo.getDefaultValue())
    );

    var defaultConfiguration = new ReportingConfiguration()
      .withEnabled(diagnosticInfo.isActivatedByDefault())
      .withLevel(severityToReportLevel.get(diagnosticInfo.getLSPSeverity()))
      .withParameters(parameters);

    var tags = diagnosticInfo.getTags().stream()
      .map(Enum::name)
      .collect(Collectors.toSet());

    var properties = new PropertyBag().withTags(tags);

    return new ReportingDescriptor()
      .withId(id)
      .withName(name)
      .withFullDescription(fullDescription)
      .withHelpUri(helpUri)
      .withDefaultConfiguration(defaultConfiguration)
      .withProperties(properties);
  }

  private static List<Result> createResults(AnalysisInfo analysisInfo) {
    var results = new ArrayList<Result>();

    analysisInfo.getFileinfos().forEach(fileInfo ->
      fileInfo.getDiagnostics().stream()
        .map(diagnostic -> createResult(fileInfo, diagnostic))
        .collect(Collectors.toCollection(() -> results))
    );

    return results;
  }

  private static Result createResult(FileInfo fileInfo, Diagnostic diagnostic) {
    var uri = Absolute.uri(fileInfo.getPath().toUri()).toString();

    var message = new Message().withText(diagnostic.getMessage());
    var ruleId = DiagnosticCode.getStringValue(diagnostic.getCode());
    var level = severityToResultLevel.get(diagnostic.getSeverity());
    var analysisTarget = new ArtifactLocation().withUri(uri);
    var locations = List.of(createLocation(diagnostic.getMessage(), uri, diagnostic.getRange()));
    var relatedLocations = Optional.ofNullable(diagnostic.getRelatedInformation())
      .stream()
      .flatMap(Collection::stream)
      .skip(1)
      .map(relatedInformation -> createLocation(
        relatedInformation.getMessage(),
        relatedInformation.getLocation().getUri(),
        relatedInformation.getLocation().getRange()
      ))
      .collect(Collectors.toSet());

    return new Result()
      .withMessage(message)
      .withRuleId(ruleId)
      .withLevel(level)
      .withAnalysisTarget(analysisTarget)
      .withLocations(locations)
      .withRelatedLocations(relatedLocations);
  }

  private static Location createLocation(String messageString, String uri, Range range) {
    var message = new Message().withText(messageString);

    var artifactLocation = new ArtifactLocation().withUri(uri);
    var region = new Region()
      .withStartLine(range.getStart().getLine() + 1)
      .withStartColumn(range.getStart().getCharacter() + 1)
      .withEndLine(range.getEnd().getLine() + 1)
      .withEndColumn(range.getEnd().getCharacter() + 1);

    var physicalLocation = new PhysicalLocation()
      .withArtifactLocation(artifactLocation)
      .withRegion(region);

    return new Location()
      .withMessage(message)
      .withPhysicalLocation(physicalLocation);
  }
}
