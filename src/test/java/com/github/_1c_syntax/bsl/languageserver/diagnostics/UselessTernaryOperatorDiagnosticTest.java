package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UselessTernaryOperatorDiagnosticTest extends AbstractDiagnosticTest<UselessTernaryOperatorDiagnostic> {

  UselessTernaryOperatorDiagnosticTest() {
    super(UselessTernaryOperatorDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 1, 26)
      .hasRange(2, 4, 2, 25)
      .hasRange(3, 4, 3, 26)
      .hasRange(4, 4, 4, 25)
      .hasRange(5, 4, 5, 21)
      .hasRange(6, 4, 6, 22)
      .hasRange(7, 4, 7, 19)
      .hasRange(8, 4, 8, 18);

  }
}
