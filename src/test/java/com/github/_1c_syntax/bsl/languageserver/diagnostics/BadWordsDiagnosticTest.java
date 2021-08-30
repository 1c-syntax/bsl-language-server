package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.*;

class BadWordsDiagnosticTest extends AbstractDiagnosticTest<BadWordsDiagnostic>{
  BadWordsDiagnosticTest() {
    super(BadWordsDiagnostic.class);
  }

  @Test
  void test() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("badWords", "лотус|шмотус");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics(); // Получение диагностик

    assertThat(diagnostics).hasSize(4); // Проверка количества
    assertThat(diagnostics, true)
      .hasRange(3, 0, 6, 41)
      .hasRange(3, 0, 6, 41)
      .hasRange(3, 0, 6, 41)
      .hasRange(9, 0, 9, 55);
  }
}
