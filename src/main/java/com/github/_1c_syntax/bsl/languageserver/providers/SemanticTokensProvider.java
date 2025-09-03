/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  private final SemanticTokensLegend legend;

  public SemanticTokens getSemanticTokensFull(DocumentContext documentContext, SemanticTokensParams params) {
    List<TokenEntry> entries = new ArrayList<>();

    // 1) Symbols: methods/functions, variables, parameters
    var symbolTree = documentContext.getSymbolTree();
    for (MethodSymbol method : symbolTree.getMethods()) {
      addRange(entries, method.getSubNameRange(), method.isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Method);
      for (ParameterDefinition p : method.getParameters()) {
        addRange(entries, p.getRange(), SemanticTokenTypes.Parameter);
      }
    }
    for (VariableSymbol varSym : symbolTree.getVariables()) {
      addRange(entries, varSym.getVariableNameRange(), SemanticTokenTypes.Variable);
    }

    // 2) Comments (lexer type LINE_COMMENT)
    for (Token t : documentContext.getComments()) {
      addTokenLike(entries, t.getLine(), t.getCharPositionInLine(), t.getText(), SemanticTokenTypes.Comment);
    }

    // 3) Lexical tokens on default channel: strings, numbers, preproc/annotations, keywords
    for (Token t : documentContext.getTokensFromDefaultChannel()) {
      final int type = t.getType();
      final String text = Objects.toString(t.getText(), "");
      if (text.isEmpty()) {
        continue;
      }

      // strings
      if (STRING_TYPES.contains(type)) {
        addTokenLike(entries, t.getLine(), t.getCharPositionInLine(), text, SemanticTokenTypes.String);
        continue;
      }

      // numbers
      if (NUMBER_TYPES.contains(type)) {
        addToken(entries, t.getLine() - 1, t.getCharPositionInLine(), text.length(), SemanticTokenTypes.Number);
        continue;
      }

      // preprocessor/macro: any token whose symbolic name starts with PREPROC_ or is HASH
      String sym = BSLLexer.VOCABULARY.getSymbolicName(type);
      if ("HASH".equals(sym) || (sym != null && sym.startsWith("PREPROC_"))) {
        addToken(entries, t.getLine() - 1, t.getCharPositionInLine(), text.length(), SemanticTokenTypes.Macro);
        continue;
      }

      // annotations
      if (ANNOTATION_TYPES.contains(type)) {
        addToken(entries, t.getLine() - 1, t.getCharPositionInLine(), text.length(), SemanticTokenTypes.Decorator);
        continue;
      }

      // keywords (by symbolic name suffix). Exclude PREPROC_* as they are handled above
      if (sym != null && sym.endsWith("_KEYWORD")) {
        addToken(entries, t.getLine() - 1, t.getCharPositionInLine(), text.length(), SemanticTokenTypes.Keyword);
      }
    }

    // 4) Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);
    return new SemanticTokens(data);
  }

  private void addRange(List<TokenEntry> out, Range range, String type) {
    Position s = range.getStart();
    Position e = range.getEnd();
    int line = s.getLine();
    int length = Math.max(0, e.getCharacter() - s.getCharacter());
    if (length > 0) {
      addToken(out, line, s.getCharacter(), length, type);
    }
  }

  private void addTokenLike(List<TokenEntry> out, int antlrStartLine1, int startChar, String text, String type) {
    // Split by newlines to keep single-line semantic tokens
    String[] parts = text.split("\r?\n|\r", -1);
    int line = antlrStartLine1 - 1; // LSP lines are 0-based
    int col = startChar;
    for (String part : parts) {
      if (!part.isEmpty()) {
        addToken(out, line, col, part.length(), type);
      }
      // Subsequent lines start at column 0
      line++;
      col = 0;
    }
  }

  private void addToken(List<TokenEntry> out, int line, int start, int length, String type) {
    int typeIdx = legend.getTokenTypes().indexOf(type);
    if (typeIdx < 0) {
      return; // type not announced in legend
    }
    out.add(new TokenEntry(line, start, length, typeIdx, 0));
  }

  private List<Integer> toDeltaEncoded(List<TokenEntry> entries) {
    // de-dup and sort
    Set<TokenEntry> uniq = new HashSet<>(entries);
    List<TokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(TokenEntry::line)
      .thenComparingInt(TokenEntry::start));

    List<Integer> data = new ArrayList<>(sorted.size() * 5);
    int prevLine = 0;
    int prevChar = 0;
    boolean first = true;

    for (TokenEntry te : sorted) {
      int deltaLine = first ? te.line : te.line - prevLine;
      int deltaStart = first ? te.start : te.start - (deltaLine == 0 ? prevChar : 0);
      data.add(deltaLine);
      data.add(deltaStart);
      data.add(te.length);
      data.add(te.type);
      data.add(te.modifiers);

      prevLine = te.line;
      prevChar = te.start;
      first = false;
    }
    return data;
  }

  private record TokenEntry(int line, int start, int length, int type, int modifiers) { }

  private static final Set<Integer> NUMBER_TYPES = Set.of(
    BSLLexer.DECIMAL,
    BSLLexer.FLOAT
  );

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL
  );

  private static final Set<Integer> ANNOTATION_TYPES = Set.of(
    BSLLexer.ANNOTATION_CUSTOM_SYMBOL,
    BSLLexer.AMPERSAND
  );
}
