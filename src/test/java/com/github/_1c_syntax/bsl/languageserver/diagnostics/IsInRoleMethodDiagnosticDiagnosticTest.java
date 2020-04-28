package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class IsInRoleMethodDiagnosticDiagnosticTest extends AbstractDiagnosticTest<IsInRoleMethodDiagnosticDiagnostic> {
  IsInRoleMethodDiagnosticDiagnosticTest() {
    super(IsInRoleMethodDiagnosticDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(32, 9, 32, 35)
      .hasRange(38, 9, 38, 23);

  }
}
