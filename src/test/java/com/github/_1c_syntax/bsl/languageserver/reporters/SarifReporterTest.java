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

import com.contrastsecurity.sarif.Location;
import com.contrastsecurity.sarif.PhysicalLocation;
import com.contrastsecurity.sarif.Result;
import com.contrastsecurity.sarif.SarifSchema210;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class SarifReporterTest {

  @Autowired
  private SarifReporter reporter;
  @Autowired
  private Collection<DiagnosticInfo> diagnosticInfos;
  @Autowired
  private LanguageServerConfiguration configuration;

  private final File file = new File("./bsl-ls.sarif");

  @BeforeEach
  void setUp() {
    FileUtils.deleteQuietly(file);
  }

  @AfterEach
  void tearDown() {
    System.gc();
    FileUtils.deleteQuietly(file);
  }

  @Test
  void report() throws IOException {

    // given
    configuration.getDiagnosticsOptions().getParameters().put("Typo", Either.forLeft(false));
    configuration.getDiagnosticsOptions().getParameters().put("test", Either.forLeft(true));
    configuration.getDiagnosticsOptions().getParameters().put("some", Either.forRight(Map.of("test", 1)));

    Diagnostic diagnostic = new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    );

    var documentContext = TestUtils.getDocumentContext("");
    String sourceDir = ".";
    FileInfo fileInfo = new FileInfo(sourceDir, documentContext, Collections.singletonList(diagnostic));
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo), sourceDir);

    // when
    reporter.report(analysisInfo, Path.of(sourceDir));

    // then
    ObjectMapper mapper = new ObjectMapper();
    var report = mapper.readValue(file, SarifSchema210.class);

    assertThat(report).isNotNull();

    var run = report.getRuns().get(0);

    assertThat(run.getTool().getDriver().getName()).isEqualTo("BSL Language Server");
    assertThat(run.getTool().getDriver().getRules())
      .hasSize(diagnosticInfos.size());

    var invocation = run.getInvocations().get(0);
    assertThat(invocation.getRuleConfigurationOverrides())
      .hasSizeGreaterThan(0)
      .anyMatch(configurationOverride -> configurationOverride.getDescriptor().getId().equals("Typo")
        && !configurationOverride.getConfiguration().getEnabled())
      .anyMatch(configurationOverride -> configurationOverride.getDescriptor().getId().equals("test")
        && configurationOverride.getConfiguration().getEnabled())
      .anyMatch(configurationOverride -> configurationOverride.getDescriptor().getId().equals("some")
        && configurationOverride.getConfiguration().getParameters().getAdditionalProperties().get("test").equals(1))
    ;

    assertThat(run.getResults())
      .hasSize(1)

      .element(0)
      .matches(result -> result.getRuleId().equals("test"))
      .matches(result -> result.getLevel() == Result.Level.ERROR)
      .matches(result -> result.getMessage().getText().equals("message"))
      .matches(result -> result.getAnalysisTarget().getUri().equals(documentContext.getUri().toString()))

      .extracting(Result::getLocations)
      .extracting(locations -> locations.get(0))
      .extracting(Location::getPhysicalLocation)
      .extracting(PhysicalLocation::getRegion)

      .matches(region -> region.getStartLine().equals(diagnostic.getRange().getStart().getLine() + 1))
      .matches(region -> region.getStartColumn().equals(diagnostic.getRange().getStart().getCharacter() + 1))
      .matches(region -> region.getEndLine().equals(diagnostic.getRange().getEnd().getLine() + 1))
      .matches(region -> region.getEndColumn().equals(diagnostic.getRange().getEnd().getCharacter() + 1))
    ;

  }

}