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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

/** Поиск тела по упорядоченным узлам AST без создания списка всех методов и временных диапазонов. */
final class CodeBlockLookup {
  private CodeBlockLookup() {
  }

  static BSLParser.@Nullable CodeBlockContext at(BSLParser.FileContext ast, Position position) {
    var subs = ast.subs();
    if (subs != null) {
      var low = 0;
      var high = subs.getChildCount();
      while (low < high) {
        var middle = (low + high) >>> 1;
        if (endsBefore(subs.getChild(middle), position)) {
          low = middle + 1;
        } else {
          high = middle;
        }
      }
      for (var i = low; i < subs.getChildCount(); i++) {
        var child = subs.getChild(i);
        var start = startOf(child);
        if (start.getLine() - 1 > position.getLine()
          || (start.getLine() - 1 == position.getLine() && start.getCharPositionInLine() > position.getCharacter())) {
          break;
        }
        if (child instanceof BSLParser.SubContext sub && contains(sub, position)) {
          var procedure = sub.procedure();
          if (procedure != null) {
            return procedure.subCodeBlock().codeBlock();
          }
          var function = sub.function();
          return function == null ? null : function.subCodeBlock().codeBlock();
        }
      }
    }
    var before = ast.fileCodeBlockBeforeSub();
    var covering = covering(before == null ? null : before.codeBlock(), position);
    if (covering != null) {
      return covering;
    }
    var after = ast.fileCodeBlock();
    return covering(after == null ? null : after.codeBlock(), position);
  }

  private static Token startOf(ParseTree node) {
    return node instanceof ParserRuleContext rule ? rule.getStart() : ((TerminalNode) node).getSymbol();
  }

  private static boolean endsBefore(ParseTree node, Position position) {
    var stop = node instanceof ParserRuleContext rule ? rule.getStop() : ((TerminalNode) node).getSymbol();
    if (stop == null) {
      stop = startOf(node);
    }
    var line = stop.getLine() - 1;
    if (line != position.getLine()) {
      return line < position.getLine();
    }
    var end = stop.getCharPositionInLine();
    if (stop.getType() != Token.EOF) {
      end += stop.getText().length();
    }
    return end < position.getCharacter();
  }

  private static boolean contains(ParserRuleContext node, Position position) {
    var start = node.getStart();
    var stop = node.getStop() == null ? start : node.getStop();
    var end = stop.getCharPositionInLine();
    if (stop.getType() != Token.EOF) {
      end += stop.getText().length();
    }
    return Ranges.containsPosition(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, end, position);
  }

  private static BSLParser.@Nullable CodeBlockContext covering(
    BSLParser.@Nullable CodeBlockContext block, Position position
  ) {
    return block != null && Ranges.containsPosition(Ranges.create(block), position) ? block : null;
  }
}
