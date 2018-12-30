/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportersAggregator {
  List<DiagnosticReporter> reporters = new ArrayList<>();

  public ReportersAggregator(String... reporterKeys) {
    Map<String, Class> reporterMap = reporterMap();
    for (String reporterKey : reporterKeys) {
      Class reporterClass = reporterMap.get(reporterKey);
      if (reporterClass == null) {
        throw new RuntimeException("Incorrect reporter key: " + reporterKey);
      }
      try {
        DiagnosticReporter reporter = (DiagnosticReporter) reporterClass.newInstance();
        reporters.add(reporter);
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }

    }
  }

  public void report(AnalysisInfo analysisInfo) {
    reporters.forEach(diagnosticReporter -> diagnosticReporter.report(analysisInfo));
  }

  private static Map<String, Class> reporterMap() {
    Map<String, Class> map = new HashMap<>();
    map.put("console",  ConsoleReporter.class);
    map.put("json", JsonReporter.class);
    map.put("tslint", TSLintReporter.class);

    return map;
  }
}
