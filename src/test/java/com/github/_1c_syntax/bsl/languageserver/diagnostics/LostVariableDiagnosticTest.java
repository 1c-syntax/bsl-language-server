package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LostVariableDiagnosticTest extends AbstractDiagnosticTest<LostVariableDiagnostic> {
  LostVariableDiagnosticTest() {
    super(LostVariableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("Значение"), 1, 4, 12)
      .hasMessageOnRange(getMessage("МояПеременная"), 4, 4, 17)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 9, 4, 16)
      .hasSize(3);
    ;

  }
  String getMessage(String name){
    return String.format("Предыдущее значение <%s> не используется", name);
  }
}
