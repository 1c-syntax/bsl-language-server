package org.github._1c_syntax.bsl.languageserver.recognizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelCaseDetectorTest {

  @Test
  void runTest() {
    CamelCaseDetector detector = new CamelCaseDetector(1);

    assertThat(detector.detect("Процедура")).isEqualTo(0);
    assertThat(detector.detect("ДанныеДокумента")).isEqualTo(1);
    assertThat(detector.detect("ДанныеДокументаДляСертификации")).isEqualTo(1);
    assertThat(detector.detect("данные ДокументаДляСертификации")).isEqualTo(1);
  }

}
