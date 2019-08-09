package org.github._1c_syntax.bsl.languageserver.context.computer;

import org.apache.commons.io.FileUtils;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveComplexityComputerTest {

  @Test
  void compute() throws IOException {

    // given
    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/context/computer/CognitiveComplexityComputerTest.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    // when
    Computer<CognitiveComplexityComputer.Data> cognitiveComplexityComputer =
      new CognitiveComplexityComputer(documentContext);
    CognitiveComplexityComputer.Data data = cognitiveComplexityComputer.compute();

    //then
    MethodSymbol firstMethod = documentContext.getMethods().get(0);
    Integer firstMethodComplexity = data.getMethodsComplexity().get(firstMethod);
    assertThat(firstMethodComplexity).isEqualTo(19);
  }
}