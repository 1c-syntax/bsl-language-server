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

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class ConsoleReporter extends AbstractDiagnosticReporter {

  public static final String KEY = "console";

  public ConsoleReporter(){
    super();
  }

  public ConsoleReporter(Path outputDir){
    super(outputDir);
  }

  @Override
  public void report(AnalysisInfo analysisInfo) {
    LOGGER.info("Analysis date: {}", analysisInfo.getDate());
    LOGGER.info("File info:\n{}", analysisInfo.getFileinfos());
  }
}
