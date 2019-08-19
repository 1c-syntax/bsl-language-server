package org.github._1c_syntax.bsl.languageserver.recognizer;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class ContainsDetector extends Detector {

  private final List<String> searchWords;

  public ContainsDetector(double probability, String... searchWords) {
    super(probability);
    this.searchWords = Arrays.asList(searchWords);
  }

  @Override
  public int scan(String line) {
    String lineWithoutWhitespaces = StringUtils.deleteWhitespace(line);
    int matchers = 0;
    for (String str : searchWords) {
      matchers += StringUtils.countMatches(lineWithoutWhitespaces, str);
    }
    return matchers;
  }

}
