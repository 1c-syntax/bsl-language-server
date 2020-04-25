package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Optional;
import java.util.stream.Stream;

public enum CompilerDirective {
  AT_SERVER_NO_CONTEXT(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL),
  AT_CLIENT_AT_SERVER_NO_CONTEXT(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL),
  AT_CLIENT_AT_SERVER(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL),
  AT_CLIENT(BSLParser.ANNOTATION_ATCLIENT_SYMBOL),
  AT_SERVER(BSLParser.ANNOTATION_ATSERVER_SYMBOL);

  private final int tokenType;

  CompilerDirective(int tokenType) {
    this.tokenType = tokenType;
  }

  public int getTokenType() {
    return tokenType;
  }

  public static Optional<CompilerDirective> of(int tokenType){
    return Stream.of(values())
      .filter(compilerDirective -> compilerDirective.getTokenType() == tokenType)
      .findAny();
  }
}
