package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.languageserver.diagnostics.NumberOfOptionalParamsDiagnostic;
import org.junit.jupiter.api.Test;

class DiagnosticParameterInfoTest {

  @Test
  void createDiagnosticParameters() {

    var diagnosticInfo = new DiagnosticInfo(NumberOfOptionalParamsDiagnostic.class);

    final long currentTimeMillis = System.currentTimeMillis();
    var diagnosticParameters = DiagnosticParameterInfo.createDiagnosticParameters(diagnosticInfo);
    System.out.println(System.currentTimeMillis() - currentTimeMillis);
  }
}