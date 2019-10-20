package com.github._1c_syntax.bsl.languageserver.utils;

import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerTest
{
  @Test
  void checkTokenizer()
  {
    Tokenizer tokenizer = new Tokenizer("Если Условие() Тогда");
    final List<Token> tokens = tokenizer.getTokens();
    assertThat(tokens).hasSize(7);
  }
}