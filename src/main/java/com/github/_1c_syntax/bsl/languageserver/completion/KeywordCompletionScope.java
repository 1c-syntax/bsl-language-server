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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayDeque;
import java.util.Deque;

/** Состояние метода и вложенных блоков по токенам перед вводимым словом; один экземпляр на запрос. */
final class KeywordCompletionScope {
  private final Deque<KeywordBlock> blocks = new ArrayDeque<>();
  private int previous;
  private int method;
  private int parametersDepth;
  private int expressionDepth;
  private boolean signatureComplete;
  private boolean async;
  private boolean canExportMethod;
  private boolean moduleVariable;

  KeywordCompletionScope() {
    previous = Token.INVALID_TYPE;
    method = Token.INVALID_TYPE;
  }

  void accept(int type) {
    if (type == BSLLexer.PROCEDURE_KEYWORD || type == BSLLexer.FUNCTION_KEYWORD) {
      startMethod(type);
    } else if (type == BSLLexer.ENDPROCEDURE_KEYWORD || type == BSLLexer.ENDFUNCTION_KEYWORD) {
      endMethod();
    } else if (inSignature()) {
      acceptSignature(type);
    } else {
      canExportMethod = false;
      acceptBody(type);
    }
    previous = type;
  }

  private void startMethod(int type) {
    blocks.clear();
    method = type;
    async = previous == BSLLexer.ASYNC_KEYWORD;
    parametersDepth = 0;
    expressionDepth = 0;
    signatureComplete = false;
    canExportMethod = false;
    moduleVariable = false;
  }

  private void endMethod() {
    blocks.clear();
    method = Token.INVALID_TYPE;
    expressionDepth = 0;
    canExportMethod = false;
  }

  private boolean inSignature() {
    return method != Token.INVALID_TYPE && !signatureComplete;
  }

  private void acceptSignature(int type) {
    if (type == BSLLexer.LPAREN) {
      parametersDepth++;
    } else if (type == BSLLexer.RPAREN && parametersDepth > 0) {
      parametersDepth--;
      if (parametersDepth == 0) {
        signatureComplete = true;
        canExportMethod = true;
      }
    } else {
      // Имена параметров и их значения не меняют глубину скобок.
    }
  }

  private void acceptBody(int type) {
    acceptExpressionDepth(type);
    acceptModuleVariable(type);
    switch (type) {
      case BSLLexer.IF_KEYWORD, BSLLexer.FOR_KEYWORD, BSLLexer.WHILE_KEYWORD, BSLLexer.TRY_KEYWORD
        -> blocks.push(KeywordBlock.open(type));
      case BSLLexer.ENDIF_KEYWORD, BSLLexer.ENDDO_KEYWORD, BSLLexer.ENDTRY_KEYWORD -> close(type);
      default -> updateBlock(type);
    }
  }

  private void acceptExpressionDepth(int type) {
    switch (type) {
      case BSLLexer.LPAREN, BSLLexer.LBRACK -> {
        expressionDepth++;
      }
      case BSLLexer.RPAREN, BSLLexer.RBRACK -> expressionDepth = Math.max(0, expressionDepth - 1);
      case BSLLexer.SEMICOLON -> expressionDepth = 0;
      default -> { /* токен не является скобкой */ }
    }
  }

  private void acceptModuleVariable(int type) {
    if (type == BSLLexer.VAR_KEYWORD) {
      moduleVariable = topLevel();
    } else if (!variableDeclarationToken(type)) {
      moduleVariable = false;
    } else {
      // Имя, запятая и Экспорт продолжают текущее объявление.
    }
  }

  private static boolean variableDeclarationToken(int type) {
    return switch (type) {
      case BSLLexer.IDENTIFIER, BSLLexer.COMMA, BSLLexer.EXPORT_KEYWORD -> true;
      default -> false;
    };
  }

  private void updateBlock(int type) {
    var top = blocks.peek();
    if (top != null) {
      top.accept(type);
    }
  }

  /** Явное закрытие внешнего блока удаляет и незавершённые вложенные конструкции. */
  private void close(int type) {
    if (blocks.stream().anyMatch(block -> block.closedBy(type))) {
      while (!blocks.pop().closedBy(type)) {
        // Восстановление после неполного ввода.
      }
    }
  }

  boolean allows(int type) {
    if (inSignature()) {
      return allowsParameter(type);
    }
    if (!allowsPosition(type)) {
      return false;
    }
    return switch (type) {
      case BSLLexer.BREAK_KEYWORD, BSLLexer.CONTINUE_KEYWORD -> blocks.stream().anyMatch(KeywordBlock::activeLoop);
      case BSLLexer.AWAIT_KEYWORD, BSLLexer.PROCEDURE_KEYWORD, BSLLexer.FUNCTION_KEYWORD,
           BSLLexer.ASYNC_KEYWORD, BSLLexer.ENDPROCEDURE_KEYWORD, BSLLexer.ENDFUNCTION_KEYWORD,
           BSLLexer.VAL_KEYWORD, BSLLexer.EXPORT_KEYWORD -> allowsMethodWord(type);
      case BSLLexer.ELSE_KEYWORD, BSLLexer.ELSIF_KEYWORD, BSLLexer.ENDIF_KEYWORD, BSLLexer.THEN_KEYWORD,
           BSLLexer.ENDDO_KEYWORD, BSLLexer.DO_KEYWORD, BSLLexer.EACH_KEYWORD, BSLLexer.IN_KEYWORD,
           BSLLexer.TO_KEYWORD, BSLLexer.EXCEPT_KEYWORD, BSLLexer.ENDTRY_KEYWORD -> allowsBlockWord(type);
      default -> true;
    };
  }

  private boolean allowsParameter(int type) {
    if (type == BSLLexer.VAL_KEYWORD) {
      return parametersDepth == 1 && (previous == BSLLexer.LPAREN || previous == BSLLexer.COMMA);
    }
    return previous == BSLLexer.ASSIGN && literal(type);
  }

  private boolean allowsPosition(int type) {
    if (expressionDepth > 0 && !expressionWord(type)) {
      return false;
    }
    var top = blocks.peek();
    return top == null || top.body() || expressionWord(type) || headerWord(type);
  }

  private boolean allowsBlockWord(int type) {
    var top = blocks.peek();
    return top != null && top.allows(type, previous, expressionEnd());
  }

  private boolean allowsMethodWord(int type) {
    return switch (type) {
      case BSLLexer.AWAIT_KEYWORD -> asyncMethod();
      case BSLLexer.PROCEDURE_KEYWORD, BSLLexer.FUNCTION_KEYWORD, BSLLexer.ASYNC_KEYWORD -> topLevel();
      case BSLLexer.ENDPROCEDURE_KEYWORD -> endsMethod(BSLLexer.PROCEDURE_KEYWORD);
      case BSLLexer.ENDFUNCTION_KEYWORD -> endsMethod(BSLLexer.FUNCTION_KEYWORD);
      case BSLLexer.VAL_KEYWORD -> false;
      case BSLLexer.EXPORT_KEYWORD -> canExport();
      default -> throw new IllegalArgumentException("Not a method keyword: " + type);
    };
  }

  private boolean asyncMethod() {
    return method != Token.INVALID_TYPE && async;
  }
  private boolean topLevel() {
    return method == Token.INVALID_TYPE && blocks.isEmpty();
  }
  private boolean endsMethod(int expected) {
    return method == expected && blocks.isEmpty();
  }
  private boolean canExport() {
    return canExportMethod || (moduleVariable && previous == BSLLexer.IDENTIFIER);
  }

  private boolean expressionEnd() {
    return switch (previous) {
      case BSLLexer.IDENTIFIER, BSLLexer.DECIMAL, BSLLexer.FLOAT, BSLLexer.DATETIME,
           BSLLexer.STRING, BSLLexer.STRINGTAIL, BSLLexer.RPAREN, BSLLexer.RBRACK,
           BSLLexer.TRUE, BSLLexer.FALSE, BSLLexer.UNDEFINED, BSLLexer.NULL -> true;
      default -> false;
    };
  }

  private static boolean literal(int type) {
    return switch (type) {
      case BSLLexer.TRUE, BSLLexer.FALSE, BSLLexer.UNDEFINED, BSLLexer.NULL -> true;
      default -> false;
    };
  }

  private static boolean expressionWord(int type) {
    if (literal(type)) {
      return true;
    }
    return switch (type) {
      case BSLLexer.NEW_KEYWORD, BSLLexer.AWAIT_KEYWORD, BSLLexer.AND_KEYWORD,
           BSLLexer.OR_KEYWORD, BSLLexer.NOT_KEYWORD -> true;
      default -> false;
    };
  }

  private static boolean headerWord(int type) {
    return switch (type) {
      case BSLLexer.THEN_KEYWORD, BSLLexer.DO_KEYWORD, BSLLexer.TO_KEYWORD,
           BSLLexer.IN_KEYWORD, BSLLexer.EACH_KEYWORD -> true;
      default -> false;
    };
  }
}
