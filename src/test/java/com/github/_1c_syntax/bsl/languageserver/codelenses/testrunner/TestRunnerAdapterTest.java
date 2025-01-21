package com.github._1c_syntax.bsl.languageserver.codelenses.testrunner;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TestRunnerAdapterTest {

  @Autowired
  private TestRunnerAdapter testRunnerAdapter;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void whenComputeTestsByLanguageServer_thenContainsTests() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("src/test/resources/codelenses/testrunner/TestRunnerAdapter.os");
    configuration.getCodeLensOptions().getTestRunnerAdapterOptions().setGetTestsByTestRunner(false);

    // when
    var testIds = testRunnerAdapter.getTestIds(documentContext);

    // then
    assertThat(testIds).hasSize(1);
    assertThat(testIds.get(0)).isEqualTo("Тест1");
  }
}