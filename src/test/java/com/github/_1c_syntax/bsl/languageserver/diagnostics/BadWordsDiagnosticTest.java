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

    assertThat(diagnostics).hasSize(6); // Проверка количества
    assertThat(diagnostics, true)
      .hasRange(0, 42, 0, 47)
      .hasRange(0, 48, 0, 54)
      .hasRange(4, 4, 4, 9)
      .hasRange(6, 24, 6, 29)
      .hasRange(6, 34, 6, 39)
      .hasRange(8, 4, 8, 10);
  }
}
