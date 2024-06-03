package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DoubleNegativesDiagnosticTest extends AbstractDiagnosticTest<DoubleNegativesDiagnostic> {
  DoubleNegativesDiagnosticTest() {
    super(DoubleNegativesDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(3, 6, 3, 74)
    ;

  }
}
