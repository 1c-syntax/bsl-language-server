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

import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportersAggregator {

  @Autowired
  private List<DiagnosticReporter> reporters;

  @Autowired
  @Qualifier("filteredReporters")
  @Lazy
  // Don't remove @Autowired annotation. It's needed for injecting filteredReporters bean correctly.
  private List<DiagnosticReporter> filteredReporters;

  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    filteredReporters.forEach(diagnosticReporter -> diagnosticReporter.report(analysisInfo, outputDir));
  }

  public List<String> reporterKeys() {
    return reporters.stream()
      .map(DiagnosticReporter::key)
      .collect(Collectors.toList());
  }
}
