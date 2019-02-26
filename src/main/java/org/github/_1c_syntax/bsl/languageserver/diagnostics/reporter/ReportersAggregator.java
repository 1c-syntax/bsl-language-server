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

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportersAggregator {
  private final List<AbstractDiagnosticReporter> reporters = new ArrayList<>();
  private final Path outputDir;

  public ReportersAggregator(Path outputDir, String[] reporterKeys) {
    this.outputDir = outputDir;
    addReporterKeys(reporterKeys);
  }

  public void report(AnalysisInfo analysisInfo) {
    reporters.forEach(diagnosticReporter -> diagnosticReporter.report(analysisInfo));
  }

  @SuppressWarnings("unchecked")
  private void addReporterKeys(String[] reporterKeys) {
    Map<String, Class> reporterMap = reporterMap();

    for (String reporterKey : reporterKeys) {
      Class<AbstractDiagnosticReporter> reporterClass = reporterMap.get(reporterKey);
      if (reporterClass == null) {
        throw new RuntimeException("Incorrect reporter key: " + reporterKey);
      }
      if (!AbstractDiagnosticReporter.class.isAssignableFrom(reporterClass)) {
        continue;
      }
      try {
        AbstractDiagnosticReporter reporter = reporterClass.getConstructor(Path.class).newInstance(outputDir);
        reporters.add(reporter);
      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Map<String, Class> reporterMap() {
    Map<String, Class> map = new HashMap<>();
    map.put(ConsoleReporter.KEY,  ConsoleReporter.class);
    map.put(JsonReporter.KEY, JsonReporter.class);
    map.put(JUnitReporter.KEY, JUnitReporter.class);
    map.put(TSLintReporter.KEY, TSLintReporter.class);
    map.put(GenericIssueReporter.KEY, GenericIssueReporter.class);

    return map;
  }
}
