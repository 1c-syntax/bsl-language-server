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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

@Slf4j
@Component
public class CodeQualityReporter extends AbstractDiagnosticReporter {

  private ReportFile reportFile;
  private SequenceWriter writer;

  public CodeQualityReporter(ServerContextProvider serverContextProvider, DiagnosticInfos diagnosticInfos) {
    super(serverContextProvider, diagnosticInfos);
  }

  @Override
  public String key() {
    return "code-quality";
  }

  @Override
  public void beginReport(ReportContext context, Path outputDir) {
    var indenter = new DefaultIndenter().withLinefeed("\n");
    var printer = new DefaultPrettyPrinter()
      .withObjectIndenter(indenter);

    var mapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .defaultPrettyPrinter(printer)
      .build();

    reportFile = ReportFile.create(outputDir, "bsl-code-quality.json");
    writer = mapper.writerFor(CodeQualityReportEntry.class).writeValuesAsArray(reportFile.stream());
  }

  @Override
  public void accept(FileInfo fileInfo) {
    var diagnosticInfosByCode = getDiagnosticInfosByCode();
    var path = fileInfo.getPath().toString().replace("\\", "/");

    var entries = fileInfo.getDiagnostics().stream()
      .map(diagnostic -> new CodeQualityReportEntry(
        path,
        diagnostic,
        diagnosticInfosByCode.get(DiagnosticCode.getStringValue(diagnostic.getCode()))
      ))
      .toList();

    writer.writeAll(entries);
  }

  @Override
  public void endReport() {
    writer.close();
    reportFile.commit();
    LOGGER.info("CodeQuality report saved to {}", reportFile.path().toAbsolutePath());
  }

  @Override
  public void abortReport() {
    if (reportFile != null) {
      reportFile.close();
    }
  }
}
