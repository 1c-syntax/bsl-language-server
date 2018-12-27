package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

public class ConsoleReporter implements DiagnosticReporter {
  @Override
  public String getKey() {
    return "console";
  }

  @Override
  public void report(AnalysisInfo analysisInfo) {
    System.out.println("Analysis date: " + analysisInfo.getDate().toString());
    System.out.println(analysisInfo.getFileinfos());
  }
}
