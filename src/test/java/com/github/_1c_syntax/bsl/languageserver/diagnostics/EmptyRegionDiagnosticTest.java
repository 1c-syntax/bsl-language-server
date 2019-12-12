package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class EmptyRegionDiagnosticTest extends AbstractDiagnosticTest<EmptyRegionDiagnostic> {
  EmptyRegionDiagnosticTest() {
    super(EmptyRegionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(0, 1, 0, 13)
      .hasRange(4, 1, 4, 23)
      .hasRange(5, 1, 5, 26);

  }
}
