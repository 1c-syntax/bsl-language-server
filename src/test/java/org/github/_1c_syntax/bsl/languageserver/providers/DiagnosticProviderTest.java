package org.github._1c_syntax.bsl.languageserver.providers;

import org.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

class DiagnosticProviderTest {



  @Test
  void configureNullDryRun() {
    // given
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider(LanguageServerConfiguration.create());
    List<BSLDiagnostic> diagnosticClasses = diagnosticProvider.getDiagnosticClasses();

    // when
    diagnosticClasses.forEach(diagnostic -> diagnostic.configure(null));

    // then
    // should run without runtime errors
  }
}