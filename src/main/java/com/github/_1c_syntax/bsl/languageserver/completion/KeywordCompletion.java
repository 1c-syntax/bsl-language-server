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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.types.MultiName;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Отбор настоящих ключевых слов по языку проекта и позиции вставки.
 * <p>
 * Канонический словарь языка определяет набор конструкций независимо от обозначений
 * параметров и частей схемы из справки. Из него же берутся написания:
 * например, для «По» предлагается To, даже если справка называет его By.
 * <p>
 * Контекст вычисляется по токенам до начала заменяемого слова, без повторного разбора
 * документа. Комментарии, строки и директивы не создают ложных циклов; незакрытые блоки
 * сохраняют контекст во время ввода. Состояние ограничено одним запросом completion.
 */
public final class KeywordCompletion {
  private record Word(int type, MultiName name) { }

  private static final List<Word> WORDS = List.of(
    new Word(BSLLexer.IF_KEYWORD, Keywords.IF),
    new Word(BSLLexer.THEN_KEYWORD, Keywords.THEN),
    new Word(BSLLexer.ELSIF_KEYWORD, Keywords.ELSIF),
    new Word(BSLLexer.ELSE_KEYWORD, Keywords.ELSE),
    new Word(BSLLexer.ENDIF_KEYWORD, Keywords.ENDIF),
    new Word(BSLLexer.FOR_KEYWORD, Keywords.FOR),
    new Word(BSLLexer.EACH_KEYWORD, Keywords.EACH),
    new Word(BSLLexer.IN_KEYWORD, Keywords.IN),
    new Word(BSLLexer.TO_KEYWORD, Keywords.TO),
    new Word(BSLLexer.WHILE_KEYWORD, Keywords.WHILE),
    new Word(BSLLexer.DO_KEYWORD, Keywords.DO),
    new Word(BSLLexer.ENDDO_KEYWORD, Keywords.END_DO),
    new Word(BSLLexer.BREAK_KEYWORD, Keywords.BREAK),
    new Word(BSLLexer.CONTINUE_KEYWORD, Keywords.CONTINUE),
    new Word(BSLLexer.PROCEDURE_KEYWORD, Keywords.PROCEDURE),
    new Word(BSLLexer.ENDPROCEDURE_KEYWORD, Keywords.END_PROCEDURE),
    new Word(BSLLexer.FUNCTION_KEYWORD, Keywords.FUNCTION),
    new Word(BSLLexer.ENDFUNCTION_KEYWORD, Keywords.END_FUNCTION),
    new Word(BSLLexer.EXPORT_KEYWORD, Keywords.EXPORT),
    new Word(BSLLexer.VAL_KEYWORD, Keywords.VAL),
    new Word(BSLLexer.VAR_KEYWORD, Keywords.VAR),
    new Word(BSLLexer.TRY_KEYWORD, Keywords.TRY),
    new Word(BSLLexer.EXCEPT_KEYWORD, Keywords.EXCEPT),
    new Word(BSLLexer.ENDTRY_KEYWORD, Keywords.END_TRY),
    new Word(BSLLexer.RAISE_KEYWORD, Keywords.RAISE),
    new Word(BSLLexer.RETURN_KEYWORD, Keywords.RETURN),
    new Word(BSLLexer.GOTO_KEYWORD, Keywords.GOTO),
    new Word(BSLLexer.EXECUTE_KEYWORD, Keywords.EXECUTE),
    new Word(BSLLexer.ADDHANDLER_KEYWORD, Keywords.ADD_HANDLER),
    new Word(BSLLexer.REMOVEHANDLER_KEYWORD, Keywords.REMOVE_HANDLER),
    new Word(BSLLexer.NEW_KEYWORD, Keywords.NEW),
    new Word(BSLLexer.ASYNC_KEYWORD, Keywords.ASYNC),
    new Word(BSLLexer.AWAIT_KEYWORD, Keywords.AWAIT),
    new Word(BSLLexer.AND_KEYWORD, Keywords.AND),
    new Word(BSLLexer.OR_KEYWORD, Keywords.OR),
    new Word(BSLLexer.NOT_KEYWORD, Keywords.NOT),
    new Word(BSLLexer.TRUE, Keywords.TRUE),
    new Word(BSLLexer.FALSE, Keywords.FALSE),
    new Word(BSLLexer.UNDEFINED, Keywords.UNDEFINED),
    new Word(BSLLexer.NULL, MultiName.create("Null", "Null"))
  );

  private KeywordCompletion() {
  }

  /**
   * Выбрать ключевые слова для позиции вставки.
   *
   * @param document текущий документ, определяющий язык исходного кода.
   * @param wordStart начало заменяемого префикса: само дописываемое слово не меняет контекст.
   * @return канонические имена без дублей, применимые в этой позиции.
   */
  public static List<String> suggest(DocumentContext document, Position wordStart) {
    var scope = new Scope();
    for (var token : document.getTokens()) {
      if (!startsBefore(token, wordStart)) {
        break;
      }
      if (isNonCodeAt(token, wordStart)) {
        return List.of();
      }
      if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
        scope.accept(token.getType());
      }
    }
    var language = document.getScriptVariantLanguage();
    return WORDS.stream().filter(word -> scope.allows(word.type()))
      .map(word -> language == Language.RU ? word.name().getRu() : word.name().getEn()).toList();
  }

  private static boolean startsBefore(Token token, Position position) {
    var line = token.getLine() - 1;
    return line < position.getLine()
      || line == position.getLine() && token.getCharPositionInLine() < position.getCharacter();
  }

  private static boolean isNonCodeAt(Token token, Position position) {
    if (token.getLine() - 1 != position.getLine()) {
      return false;
    }
    return switch (token.getType()) {
      case BSLLexer.LINE_COMMENT, BSLLexer.STRINGSTART, BSLLexer.STRINGPART,
           BSLLexer.HASH, BSLLexer.AMPERSAND -> true;
      case BSLLexer.STRING, BSLLexer.STRINGTAIL ->
        position.getCharacter() < token.getCharPositionInLine() + token.getText().length();
      default -> false;
    };
  }

  private enum Kind {IF, FOR, WHILE, TRY}

  /** Незавершённая конструкция; флаги описывают уже прочитанную часть её заголовка или веток. */
  private static final class Block {
    private final Kind kind;
    private boolean body;
    private boolean lastBranch;
    private boolean each;
    private boolean assigned;
    private boolean delimiter;

    private Block(Kind kind) {
      this.kind = kind;
      body = kind == Kind.TRY;
    }

    private boolean loop() {
      return kind == Kind.FOR || kind == Kind.WHILE;
    }
  }

  /** Границы блоков и объявления метода, восстановленные из уже существующих токенов документа. */
  private static final class Scope {
    private final Deque<Block> blocks = new ArrayDeque<>();
    private int previous = Token.INVALID_TYPE;
    private int method = Token.INVALID_TYPE;
    private int parametersDepth;
    private int expressionDepth;
    private boolean signatureComplete;
    private boolean async;
    private boolean canExportMethod;
    private boolean moduleVariable;

    private void accept(int type) {
      if (type == BSLLexer.PROCEDURE_KEYWORD || type == BSLLexer.FUNCTION_KEYWORD) {
        blocks.clear();
        method = type;
        async = previous == BSLLexer.ASYNC_KEYWORD;
        parametersDepth = 0;
        expressionDepth = 0;
        signatureComplete = false;
        canExportMethod = false;
        moduleVariable = false;
      } else if (type == BSLLexer.ENDPROCEDURE_KEYWORD || type == BSLLexer.ENDFUNCTION_KEYWORD) {
        blocks.clear();
        method = Token.INVALID_TYPE;
        expressionDepth = 0;
        canExportMethod = false;
      } else if (method != Token.INVALID_TYPE && !signatureComplete) {
        if (type == BSLLexer.LPAREN) {
          parametersDepth++;
        } else if (type == BSLLexer.RPAREN && parametersDepth > 0 && --parametersDepth == 0) {
          signatureComplete = true;
          canExportMethod = true;
        }
      } else {
        canExportMethod = false;
        acceptBody(type);
      }
      previous = type;
    }

    private void acceptBody(int type) {
      if (type == BSLLexer.LPAREN || type == BSLLexer.LBRACK) {
        expressionDepth++;
      } else if (type == BSLLexer.RPAREN || type == BSLLexer.RBRACK) {
        expressionDepth = Math.max(0, expressionDepth - 1);
      }
      if (type == BSLLexer.VAR_KEYWORD) {
        moduleVariable = method == Token.INVALID_TYPE && blocks.isEmpty();
      } else if (type != BSLLexer.IDENTIFIER && type != BSLLexer.COMMA && type != BSLLexer.EXPORT_KEYWORD) {
        moduleVariable = false;
      }
      var top = blocks.peek();
      switch (type) {
        case BSLLexer.IF_KEYWORD -> blocks.push(new Block(Kind.IF));
        case BSLLexer.FOR_KEYWORD -> blocks.push(new Block(Kind.FOR));
        case BSLLexer.WHILE_KEYWORD -> blocks.push(new Block(Kind.WHILE));
        case BSLLexer.TRY_KEYWORD -> blocks.push(new Block(Kind.TRY));
        case BSLLexer.ENDIF_KEYWORD -> close(block -> block.kind == Kind.IF);
        case BSLLexer.ENDDO_KEYWORD -> close(Block::loop);
        case BSLLexer.ENDTRY_KEYWORD -> close(block -> block.kind == Kind.TRY);
        case BSLLexer.THEN_KEYWORD -> {
          if (top != null && top.kind == Kind.IF) {
            top.body = true;
          }
        }
        case BSLLexer.DO_KEYWORD -> {
          if (top != null && top.loop()) {
            top.body = true;
          }
        }
        case BSLLexer.ELSIF_KEYWORD -> {
          if (top != null && top.kind == Kind.IF) {
            top.body = false;
          }
        }
        case BSLLexer.ELSE_KEYWORD, BSLLexer.EXCEPT_KEYWORD -> {
          if (top != null && (type == BSLLexer.ELSE_KEYWORD && top.kind == Kind.IF
              || type == BSLLexer.EXCEPT_KEYWORD && top.kind == Kind.TRY)) {
            top.lastBranch = true;
            top.body = true;
          }
        }
        case BSLLexer.EACH_KEYWORD -> {
          if (top != null && top.kind == Kind.FOR && !top.body) {
            top.each = true;
          }
        }
        case BSLLexer.ASSIGN -> {
          if (top != null && top.kind == Kind.FOR && !top.body) {
            top.assigned = true;
          }
        }
        case BSLLexer.TO_KEYWORD, BSLLexer.IN_KEYWORD -> {
          if (top != null && top.kind == Kind.FOR && !top.body) {
            top.delimiter = true;
          }
        }
        default -> {
          // Остальные токены не меняют границ конструкций.
        }
      }
    }

    /** Закрыть найденный блок вместе с незавершёнными вложенными при восстановлении после ошибки. */
    private void close(Predicate<Block> matches) {
      if (blocks.stream().anyMatch(matches)) {
        while (!matches.test(blocks.pop())) {
          // Вложенные незавершённые конструкции не переходят за явное закрытие внешней.
        }
      }
    }

    private boolean allows(int type) {
      if (method != Token.INVALID_TYPE && !signatureComplete) {
        return type == BSLLexer.VAL_KEYWORD
          ? parametersDepth == 1 && (previous == BSLLexer.LPAREN || previous == BSLLexer.COMMA)
          : previous == BSLLexer.ASSIGN && literal(type);
      }
      var top = blocks.peek();
      if (expressionDepth > 0 && !expressionWord(type)) {
        return false;
      }
      if (top != null && !top.body && !expressionWord(type)
          && type != BSLLexer.THEN_KEYWORD && type != BSLLexer.DO_KEYWORD
          && type != BSLLexer.TO_KEYWORD && type != BSLLexer.IN_KEYWORD && type != BSLLexer.EACH_KEYWORD) {
        return false;
      }
      return switch (type) {
        case BSLLexer.BREAK_KEYWORD, BSLLexer.CONTINUE_KEYWORD ->
          blocks.stream().anyMatch(block -> block.loop() && block.body);
        case BSLLexer.AWAIT_KEYWORD -> method != Token.INVALID_TYPE && async;
        case BSLLexer.PROCEDURE_KEYWORD, BSLLexer.FUNCTION_KEYWORD, BSLLexer.ASYNC_KEYWORD ->
          method == Token.INVALID_TYPE && blocks.isEmpty();
        case BSLLexer.ENDPROCEDURE_KEYWORD -> method == BSLLexer.PROCEDURE_KEYWORD && blocks.isEmpty();
        case BSLLexer.ENDFUNCTION_KEYWORD -> method == BSLLexer.FUNCTION_KEYWORD && blocks.isEmpty();
        case BSLLexer.VAL_KEYWORD -> false;
        case BSLLexer.EXPORT_KEYWORD -> canExportMethod || moduleVariable && previous == BSLLexer.IDENTIFIER;
        case BSLLexer.ELSE_KEYWORD, BSLLexer.ELSIF_KEYWORD ->
          top != null && top.kind == Kind.IF && top.body && !top.lastBranch;
        case BSLLexer.ENDIF_KEYWORD -> top != null && top.kind == Kind.IF && top.body;
        case BSLLexer.THEN_KEYWORD -> top != null && top.kind == Kind.IF && !top.body && expressionEnd();
        case BSLLexer.ENDDO_KEYWORD -> top != null && top.loop() && top.body;
        case BSLLexer.DO_KEYWORD -> top != null && top.loop() && !top.body && expressionEnd()
          && (top.kind == Kind.WHILE || top.delimiter);
        case BSLLexer.EACH_KEYWORD -> top != null && top.kind == Kind.FOR && !top.body
          && previous == BSLLexer.FOR_KEYWORD;
        case BSLLexer.IN_KEYWORD -> top != null && top.kind == Kind.FOR && !top.body && top.each
          && !top.delimiter && previous == BSLLexer.IDENTIFIER;
        case BSLLexer.TO_KEYWORD -> top != null && top.kind == Kind.FOR && !top.body && !top.each
          && top.assigned && !top.delimiter && expressionEnd();
        case BSLLexer.EXCEPT_KEYWORD -> top != null && top.kind == Kind.TRY && !top.lastBranch;
        case BSLLexer.ENDTRY_KEYWORD -> top != null && top.kind == Kind.TRY && top.lastBranch;
        default -> true;
      };
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
      return type == BSLLexer.TRUE || type == BSLLexer.FALSE || type == BSLLexer.UNDEFINED || type == BSLLexer.NULL;
    }

    private static boolean expressionWord(int type) {
      return literal(type) || type == BSLLexer.NEW_KEYWORD || type == BSLLexer.AWAIT_KEYWORD
        || type == BSLLexer.AND_KEYWORD || type == BSLLexer.OR_KEYWORD || type == BSLLexer.NOT_KEYWORD;
    }
  }
}
