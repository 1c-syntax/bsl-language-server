package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Optional;
import java.util.stream.Stream;

public enum Annotation {
  BEFORE(BSLParser.ANNOTATION_BEFORE_SYMBOL),
  AFTER(BSLParser.ANNOTATION_AFTER_SYMBOL),
  AROUND(BSLParser.ANNOTATION_AROUND_SYMBOL),
  CHANGEANDVALIDATE(BSLParser.ANNOTATION_CHANGEANDVALIDATE_SYMBOL);

  private final int tokenType;

  Annotation(int tokenType) {
    this.tokenType = tokenType;
  }

  public int getTokenType() {
    return tokenType;
  }

  public static Optional<Annotation> of(int tokenType) {
    return Stream.of(values())
      .filter(annotation -> annotation.getTokenType() == tokenType)
      .findAny();
  }
}
