package org.github._1c_syntax.bsl.languageserver.recognizer;

public class EndWithDetector extends AbstractDetector {

  private final char[] endOfLines;

  public EndWithDetector(double probability, char endOfLines) {
    super(probability);
    this.endOfLines = new char[]{endOfLines};
  }

  public EndWithDetector(double probability, char[] endOfLines) {
    super(probability);
    this.endOfLines = endOfLines;
  }

  @Override
  int scan(String line) {
    for (int index = line.length() - 1; index >= 0; index--) {
      char character = line.charAt(index);

      for (char endOfLine : endOfLines) {
        if (character == endOfLine) {
          return 1;
        }
      }

      if (!Character.isWhitespace(character)) {
        return 0;
      }
    }

    return 0;
  }

}
