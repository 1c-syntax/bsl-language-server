package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class JsonReporter implements DiagnosticReporter {
  @Override
  public String getKey() {
    return "json";
  }

  @Override
  public void report(AnalysisInfo analysisInfo) {
    ObjectMapper mapper = new ObjectMapper();
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    mapper.setDateFormat(df);

    try {
      mapper.writeValue(new File("./out.json"), analysisInfo);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
