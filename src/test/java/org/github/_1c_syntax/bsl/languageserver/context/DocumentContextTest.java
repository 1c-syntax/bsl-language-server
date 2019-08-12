package org.github._1c_syntax.bsl.languageserver.context;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContextTest {

  @Test
  void testClearASTData() throws IOException {
    // given
    DocumentContext documentContext = getDocumentContext();

    // when
    documentContext.clearASTData();

    // then
    assertThat(documentContext).extracting(DocumentContext::getAst).isNull();

  }


  public DocumentContext getDocumentContext() throws IOException {

    // given
    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/context/DocumentContextTest.bsl"),
      StandardCharsets.UTF_8
    );

    return new DocumentContext("fake-uri.bsl", fileContent);
  }
}