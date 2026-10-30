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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Optional;

/** Поиск символов по узлам синтаксического дерева, области видимости и позиции в документе. */
final class SymbolTreeLookup {

  private SymbolTreeLookup() {
  }

  static Optional<MethodSymbol> methodByNode(SymbolTree tree, ParserRuleContext ctx) {
    ParserRuleContext subNameNode;
    if (Trees.nodeContainsErrors(ctx)) {
      subNameNode = ctx;
    } else if (ctx instanceof BSLParser.SubContext subContext) {
      if (subContext.function() == null) {
        subNameNode = subContext.procedure().procDeclaration().subName();
      } else {
        subNameNode = subContext.function().funcDeclaration().subName();
      }
    } else {
      subNameNode = ctx;
    }

    Range subNameRange = Ranges.create(subNameNode);

    // subNameRange == selectionRange метода, поэтому ищем через индекс
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

    ParserRuleContext varNameNode;

    if (Trees.nodeContainsErrors(ctx)) {
      varNameNode = ctx;
    } else if (ctx instanceof BSLParser.ModuleVarDeclarationContext moduleVarDeclarationContext) {
      varNameNode = moduleVarDeclarationContext.var_name();
    } else if (ctx instanceof BSLParser.SubVarDeclarationContext subVarDeclarationContext) {
      varNameNode = subVarDeclarationContext.var_name();
    } else {
      varNameNode = ctx;
    }

    Range variableNameRange = Ranges.create(varNameNode);

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
    var sub = Trees.getRootParent(node, BSLParser.RULE_sub);
    if (sub instanceof BSLParser.SubContext subContext) {
      var inMethod = tree.getMethodSymbol(subContext).flatMap(method -> tree.getVariableSymbol(variableName, method));
      if (inMethod.isPresent()) {
        return inMethod;
      }
    }
    return tree.getVariableSymbol(variableName, tree.getModule());
  }

  static SourceDefinedSymbol innermostSymbol(SourceDefinedSymbol parent, Position position) {
    // Ищем среди детей символ, содержащий позицию
    for (var child : parent.getChildren()) {
      if (Ranges.containsPosition(child.getRange(), position)) {
        // Рекурсивно ищем более вложенный символ
        var found = innermostSymbol(child, position);
        // Пропускаем Namespace (Region) - ищем дальше или возвращаем parent
        if (found.getSymbolKind() == SymbolKind.Namespace) {
          continue;
        }
        return found;
      }
    }
    // Если среди детей не нашли — возвращаем текущий символ
    return parent;
  }
}
