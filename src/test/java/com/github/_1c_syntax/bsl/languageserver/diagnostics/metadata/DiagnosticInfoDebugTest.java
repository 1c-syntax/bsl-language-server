package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.LineLengthDiagnostic;
import com.github._1c_syntax.utils.StringInterner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

@SpringBootTest
class DiagnosticInfoDebugTest {
  @Autowired
  private LanguageServerConfiguration configuration;
  
  @Autowired
  private StringInterner stringInterner;

  @Test
  void debug() {
    File configurationFile = new File("./src/test/resources/.bsl-language-server-diagnostic-overrides.json");
    configuration.update(configurationFile);

    var info = new DiagnosticInfo(LineLengthDiagnostic.class, configuration, stringInterner);
    
    System.out.println("Type: " + info.getType());
    System.out.println("Severity: " + info.getSeverity());
    System.out.println("LSP Severity: " + info.getLSPSeverity());
    System.out.println("Override minimum: " + configuration.getDiagnosticsOptions().getOverrideMinimumLSPDiagnosticLevel());
  }
}
