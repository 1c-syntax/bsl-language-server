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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

@Component
@RequiredArgsConstructor
@Slf4j
public class SarifReporter implements DiagnosticReporter {

  private final LanguageServerConfiguration configuration;
  private final Collection<DiagnosticInfo> diagnosticInfos;

  @Override
  public String key() {
    return "sarif";
  }

  @Override
  @SneakyThrows
  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    var report = new SarifReport(analysisInfo, configuration, diagnosticInfos);

    var mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    var reportFile = new File(outputDir.toFile(), "./bsl-ls.sarif");
    mapper.writeValue(reportFile, report);
    LOGGER.info("SARIF report saved to {}", reportFile.getAbsolutePath());
  }
}
