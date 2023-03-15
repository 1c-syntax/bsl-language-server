package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class SetPrivilegedModeDiagnosticTest extends AbstractDiagnosticTest<SetPrivilegedModeDiagnostic> {
  SetPrivilegedModeDiagnosticTest() {
    super(SetPrivilegedModeDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(2, 4, 36)
      .hasRange(4, 4, 36)
      .hasSize(2);
  }
}
