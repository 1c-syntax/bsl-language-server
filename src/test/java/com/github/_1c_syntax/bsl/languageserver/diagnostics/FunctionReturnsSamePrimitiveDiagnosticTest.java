package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
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

  assertThat(diagnostics)
    .hasSize(3)
    .anyMatch(diagnostic -> diagnostic.getRange().equals(
      Ranges.create(0, 8, 0, 23)))
    .anyMatch(diagnostic -> diagnostic.getRange().equals(
      Ranges.create(25, 8, 25, 14)))
    .anyMatch(diagnostic -> diagnostic.getRange().equals(
      Ranges.create(35, 8, 35, 17)));

  }
}
