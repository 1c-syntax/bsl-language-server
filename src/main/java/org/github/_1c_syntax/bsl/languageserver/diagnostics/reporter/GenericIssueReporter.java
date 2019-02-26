/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class GenericIssueReporter extends AbstractDiagnosticReporter {

  public static final String KEY = "generic";

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericIssueReporter.class.getSimpleName());

  public GenericIssueReporter(){
    super();
  }

  public GenericIssueReporter(Path outputDir){
    super(outputDir);
  }

  @Override
  public void report(AnalysisInfo analysisInfo) {

    GenericIssueReport report = new GenericIssueReport(analysisInfo);
    ObjectMapper mapper = new ObjectMapper();
    try {
      File reportFile = new File(outputDir.toFile(), "bsl-generic-json.json");
      mapper.writeValue(reportFile, report);
      LOGGER.info("Generic issue report saved to {}", reportFile.getAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

}
