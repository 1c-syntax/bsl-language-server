package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
class QueryComputerDateTimeTest {

  @Test
  void testDateTimeInQuery() {
    // given
    DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/QueryComputerDateTimeTest.bsl");

    // when/then - should not throw StringIndexOutOfBoundsException
    assertThatNoException().isThrownBy(documentContext::getQueries);
  }
}
