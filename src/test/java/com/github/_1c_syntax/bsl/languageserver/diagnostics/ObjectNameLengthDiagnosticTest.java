package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class ObjectNameLengthDiagnosticTest extends AbstractDiagnosticTest<ObjectNameLengthDiagnostic> {
  ObjectNameLengthDiagnosticTest() {
    super(ObjectNameLengthDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(6, 0, 6, 20);

  }
}
