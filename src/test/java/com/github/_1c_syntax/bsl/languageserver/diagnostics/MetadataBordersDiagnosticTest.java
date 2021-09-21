package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MetadataBordersDiagnosticTest extends AbstractDiagnosticTest<MetadataBordersDiagnostic> {
  MetadataBordersDiagnosticTest() {
    super(MetadataBordersDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("metadataBordersParameters", "{\"лотус|шмотус\":\"MetadataBordersDiagnostic\"}");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(0, 42, 0, 47)
      .hasRange(0, 48, 0, 54)
      .hasRange(4, 4, 4, 9)
      .hasRange(6, 24, 6, 29)
      .hasRange(6, 34, 6, 39)
      .hasRange(8, 4, 8, 10);

  }
}
