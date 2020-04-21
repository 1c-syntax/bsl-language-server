/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
public class GenericCoverageReporter extends AbstractDiagnosticReporter {

  public static final String KEY = "genericCoverage";

  public GenericCoverageReporter() {
    super();
  }

  public GenericCoverageReporter(Path outputDir) {
    super(outputDir);
  }

  @Override
  public void report(AnalysisInfo analysisInfo) {

    GenericCoverageReport report = new GenericCoverageReport(analysisInfo);

    ObjectMapper mapper = new XmlMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    try {
      File reportFile = new File(outputDir.toFile(), "genericCoverage.xml");
      mapper.writeValue(reportFile, report);
      LOGGER.info("Generic coverage report saved to {}", reportFile.getCanonicalPath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
