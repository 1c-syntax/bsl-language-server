package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FieldsFromConnectionsWithoutIsNullDiagnosticTest extends AbstractDiagnosticTest<FieldsFromConnectionsWithoutIsNullDiagnostic> {
  FieldsFromConnectionsWithoutIsNullDiagnosticTest() {
    super(FieldsFromConnectionsWithoutIsNullDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(4, 13, 4, 30)
      .hasRange(16, 13, 16, 31)
      .hasRange(30, 13, 30, 31)
      .hasRange(47, 9, 47, 25)
      .hasRange(57, 13, 57, 27)
      .hasRange(87, 30, 87, 48);

  }
}
