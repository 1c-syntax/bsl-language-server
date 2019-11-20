package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.EmptyCodeBlockDiagnostic;
import org.junit.jupiter.api.Test;


class DiagnosticInfoTest {

  @Test
  void testGetDescription() {

    DiagnosticInfo diagnosticInfo = new DiagnosticInfo(EmptyCodeBlockDiagnostic.class, LanguageServerConfiguration.create());
    Object description = diagnosticInfo.getDescription("commentAsCode");

  }
}