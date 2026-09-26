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
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

@Slf4j
@Component
public class GenericIssueReporter extends AbstractDiagnosticReporter {

  private ReportFile reportFile;
  private JsonGenerator generator;
  private SequenceWriter writer;

  public GenericIssueReporter(ServerContextProvider serverContextProvider, DiagnosticInfos diagnosticInfos) {
    super(serverContextProvider, diagnosticInfos);
  }

  @Override
  public String key() {
    return "generic";
  }

  @Override
  public void beginReport(ReportContext context, Path outputDir) {
    var mapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();

    reportFile = ReportFile.create(outputDir, "bsl-generic-json.json");
    generator = mapper.createGenerator(reportFile.stream());
    generator.writeStartObject();
    generator.writeName("issues");
    writer = mapper.writerFor(GenericIssueReport.GenericIssueEntry.class).writeValuesAsArray(generator);
  }

  @Override
  public void accept(FileInfo fileInfo) {
    var diagnosticInfosByCode = getDiagnosticInfosByCode();
    var path = fileInfo.getPath().toString();

    var entries = fileInfo.getDiagnostics().stream()
      .map(diagnostic -> new GenericIssueReport.GenericIssueEntry(
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
    generator.writeEndObject();
    generator.close();
    reportFile.commit();
    LOGGER.info("Generic issue report saved to {}", reportFile.path().toAbsolutePath());
  }

  @Override
  public void abortReport() {
    if (reportFile != null) {
      reportFile.close();
    }
  }
}
