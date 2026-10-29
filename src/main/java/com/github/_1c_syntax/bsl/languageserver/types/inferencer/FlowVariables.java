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

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Change;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Changes;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Переменные тела и их изменения; коллекции создаются здесь и наружу отдаются только для чтения. */
record FlowVariables(List<VariableSymbol> variables, Map<VariableSymbol, Integer> indexes,
                     Map<ParserRuleContext, List<Change>> changesByStatement) {
  FlowVariables {
    variables = Collections.unmodifiableList(variables);
    indexes = Collections.unmodifiableMap(indexes);
    changesByStatement = Collections.unmodifiableMap(changesByStatement);
  }

  static FlowVariables collect(BSLParser.CodeBlockContext body, FlowLayout layout, FlowInputs inputs,
                               Function<VariableSymbol, Changes> changesOf) {
    // Каждой переменной, попавшей в расчёт, достаётся номер — её место в окружении.
    // Окружение хранится массивом по этим номерам, а не картой: копия массива на десяток
    // элементов дешевле копирования и перехеширования карты, а копий тут много — на
    // каждое изменение, слияние и сужение в каждом проходе.
    Map<VariableSymbol, Integer> indexes = new IdentityHashMap<>();
    var variables = new ArrayList<VariableSymbol>();
    Map<ParserRuleContext, List<Change>> changesByStatement = new IdentityHashMap<>();
    for (var variable : inputs.variables().apply(body)) {
      var changes = changesOf.apply(variable);
      var definitions = changes.definitions();
      var mutations = changes.mutations();
      // Если хоть одно изменение типа не легло в граф отдельным оператором, его вклад
      // потерялся бы — тогда точнее прежний путь с обходом всей области видимости.
      // Отсутствие присваиваний расчёту не мешает: тип такой переменной — входной факт по
      // всему телу, и это тот же ответ, что дал бы обход области видимости.
      if (!allPlaced(layout, definitions) || !allPlaced(layout, mutations)) {
        continue;
      }
      var index = variables.size();
      indexes.put(variable, index);
      variables.add(variable);
      collectChanges(layout, variable, index, definitions, true, changesByStatement);
      collectChanges(layout, variable, index, mutations, false, changesByStatement);
    }

    changesByStatement.replaceAll((statement, changes) -> List.copyOf(changes));
    return new FlowVariables(variables, indexes, changesByStatement);
  }

  /** Разложить позиции изменений по операторам, в которых они стоят. */
  private static void collectChanges(
    FlowLayout layout,
    VariableSymbol variable,
    int index,
    Collection<Position> positions,
    boolean definition,
    Map<ParserRuleContext, List<Change>> target
  ) {
    layout.index(positions).forEach((statement, position) ->
      target.computeIfAbsent(statement, key -> new ArrayList<>(1))
        .add(new Change(variable, index, position, definition)));
  }

  /** Все ли изменения типа легли в операторы графа. */
  private static boolean allPlaced(FlowLayout layout, Collection<Position> positions) {
    for (var position : positions) {
      if (layout.insideBody(position) && !layout.isPlaced(position)) {
        return false;
      }
    }
    return true;
  }
}
