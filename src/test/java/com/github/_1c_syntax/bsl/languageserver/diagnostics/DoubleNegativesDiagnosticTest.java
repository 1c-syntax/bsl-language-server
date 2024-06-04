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
      .hasRange(1, 5, 1, 73)
      .hasRange(7, 4, 7, 19)
      .hasRange(8, 4, 8, 20)
      .hasRange(9, 4, 9, 20)
      .hasRange(10, 4, 10, 21)
      .hasRange(11, 4, 11, 42)
      .hasRange(12, 4, 12, 42)
      .hasRange(13, 4, 13, 25)
      .hasRange(14, 4, 14, 25)
    ;

  }
}
