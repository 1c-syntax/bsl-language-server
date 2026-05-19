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
package com.github._1c_syntax.bsl.languageserver.utils.bsl;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Поиск парных открывающих/закрывающих ключевых слов BSL: `Если`/`КонецЕсли`,
 * `Процедура`/`КонецПроцедуры`, `Цикл`/`КонецЦикла` и т.п.
 */
public final class BlockKeywordMatcher {

  private record ScopePair(Set<Integer> openers, Set<Integer> closers) {
  }

  private static final ScopePair IF_SCOPE =
    new ScopePair(Set.of(BSLLexer.IF_KEYWORD), Set.of(BSLLexer.ENDIF_KEYWORD));
  private static final ScopePair DO_SCOPE =
    new ScopePair(Set.of(BSLLexer.WHILE_KEYWORD, BSLLexer.FOR_KEYWORD), Set.of(BSLLexer.ENDDO_KEYWORD));
  private static final ScopePair TRY_SCOPE =
    new ScopePair(Set.of(BSLLexer.TRY_KEYWORD), Set.of(BSLLexer.ENDTRY_KEYWORD));
  private static final ScopePair PROCEDURE_SCOPE =
    new ScopePair(Set.of(BSLLexer.PROCEDURE_KEYWORD), Set.of(BSLLexer.ENDPROCEDURE_KEYWORD));
  private static final ScopePair FUNCTION_SCOPE =
    new ScopePair(Set.of(BSLLexer.FUNCTION_KEYWORD), Set.of(BSLLexer.ENDFUNCTION_KEYWORD));

  private static final Map<Integer, ScopePair> SCOPE_BY_CLOSER_TYPE = Map.ofEntries(
    Map.entry(BSLLexer.ENDIF_KEYWORD, IF_SCOPE),
    Map.entry(BSLLexer.ELSE_KEYWORD, IF_SCOPE),
    Map.entry(BSLLexer.ELSIF_KEYWORD, IF_SCOPE),
    Map.entry(BSLLexer.ENDDO_KEYWORD, DO_SCOPE),
    Map.entry(BSLLexer.ENDTRY_KEYWORD, TRY_SCOPE),
    Map.entry(BSLLexer.EXCEPT_KEYWORD, TRY_SCOPE),
    Map.entry(BSLLexer.ENDPROCEDURE_KEYWORD, PROCEDURE_SCOPE),
    Map.entry(BSLLexer.ENDFUNCTION_KEYWORD, FUNCTION_SCOPE)
  );

  private BlockKeywordMatcher() {
    // utility class
  }

  /**
   * Ищет парный открывающий токен для закрывающего ключевого слова.
   *
   * Возвращает null, если переданный токен не относится к парным закрывающим
   * или парный открывающий не найден (несбалансированный код).
   */
  public static @Nullable Token findMatchingOpener(List<Token> allTokens, int closerIndex) {
    if (closerIndex < 0 || closerIndex >= allTokens.size()) {
      return null;
    }
    var closer = allTokens.get(closerIndex);
    var scope = SCOPE_BY_CLOSER_TYPE.get(closer.getType());
    if (scope == null) {
      return null;
    }
    // Для настоящих закрывашек (КонецЕсли/КонецЦикла и т.п.) стартуем с балансом 1
    // и ищем opener, сводящий его в 0. Для промежуточных (Иначе/ИначеЕсли/Исключение)
    // стартуем с 0 — родитель найдётся при первом opener без перекрывающего closer
    // (баланс = -1).
    var balance = scope.closers.contains(closer.getType()) ? 1 : 0;
    var target = balance - 1;
    for (var i = closerIndex - 1; i >= 0; i--) {
      var t = allTokens.get(i);
      if (t.getChannel() != Token.DEFAULT_CHANNEL) {
        continue;
      }
      var type = t.getType();
      if (scope.closers.contains(type)) {
        balance++;
      } else if (scope.openers.contains(type)) {
        balance--;
        if (balance == target) {
          return t;
        }
      } else {
        // no-op: токен вне рассматриваемой скобочной структуры.
      }
    }
    return null;
  }
}
