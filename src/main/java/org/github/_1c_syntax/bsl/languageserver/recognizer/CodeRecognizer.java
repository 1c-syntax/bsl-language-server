package org.github._1c_syntax.bsl.languageserver.recognizer;

public class CodeRecognizer {
  private final LanguageFootprint language;
  private final double threshold;

  public CodeRecognizer(double threshold, LanguageFootprint language) {
    this.language = language;
    this.threshold = threshold;
  }

  private double recognition(String line) {
    double probability = 0;
    for (Detector pattern : language.getDetectors()) {
      probability = 1 - (1 - probability) * (1 - pattern.detect(line));
    }
    return probability;
  }

  public final boolean meetsCondition(String line) {
    return recognition(line) - threshold > 0;
  }
}
