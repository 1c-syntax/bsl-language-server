package org.github._1c_syntax.bsl.languageserver.recognizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KeywordsDetectorTest {

  @Test
  void runTest() {
    KeywordsDetector detector = new KeywordsDetector(0.5, "Если", "Тогда", "ИЛИ");
    String testedText = "Если НЕПродолжатьВыполнение() Тогда Возврат; КонецЕсли;";

    assertThat(detector.detect(testedText)).isEqualTo(0.75);
    assertThat(detector.detect("или")).isEqualTo(0);
    assertThat(detector.detect("ИЛИ")).isEqualTo(0.5);
  }
}
