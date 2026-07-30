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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Переменные документа, разложенные по областям видимости — по методу либо по телу модуля.
 * <p>
 * Одна область — одно тело, поэтому раскладка отвечает на вопрос «какие переменные живут в
 * том же теле, что заданная». Считается один раз на документ: без этого каждый расчёт по
 * потоку перебирал бы переменные всего модуля, а тел в модуле столько же, сколько методов.
 * <p>
 * Переменные модуля (объявленные {@code Перем}) в раскладку не попадают: их меняют из разных
 * методов, и расчёт по одному телу для них неприменим.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class VariablesByScopeIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<SourceDefinedSymbol, List<VariableSymbol>>> byUri = new ConcurrentHashMap<>();

  /**
   * Переменные документа по областям видимости.
   *
   * @param documentContext документ.
   * @return переменные по областям; область без переменных в карте отсутствует.
   */
  public Map<SourceDefinedSymbol, List<VariableSymbol>> get(DocumentContext documentContext) {
    // Раскладка считается ВНЕ computeIfAbsent: обход дерева символов не мгновенный, а под
    // замком корзины на нём выстраивались бы все потоки пакетного анализа. Двойная работа
    // при гонке безвредна — результат от порядка не зависит.
    var cached = byUri.get(documentContext.getUri());
    if (cached != null) {
      return cached;
    }
    Map<SourceDefinedSymbol, List<VariableSymbol>> byScope = new HashMap<>();
    for (var variable : documentContext.getSymbolTree().getVariables()) {
      var scope = variable.getScope();
      if (scope != null && variable.getKind() != VariableKind.MODULE) {
        byScope.computeIfAbsent(scope, key -> new ArrayList<>()).add(variable);
      }
    }
    var previous = byUri.putIfAbsent(documentContext.getUri(), byScope);
    return previous == null ? byScope : previous;
  }

  /**
   * Удалить раскладку документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    byUri.remove(uri);
  }
}
