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
  void testWithoutSettings() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void testMatchesInValidModule() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("metadataBordersParameters", "{\"лотус|шмотус\":\"fake-uri\"}");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(0);
  }

  @Test
  void testMatchesInWrongModule() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("metadataBordersParameters", "{\"лотус|шмотус\":\"MetadataBordersDiagnostic\"}");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(3, 0, 6, 41)
      .hasRange(3, 0, 6, 41)
      .hasRange(3, 0, 6, 41)
      .hasRange(8, 0, 8, 55);
  }
}
