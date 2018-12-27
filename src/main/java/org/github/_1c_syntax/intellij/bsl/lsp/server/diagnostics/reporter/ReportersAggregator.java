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

    return map;
  }
}
