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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Optional;

/** Поиск символов по объявлениям и области видимости. */
final class SymbolTreeLookup {
  private SymbolTreeLookup() {
  }

  static Optional<MethodSymbol> methodByNode(SymbolTree tree, ParserRuleContext ctx) {
    var subNameRange = SymbolDeclarationRanges.methodName(ctx);

    // subNameRange == selectionRange метода, поэтому ищем через O(1)-индекс
    // селекшн-рейнджей (тот же, что и findSymbolBySelectionRange), а не линейным
    // сканом getMethods() с Range.equals на каждом.
    // Точное равенство диапазона обязательно: часть вызывающих передаёт не узел
    // объявления, а произвольный/ошибочный контекст (напр. ctx.getParent()),
    // старт которого может попасть в selectionRange соседнего символа. Прежний
    // скан для таких возвращал пусто (точное равенство не выполнялось) —
    // сохраняем это, иначе резолвится «лишний» метод.
    return tree.findSymbolBySelectionRange(subNameRange.getStart())
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .filter(methodSymbol -> methodSymbol.getSubNameRange().equals(subNameRange));
  }

  static Optional<VariableSymbol> variableByNode(SymbolTree tree, ParserRuleContext ctx) {

    var variableNameRange = SymbolDeclarationRanges.variableName(ctx);

    // variableNameRange == selectionRange переменной, поэтому ищем через
    // индекс селекшн-рейнджей, а не линейным сканом getVariables() с
    // Range.equals на каждом.
    // Точное равенство диапазона обязательно: часть вызывающих передаёт не узел
    // объявления, а произвольный/ошибочный контекст, старт которого может
    // попасть в selectionRange соседнего символа. Прежний скан для таких
    // возвращал пусто — сохраняем это, иначе резолвится «лишняя» переменная.
    return tree.findSymbolBySelectionRange(variableNameRange.getStart())
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .filter(variableSymbol -> variableSymbol.getVariableNameRange().equals(variableNameRange));
  }

  static Optional<VariableSymbol> variableInScope(SymbolTree tree, ParserRuleContext node, String variableName) {
    var sub = SymbolDeclarationRanges.enclosingMethod(node);
    if (sub.isPresent()) {
      var subContext = sub.orElseThrow();
      var inMethod = tree.getMethodSymbol(subContext).flatMap(method -> tree.getVariableSymbol(variableName, method));
      if (inMethod.isPresent()) {
        return inMethod;
      }
      // У метода с ошибками разбора объявленные и созданные в нём переменные заводятся на
      // уровне модуля — свои они ему от этого быть не перестают.
      return tree.getVariableSymbol(variableName, tree.getModule())
        .filter(variable -> variable.getKind() == VariableKind.MODULE
          || Ranges.containsRange(Ranges.create(subContext), variable.getRange()));
    }
    return tree.getVariableSymbol(variableName, tree.getModule());
  }
}
