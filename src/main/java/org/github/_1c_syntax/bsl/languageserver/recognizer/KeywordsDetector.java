package org.github._1c_syntax.bsl.languageserver.recognizer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class KeywordsDetector extends Detector {

  private final List<String> keywords;
  private boolean toUpperCase = false;

  private KeywordsDetector(double probability, String... keywords) {
    super(probability);
    this.keywords = Arrays.asList(keywords);
  }

  public KeywordsDetector(double probability, boolean toUpperCase, String... keywords) {
    this(probability, keywords);
    this.toUpperCase = toUpperCase;
  }

  @Override
  public int scan(String line) {
    int matchers = 0;
    if (toUpperCase) {
      line = line.toUpperCase(Locale.getDefault());
    }
    StringTokenizer tokenizer = new StringTokenizer(line, " \t(),{}");
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      if (keywords.contains(word)) {
        matchers++;
      }
    }
    return matchers;
  }

}
