package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NonExportMethodsInApiRegionDiagnosticTest extends AbstractDiagnosticTest<NonExportMethodsInApiRegionDiagnostic> {

  NonExportMethodsInApiRegionDiagnosticTest() {
    super(NonExportMethodsInApiRegionDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics)
      // на +
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(8, 10, 8, 16)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(20, 10, 20, 13)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(25, 14, 25, 27)))
    ;
  }
}
