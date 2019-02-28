package org.github._1c_syntax.bsl.languageserver.configuration;

import lombok.Data;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticConfiguration;

import java.util.HashMap;
import java.util.Map;

@Data
public class LanguageServerConfiguration {
  private DiagnosticLanguage diagnosticLanguage;
  private final Map<String, Either<Boolean, DiagnosticConfiguration>> diagnostics;

  public LanguageServerConfiguration() {
    diagnostics = new HashMap<>();
    diagnosticLanguage = DiagnosticLanguage.EN;
  }
}
