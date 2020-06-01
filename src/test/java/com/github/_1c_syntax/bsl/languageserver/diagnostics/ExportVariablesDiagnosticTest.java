package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class ExportVariablesDiagnosticTest extends AbstractDiagnosticTest<ExportVariablesDiagnostic> {
  ExportVariablesDiagnosticTest() {
    super(ExportVariablesDiagnostic.class);
  }

  @Test
  void test() throws IOException {

    String content = "Перем Перем1 Экспорт,\n Перем2\n,Перем53 \nЭкспорт\n\n\n";
    var document = TestUtils.getDocumentContext(content, context);
    List<Diagnostic> diagnostics = getDiagnostics(document);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 6, 0, 20)
      .hasRange(2, 1, 3, 7);
  }
}
