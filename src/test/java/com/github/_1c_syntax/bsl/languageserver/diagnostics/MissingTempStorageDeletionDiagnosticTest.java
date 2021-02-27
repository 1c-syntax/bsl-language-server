package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissingTempStorageDeletionDiagnosticTest extends AbstractDiagnosticTest<MissingTempStorageDeletionDiagnostic> {
  MissingTempStorageDeletionDiagnosticTest() {
    super(MissingTempStorageDeletionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(3, 24, 77)
      .hasRange(13, 24, 77)
      .hasRange(21, 24, 77)
      .hasRange(33, 24, 77)
    ;

  }
}
