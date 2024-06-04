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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.AnalysisInfoObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Component
public class JsonReporter implements DiagnosticReporter {

  @Override
  public String key() {
    return "json";
  }

  @Override
  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    ObjectMapper mapper = new AnalysisInfoObjectMapper();

    try {
      File reportFile = new File(outputDir.toFile(), "./bsl-json.json");
      mapper.writeValue(reportFile, analysisInfo);
      LOGGER.info("JSON report saved to {}", reportFile.getAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
