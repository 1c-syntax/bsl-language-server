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

import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.AnalysisInfoJsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SequenceWriter;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class JsonReporter implements DiagnosticReporter {

  private ReportFile reportFile;
  private JsonGenerator generator;
  private SequenceWriter writer;
  private String sourceDir;

  @Override
  public String key() {
    return "json";
  }

  @Override
  public boolean isMetricCalculationRequired() {
    return true;
  }

  @Override
  public void beginReport(ReportContext context, Path outputDir) {
    sourceDir = context.sourceDir();

    var mapper = new AnalysisInfoJsonMapper();

    reportFile = ReportFile.create(outputDir, "bsl-json.json");
    generator = mapper.createGenerator(reportFile.stream());
    generator.writeStartObject();
    generator.writeName("date");
    // формат совпадает с @JsonFormat на AnalysisInfo.date: аннотация компонента записи
    // не применяется при потоковой записи поля, поэтому дату форматируем сами
    generator.writeString(context.date().format(DateTimeFormatter.ofPattern(AnalysisInfo.DATE_PATTERN)));
    generator.writeName("fileinfos");
    writer = mapper.writerFor(FileInfo.class).writeValuesAsArray(generator);
  }

  @Override
  public void accept(FileInfo fileInfo) {
    writer.write(fileInfo);
  }

  @Override
  public void endReport() {
    writer.close();
    generator.writeName("sourceDir");
    generator.writeString(sourceDir);
    generator.writeEndObject();
    generator.close();
    reportFile.commit();
    LOGGER.info("JSON report saved to {}", reportFile.path().toAbsolutePath());
  }

  @Override
  public void abortReport() {
    if (reportFile != null) {
      reportFile.close();
    }
  }
}
