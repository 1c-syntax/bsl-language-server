package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class WrongMetadataInQueryDiagnosticTest extends AbstractDiagnosticTest<WrongMetadataInQueryDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata";

  WrongMetadataInQueryDiagnosticTest() {
    super(WrongMetadataInQueryDiagnostic.class);
  }

  @Test
  void test() {

    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"РегистрСведений.УстаревшееИмяРегистра\" внутри запроса",
        4, 18, 55)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"РегистрСведений.УдалитьИмяРегистра\" внутри запроса",
        19, 40, 74)

      .hasSize(2);

  }
}
