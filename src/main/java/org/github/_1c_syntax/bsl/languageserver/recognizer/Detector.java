package org.github._1c_syntax.bsl.languageserver.recognizer;

public abstract class Detector {
  private final double probability;

  public Detector(double probability) {
    if (probability < 0 || probability > 1) {
      throw new IllegalArgumentException("probability should be between [0 .. 1]");
    }
    this.probability = probability;
  }

  abstract int scan(String line);

  final double detect(String line) {
    int matchers = scan(line);
    if (matchers == 0) {
      return 0;
    }
    return 1 - Math.pow(1 - probability, matchers);
  }
}
