package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import sun.jvm.hotspot.utilities.Assert;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonExportMethodsInApiRegionDiagnosticTest extends AbstractDiagnosticTest<NonExportMethodsInApiRegionDiagnostic> {
  NonExportMethodsInApiRegionDiagnosticTest() {
    super(NonExportMethodsInApiRegionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(3);
  }
}
