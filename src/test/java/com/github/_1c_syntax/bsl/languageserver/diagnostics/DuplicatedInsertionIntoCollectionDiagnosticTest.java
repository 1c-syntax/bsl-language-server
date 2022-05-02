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

class DuplicatedInsertionIntoCollectionDiagnosticTest extends AbstractDiagnosticTest<DuplicatedInsertionIntoCollectionDiagnostic> {
  DuplicatedInsertionIntoCollectionDiagnosticTest() {
    super(DuplicatedInsertionIntoCollectionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    checkContent(
      diagnostics.get(0),
      Ranges.create(8, 4, 8, 34),
      Arrays.asList(
        Ranges.create(7, 4, 7, 34),
        Ranges.create(8, 4, 8, 34))
    );

    checkContent(
      diagnostics.get(1),
      Ranges.create(12, 4, 12, 35),
      Arrays.asList(
        Ranges.create(11, 4, 11, 35),
        Ranges.create(12, 4, 12, 35))
    );

    checkContent(
      diagnostics.get(2),
      Ranges.create(4, 4, 4, 34),
      Arrays.asList(
        Ranges.create(3, 4, 3, 34),
        Ranges.create(4, 4, 4, 34))
    );

    checkContent(
      diagnostics.get(3),
      Ranges.create(22, 8, 22, 38),
      Arrays.asList(
        Ranges.create(21, 8, 21, 38),
        Ranges.create(22, 8, 22, 38))
    );

    checkContent(
      diagnostics.get(4),
      Ranges.create(27, 8, 27, 55),
      Arrays.asList(
        Ranges.create(26, 8, 26, 55),
        Ranges.create(27, 8, 27, 55))
    );

    checkContent(
      diagnostics.get(5),
      Ranges.create(58, 12, 58, 76),
      Arrays.asList(
        Ranges.create(56, 12, 56, 87),
        Ranges.create(58, 12, 58, 76))
    );

    assertThat(diagnostics).hasSize(6);

  }

  // дубль следующих методов из кода FieldsFromJoinsWithoutIsNullDiagnosticTest
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
