package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DeletingCollectionItemDiagnosticTest extends AbstractDiagnosticTest<DeletingCollectionItemDiagnostic> {

  DeletingCollectionItemDiagnosticTest() {
    super(DeletingCollectionItemDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(1);
  }

}
