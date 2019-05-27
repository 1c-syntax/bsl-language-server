package org.github._1c_syntax.bsl.languageserver.providers;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.FoldingRange;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

class FoldingRangeProviderTest {

  @Test
  void testFoldingRange() throws IOException {

    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/providers/foldingRange.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    List<FoldingRange> foldingRanges = FoldingRangeProvider.getFoldingRange(documentContext);

    assertThat(foldingRanges).hasSize(9);

    // regions
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals("region"))
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 0 && foldingRange.getEndLine() == 23)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 2 && foldingRange.getEndLine() == 16)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 4 && foldingRange.getEndLine() == 14)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 8 && foldingRange.getEndLine() == 12)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 9 && foldingRange.getEndLine() == 11)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 20 && foldingRange.getEndLine() == 21)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 25 && foldingRange.getEndLine() == 26)
      ;


    // comments
    assertThat(foldingRanges)
      .filteredOn(foldingRange -> foldingRange.getKind().equals("comment"))
      .hasSize(2)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 6 && foldingRange.getEndLine() == 7)
      .anyMatch(foldingRange -> foldingRange.getStartLine() == 18 && foldingRange.getEndLine() == 19)
      ;

  }
}