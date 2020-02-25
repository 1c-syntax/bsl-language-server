package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class PublicMethodsDescriptionDiagnosticTest extends AbstractDiagnosticTest<PublicMethodsDescriptionDiagnostic> {
  PublicMethodsDescriptionDiagnosticTest() {
    super(PublicMethodsDescriptionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(41, 0, 45, 12)
      .hasRange(55, 0, 59, 12);

  }

  @Test
  void testConfigure() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkAllRegion", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(41, 0, 45, 12)
      .hasRange(55, 0, 59, 12)
      .hasRange(103, 0, 107, 12);
  }
}
