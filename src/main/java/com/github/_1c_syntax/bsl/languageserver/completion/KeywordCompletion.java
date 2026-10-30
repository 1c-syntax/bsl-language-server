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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.types.MultiName;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;

import java.util.List;

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
    var scope = new KeywordCompletionScope();
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
    return WORDS.stream().filter(word -> supported(word.type(), document.getFileType()))
      .filter(word -> scope.allows(word.type()))
      .map(word -> language == Language.RU ? word.name().getRu() : word.name().getEn()).toList();
  }

  private static boolean supported(int keyword, FileType fileType) {
    return switch (keyword) {
      case BSLLexer.ASYNC_KEYWORD, BSLLexer.AWAIT_KEYWORD -> fileType != FileType.OS;
      default -> true;
    };
  }

  private static boolean startsBefore(Token token, Position position) {
    var line = token.getLine() - 1;
    return line < position.getLine()
      || (line == position.getLine() && token.getCharPositionInLine() < position.getCharacter());
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

}
