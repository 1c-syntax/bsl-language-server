package com.github._1c_syntax.bsl.languageserver.diagnostics;

import lombok.val;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class SetPermissionsForNewObjectsDiagnosticTest extends AbstractDiagnosticTest<SetPermissionsForNewObjectsDiagnostic> {
  SetPermissionsForNewObjectsDiagnosticTest() {
    super(SetPermissionsForNewObjectsDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";

  @Test
  void test() {

    initServerContext(PATH_TO_METADATA);
    val configuration = context.getConfiguration();

    if (!configuration.getRoles().isEmpty()){
      List<Diagnostic> diagnostics = getDiagnostics();
      assertThat(diagnostics).hasSize(1);
    }

  }

}
