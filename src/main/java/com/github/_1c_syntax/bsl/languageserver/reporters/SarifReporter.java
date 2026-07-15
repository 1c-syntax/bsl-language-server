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
import com.contrastsecurity.sarif.SarifSchema210;
import com.contrastsecurity.sarif.Tool;
import com.contrastsecurity.sarif.ToolComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationFeature;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMessage;

/**
 * Репортер в формат SARIF.
 *
 * @see <a href="https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html">SARIF specification</a>.
 */
@Component
@Slf4j
public class SarifReporter extends AbstractDiagnosticReporter {

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

  private final ServerInfo serverInfo;
  private final LanguageServerConfiguration configuration;

  public SarifReporter(
    ServerContextProvider serverContextProvider,
    DiagnosticInfos diagnosticInfos,
    ServerInfo serverInfo,
    LanguageServerConfiguration configuration
  ) {
    super(serverContextProvider, diagnosticInfos);
    this.serverInfo = serverInfo;
    this.configuration = configuration;
  }

  @Override
  public String key() {
    return "sarif";
  }

  @Override
  @SneakyThrows
  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    var reportFile = new File(outputDir.toFile(), "./bsl-ls.sarif");

    // Отчёт формируется потоково через JsonGenerator: результаты (по одному на диагностику,
    // на крупной конфигурации — миллионы) сериализуются по мере обхода и не удерживаются в
    // памяти целиком. Так исключается построение второго полного графа Result/Location/Region
    // поверх уже вычисленных диагностик — главный источник пикового потребления памяти при
    // генерации SARIF (см. issue #4248). Отступы (INDENT_OUTPUT) сохранены: файл на миллионы
    // результатов иначе — одна строка на сотни МБ, которую не открыть в редакторе.
    var mapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();

    try (
      var out = new BufferedOutputStream(new FileOutputStream(reportFile));
      var gen = mapper.createGenerator(out)
    ) {
      gen.writeStartObject();
      gen.writeName("$schema");
      gen.writeString("https://json.schemastore.org/sarif-2.1.0.json");
      gen.writeName("version");
      gen.writePOJO(SarifSchema210.Version._2_1_0);
      gen.writeName("runs");
      gen.writeStartArray();
      writeRun(gen, analysisInfo);
      gen.writeEndArray();
      gen.writeEndObject();
    }

    LOGGER.info("SARIF report saved to {}", reportFile.getAbsolutePath());
  }

  private void writeRun(JsonGenerator gen, AnalysisInfo analysisInfo) {
    gen.writeStartObject();
    gen.writeName("tool");
    gen.writePOJO(createTool(configuration));
    gen.writeName("invocations");
    gen.writePOJO(List.of(createInvocation(configuration)));
    gen.writeName("language");
    gen.writeString(configuration.getLanguage().getLanguageCode());
    gen.writeName("defaultEncoding");
    gen.writeString("UTF-8");
    gen.writeName("defaultSourceLanguage");
    gen.writeString("BSL");
    gen.writeName("results");
    gen.writeStartArray();
    for (FileInfo fileInfo : analysisInfo.fileinfos()) {
      // uri вычисляется один раз на файл, а не на каждую диагностику
      var uri = Absolute.uri(fileInfo.getPath().toUri()).toString();
      for (Diagnostic diagnostic : fileInfo.getDiagnostics()) {
        gen.writePOJO(createResult(uri, diagnostic));
      }
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static Invocation createInvocation(LanguageServerConfiguration configuration) {
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

  private Tool createTool(LanguageServerConfiguration configuration) {
    var diagnosticInfoValues = diagnosticInfos.getByCode().values();

    var name = serverInfo.getName();
    var organization = "1c-syntax";
    var version = serverInfo.getVersion();
    var informationUri = URI.create(configuration.getSiteRoot());
    var language = configuration.getLanguage().getLanguageCode();
    var rules = diagnosticInfoValues.stream()
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

  private static Result createResult(String uri, Diagnostic diagnostic) {
    var messageText = DiagnosticMessage.getStringValue(diagnostic.getMessage());

    var message = new Message().withText(messageText);
    var ruleId = DiagnosticCode.getStringValue(diagnostic.getCode());
    var level = severityToResultLevel.get(diagnostic.getSeverity());
    var analysisTarget = new ArtifactLocation().withUri(uri);
    var locations = List.of(createLocation(messageText, uri, diagnostic.getRange()));
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
