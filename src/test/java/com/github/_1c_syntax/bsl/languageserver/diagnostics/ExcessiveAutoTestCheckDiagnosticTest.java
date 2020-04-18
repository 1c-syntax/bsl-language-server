package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class ExcessiveAutoTestCheckDiagnosticTest extends AbstractDiagnosticTest<ExcessiveAutoTestCheckDiagnostic> {
  ExcessiveAutoTestCheckDiagnosticTest() {
    super(ExcessiveAutoTestCheckDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(3, 4, 7, 13)
      .hasRange(14, 4, 16, 13)
      .hasRange(22, 4, 26, 13)
      .hasRange(46, 4, 48, 9)
      .hasRange(54, 4, 56, 9)
      .hasRange(62, 4, 66, 9);

  }
}
