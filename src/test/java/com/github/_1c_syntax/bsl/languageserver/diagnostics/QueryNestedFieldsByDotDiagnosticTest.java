package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class QueryNestedFieldsByDotDiagnosticTest extends AbstractDiagnosticTest<QueryNestedFieldsByDotDiagnostic> {
  QueryNestedFieldsByDotDiagnosticTest() {
    super(QueryNestedFieldsByDotDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(21, 3, 21, 40) //Ошибка №1
      .hasRange(22, 3, 22, 39) //Ошибка №1
      .hasRange(23, 3, 23, 36) //Ошибка №1
      .hasRange(24, 3, 24, 43) //Ошибка №1
      .hasRange(29, 3, 29, 33) //Ошибка №7
      .hasRange(53, 6, 53, 39) //Ошибка №3
      .hasRange(53, 41, 53, 77) //Ошибка №3
      .hasRange(53, 79, 53, 116) //Ошибка №3
      .hasRange(101, 7, 101, 61) //Ошибка №2
      .hasRange(102, 7, 102, 64) //Ошибка №2
      .hasRange(103, 7, 103, 65) //Ошибка №2
      .hasRange(115, 3, 115, 82); //Ошибка №6
  }
}
