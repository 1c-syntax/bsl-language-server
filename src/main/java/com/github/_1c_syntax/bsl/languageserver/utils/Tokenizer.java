package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.UnicodeBOMInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.antlr.v4.runtime.Token.EOF;

public class Tokenizer {

  private String content;
  private Lazy<CommonTokenStream> tokenStream = new Lazy<>(this::computeTokenStream);
  private Lazy<List<Token>> tokens = new Lazy<>(this::computeTokens);

  public Tokenizer(String content) {
    this.content = content;
  }

  public List<Token> getTokens() {
    final List<Token> tokensUnboxed = tokens.getOrCompute();
    return new ArrayList<>(tokensUnboxed);
  }

  private List<Token> computeTokens() {
    List<Token> tokensTemp = new ArrayList<>(getTokenStream().getTokens());

    Token lastToken = tokensTemp.get(tokensTemp.size() - 1);
    if (lastToken.getType() == EOF) {
      tokensTemp.remove(tokensTemp.size() - 1);
    }

    return tokensTemp;
  }

  private CommonTokenStream getTokenStream() {
    final CommonTokenStream tokenStreamUnboxed = tokenStream.getOrCompute();
    tokenStreamUnboxed.seek(0);
    return tokenStreamUnboxed;
  }

  private CommonTokenStream computeTokenStream() {
    requireNonNull(content);

    CharStream input;

    try (InputStream inputStream = IOUtils.toInputStream(content, StandardCharsets.UTF_8);
         UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(inputStream)
    ) {

      ubis.skipBOM();

      input = CharStreams.fromStream(ubis, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BSLLexer lexer = new BSLLexer(input);

    lexer.setInputStream(input);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);

    CommonTokenStream tempTokenStream = new CommonTokenStream(lexer);
    tempTokenStream.fill();

    return tempTokenStream;
  }

}
