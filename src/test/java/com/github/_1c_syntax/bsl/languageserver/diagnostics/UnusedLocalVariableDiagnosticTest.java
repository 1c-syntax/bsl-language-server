package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UnusedLocalVariableDiagnosticTest extends AbstractDiagnosticTest<UnusedLocalVariableDiagnostic> {
  UnusedLocalVariableDiagnosticTest() {
    super(UnusedLocalVariableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(14, 10, 14, 13)
      .hasRange(16, 1, 16, 12)
      .hasRange(45, 4, 45, 17)
      .hasRange(51, 8, 51, 10);

  }
}
