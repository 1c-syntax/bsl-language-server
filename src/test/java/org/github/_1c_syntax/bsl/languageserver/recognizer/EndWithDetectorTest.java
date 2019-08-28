package org.github._1c_syntax.bsl.languageserver.recognizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class EndWithDetectorTest {

  @Test
  void scan() {
    EndWithDetector detector = new EndWithDetector(1, ';');

    assertThat(detector.detect("Какой-то текст с ; в середине")).isEqualTo(0);
    assertThat(detector.detect("Какой-то текст заказнчивающийся на ;")).isEqualTo(1);
  }
}