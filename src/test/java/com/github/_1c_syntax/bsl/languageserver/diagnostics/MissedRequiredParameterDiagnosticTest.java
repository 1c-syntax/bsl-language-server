package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissedRequiredParameterDiagnosticTest extends AbstractDiagnosticTest<MissedRequiredParameterDiagnostic> {
  MissedRequiredParameterDiagnosticTest() {
    super(MissedRequiredParameterDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(2, 16, 2, 29)
      .hasRange(8, 16, 8, 27);

  }
}
