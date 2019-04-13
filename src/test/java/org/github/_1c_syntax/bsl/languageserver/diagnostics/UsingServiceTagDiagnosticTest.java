package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsingServiceTagDiagnosticTest extends AbstractDiagnosticTest<UsingServiceTagDiagnostic> {

  UsingServiceTagDiagnosticTest()
  {
    super(UsingServiceTagDiagnostic.class);
  }

  @Test
  void runTest()
  {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(1, 0, 1, 36));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(13, 1, 13, 47));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(21, 4, 21, 29));

  }
}
