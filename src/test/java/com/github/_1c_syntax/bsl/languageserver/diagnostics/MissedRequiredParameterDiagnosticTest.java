package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissedRequiredParameterDiagnosticTest extends AbstractDiagnosticTest<MissedRequiredParameterDiagnostic> {
  MissedRequiredParameterDiagnosticTest() {
    super(MissedRequiredParameterDiagnostic.class);
  }
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @Test
  void testLocalMethod() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(2, 16, 2, 29)
      .hasRange(8, 16, 8, 27)
      .hasRange(14, 16, 14, 26);
  }

  @Test
  void testSideMethod() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(2, 16, 2, 29)
      .hasRange(8, 16, 8, 27)
      .hasRange(14, 16, 14, 26);
  }
}
