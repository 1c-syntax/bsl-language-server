package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

public interface DiagnosticReporter {
  String getKey();
  void report(AnalysisInfo analysisInfo);
}
