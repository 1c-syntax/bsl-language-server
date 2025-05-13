package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class LogicalOrInJoinQuerySectionDiagnosticTest extends AbstractDiagnosticTest<LogicalOrInJoinQuerySectionDiagnostic> {
  LogicalOrInJoinQuerySectionDiagnosticTest() {
    super(LogicalOrInJoinQuerySectionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);

    assertThat(diagnostics, true)
      .hasRange(12, 62, 12, 65)
      .hasRange(12, 108, 12, 111)
      .hasRange(24, 14, 24, 17)
      .hasRange(26, 14, 26, 17)
      .hasRange(27, 14, 27, 17)
      .hasRange(29, 14, 29, 17)
      .hasRange(30, 14, 30, 17)
      .hasRange(19, 15, 19, 18);

  }
}
