package org.github._1c_syntax.bsl.languageserver.recognizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainsDetectorTest {

  @Test
  void runTest() {
    ContainsDetector detector = new ContainsDetector(1, "КонецЕсли;", "КонецФункции", "КонецПроцедуры");

    assertThat(detector.detect("Процедура Какой-то текст")).isEqualTo(0);
    assertThat(detector.detect("Какой-то текст КонецЕсли")).isEqualTo(0);
    assertThat(detector.detect("КонецФункции Какой-то текст")).isEqualTo(1);
    assertThat(detector.detect("Какой-то текст КонецЕсли;")).isEqualTo(1);
    assertThat(detector.detect("Какой-то текст КонецПроцедуры")).isEqualTo(1);
  }

}
