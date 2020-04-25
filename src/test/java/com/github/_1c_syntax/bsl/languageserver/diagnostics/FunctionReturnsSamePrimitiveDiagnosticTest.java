package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FunctionReturnsSamePrimitiveDiagnosticTest extends AbstractDiagnosticTest<FunctionReturnsSamePrimitiveDiagnostic> {
  FunctionReturnsSamePrimitiveDiagnosticTest() {
    super(FunctionReturnsSamePrimitiveDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

  assertThat(diagnostics).hasSize(4);

  }
}
