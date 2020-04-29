package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DeprecatedMethods8317DiagnosticTest extends AbstractDiagnosticTest<DeprecatedMethods8317Diagnostic> {
  DeprecatedMethods8317DiagnosticTest() {
    super(DeprecatedMethods8317Diagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(4, 17, 4, 43)
      .hasRange(5, 17, 5, 45)
      .hasRange(6, 8, 6, 34);

  }
}
