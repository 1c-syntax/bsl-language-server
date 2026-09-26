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

import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

@Slf4j
@Component
public class TSLintReporter implements DiagnosticReporter {

  private ReportFile reportFile;
  private SequenceWriter writer;

  @Override
  public String key() {
    return "tslint";
  }

  @Override
  public void beginReport(ReportContext context, Path outputDir) {
    var mapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();

    reportFile = ReportFile.create(outputDir, "bsl-tslint.json");
    writer = mapper.writerFor(TSLintReportEntry.class).writeValuesAsArray(reportFile.stream());
  }

  @Override
  public void accept(FileInfo fileInfo) {
    var path = fileInfo.getPath().toString();
    var entries = fileInfo.getDiagnostics().stream()
      .map(diagnostic -> new TSLintReportEntry(path, diagnostic))
      .toList();

    writer.writeAll(entries);
  }

  @Override
  public void endReport() {
    writer.close();
    reportFile.commit();
    LOGGER.info("TSLint report saved to {}", reportFile.path().toAbsolutePath());
  }

  @Override
  public void abortReport() {
    if (reportFile != null) {
      reportFile.close();
    }
  }
}
