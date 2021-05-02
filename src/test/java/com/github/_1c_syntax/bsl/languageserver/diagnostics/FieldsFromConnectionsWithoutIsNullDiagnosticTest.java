package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FieldsFromConnectionsWithoutIsNullDiagnosticTest extends AbstractDiagnosticTest<FieldsFromConnectionsWithoutIsNullDiagnostic> {
  FieldsFromConnectionsWithoutIsNullDiagnosticTest() {
    super(FieldsFromConnectionsWithoutIsNullDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);

    checkContent(
      diagnostics.get(0),
      Ranges.create(6, 5, 7, 44),
      Ranges.create(4, 13, 4, 30)
    );

    checkContent(
      diagnostics.get(1),
      Ranges.create(20, 5, 21, 45),
      Ranges.create(16, 13, 16, 31)
    );

    checkContent(
      diagnostics.get(2),
      Ranges.create(33, 5, 34, 45),
      Ranges.create(30, 13, 30, 31)
    );

    checkContent(
      diagnostics.get(3),
      Ranges.create(45, 5, 46, 45),
      Ranges.create(47, 9, 47, 25)
      );

    checkContent(
      diagnostics.get(4),
      Ranges.create(60, 5, 61, 46),
      Ranges.create(57, 13, 57, 27)
    );

    checkContent(
      diagnostics.get(5),
      Ranges.create(84, 5, 85, 46),
      Ranges.create(87, 8, 87, 26)
    );

  }

  private void checkContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    Range relatedLocationRange
  ) {
    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);
    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(1);

    DiagnosticRelatedInformation relatedInformation = relatedInformationList.get(0);
    Assertions.assertThat(relatedInformation.getLocation().getRange()).isEqualTo(relatedLocationRange);

  }
}
