/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorReachability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Границы условий и содержимое доказанно недостижимых веток, без построения CFG. */
final class PreprocessorDeadCode {
  private static final Result EMPTY = new Result(List.of(), List.of(), true);
  private final DocumentContext documentContext;
  private final CompilationContextResolver compilationContextResolver;
  private final List<Range> preprocessorRanges = new ArrayList<>();
  private final List<Range> unreachablePreprocessorRanges = new ArrayList<>();

  /** Служебные границы веток, диапазоны неиспользуемого текста и корректность структуры. */
  record Result(List<Range> branches, List<Range> unreachable, boolean wellFormed) { }

  private PreprocessorDeadCode(DocumentContext document, CompilationContextResolver contexts) {
    documentContext = document;
    compilationContextResolver = contexts;
  }

  static Result collect(DocumentContext document, CompilationContextResolver contexts, BSLParser.FileContext ast) {
    var nodes = Trees.findAllRuleNodes(ast, BSLParser.RULE_preprocessor);
    return nodes.isEmpty() ? EMPTY : new PreprocessorDeadCode(document, contexts).collect(nodes);
  }

  private Result collect(Iterable<? extends ParseTree> nodes) {
    var reachability = new PreprocessorReachability(documentContext.getFileType());
    Deque<BSLParser.PreprocessorContext> previous = new ArrayDeque<>();
    for (var tree : nodes) {
      var node = (BSLParser.PreprocessorContext) tree;
      if (node.preproc_if() != null) {
        previous.push(node);
      } else if (node.preproc_elsif() != null || node.preproc_else() != null || node.preproc_endif() != null) {
        savePreprocessorRange(previous, node, reachability);
        if (node.preproc_endif() == null) {
          previous.push(node);
        }
      } else {
        continue;
      }
      reachability.enter(node);
    }
    return new Result(List.copyOf(preprocessorRanges), List.copyOf(unreachablePreprocessorRanges),
      reachability.isWellFormed());
  }

  private void savePreprocessorRange(Deque<BSLParser.PreprocessorContext> nodes,
                                    BSLParser.PreprocessorContext node, PreprocessorReachability reachability) {
    if (!nodes.isEmpty()) {
      var previous = nodes.pop();
      preprocessorRanges.add(
        Ranges.create(
          previous.getStop().getLine(),
          previous.getStop().getCharPositionInLine() + previous.getStop().getText().length() + 1,
          node.getStart().getLine(),
          node.getStart().getCharPositionInLine() - 1));
      if (reachability.isUnreachable(compilationContextResolver.possibleContexts(documentContext, previous)
        .orElse(ExecutionContext.ALL))) {
        addUnreachableBranch(previous, node);
      }
    }
  }

  /**
   * Выделить содержимое ветки, включая комментарии и вложенные области.
   * Пустая ветка или ветка только с комментариями/директивами не содержит недостижимого кода.
   */
  private void addUnreachableBranch(BSLParser.PreprocessorContext opening, BSLParser.PreprocessorContext closing) {
    var tokens = documentContext.getTokens();
    var first = -1;
    var last = -1;
    var hasCode = false;
    for (var i = opening.getStop().getTokenIndex() + 1; i < closing.getStart().getTokenIndex(); i++) {
      var token = tokens.get(i);
      if (token.getLine() <= opening.getStop().getLine() || token.getText().isBlank()) {
        continue;
      }
      if (first < 0) {
        first = i;
      }
      last = i;
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != BSLLexer.HASH
        && !BSLLexer.VOCABULARY.getSymbolicName(token.getType()).startsWith("PREPROC_")) {
        hasCode = true;
      }
    }
    if (hasCode) {
      unreachablePreprocessorRanges.add(Ranges.create(tokens.get(first), tokens.get(last)));
    }
  }
}
