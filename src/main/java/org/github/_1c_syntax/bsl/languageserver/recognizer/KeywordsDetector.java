package org.github._1c_syntax.bsl.languageserver.recognizer;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class KeywordsDetector extends Detector {

  private final List<String> keywords;
  public KeywordsDetector(double probability, String... keywords) {
    super(probability);
    this.keywords = Arrays.asList(keywords);
  }

  @Override
  public int scan(String line) {
    int matchers = 0;
    StringTokenizer tokenizer = new StringTokenizer(line, " \t\n");
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      if (keywords.contains(word)) {
        matchers++;
      }
    }
    return matchers;
  }

}
