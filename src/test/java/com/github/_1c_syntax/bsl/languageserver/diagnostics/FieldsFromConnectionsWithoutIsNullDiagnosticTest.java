package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FieldsFromConnectionsWithoutIsNullDiagnosticTest extends AbstractDiagnosticTest<FieldsFromConnectionsWithoutIsNullDiagnostic> {
  FieldsFromConnectionsWithoutIsNullDiagnosticTest() {
    super(FieldsFromConnectionsWithoutIsNullDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

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

    checkContent(
      diagnostics.get(6),
      Ranges.create(104, 5, 105, 46),
      Arrays.asList(
        Ranges.create(99, 5, 99, 19),
        Ranges.create(98, 13, 98, 31),
        Ranges.create(100, 5, 100, 28))
      );

    checkContent(
      diagnostics.get(7),
      Ranges.create(154, 8, 155, 50),
      Ranges.create(151, 8, 151, 28)
      );

    assertThat(diagnostics).hasSize(8);

  }

  private void checkContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    Range relatedLocationRange
  ) {
    checkContent(diagnostic, diagnosticRange, Collections.singletonList(relatedLocationRange));
  }

  private void checkContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    List<Range> relatedLocationRanges
  ) {
    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);
    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(relatedLocationRanges.size());

    for (int i = 0; i < relatedLocationRanges.size(); i++) {
      var relatedInformation = relatedInformationList.get(i);
      var relatedLocationRange = relatedLocationRanges.get(i);
      Assertions.assertThat(relatedInformation.getLocation().getRange()).isEqualTo(relatedLocationRange);
    }
  }
}
