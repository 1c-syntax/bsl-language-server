package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UsageWriteLogEventDiagnosticTest extends AbstractDiagnosticTest<UsageWriteLogEventDiagnostic> {
  UsageWriteLogEventDiagnosticTest() {
    super(UsageWriteLogEventDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(3, 4, 39)
      .hasRange(4, 4, 73)
      .hasRange(5, 4, 77)
      .hasRange(7, 4, 9, 61)
      .hasRange(11, 4, 79)
      .hasRange(16, 6, 17, 25)
      .hasRange(23, 6, 24, 24)
      .hasRange(31, 6, 32, 35)
      .hasRange(38, 6, 39, 37)
    ;

  }
}
