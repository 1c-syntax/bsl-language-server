package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class WrongMetadataInQueryDiagnosticTest extends AbstractDiagnosticTest<WrongMetadataInQueryDiagnostic> {
  WrongMetadataInQueryDiagnosticTest() {
    super(WrongMetadataInQueryDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";

  @Test
  void test() {

    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics);
    assertThat(diagnostics, true)
      .hasRange(4, 18, 51)

      .hasSize(1);

  }
}
