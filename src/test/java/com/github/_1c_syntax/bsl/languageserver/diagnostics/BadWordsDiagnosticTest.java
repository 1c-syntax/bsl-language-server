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
    List<Diagnostic> diagnostics = getDiagnostics(); // Получение диагностик

    assertThat(diagnostics).hasSize(3); // Проверка количества
    assertThat(diagnostics, true)
      .hasRange(4, 0, 4, 9)  // Проверка конкретного случая
      .hasRange(3, 6, 3, 7)  // Проверка конкретного случая
      .hasRange(3, 6, 3, 7); // Проверка конкретного случая
  }
}
