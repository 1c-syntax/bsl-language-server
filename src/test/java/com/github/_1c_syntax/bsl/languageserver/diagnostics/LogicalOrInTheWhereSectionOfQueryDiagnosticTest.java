package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class LogicalOrInTheWhereSectionOfQueryDiagnosticTest extends AbstractDiagnosticTest<LogicalOrInTheWhereSectionOfQueryDiagnostic> {
  LogicalOrInTheWhereSectionOfQueryDiagnosticTest() {
    super(LogicalOrInTheWhereSectionOfQueryDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(7, 15,18)
      .hasRange(19, 8, 11)
      .hasRange(31, 38, 41)
      .hasRange(43, 8, 11)
      .hasRange(44, 36, 39)
      .hasRange(58, 21, 24)
    ;

  }
}
