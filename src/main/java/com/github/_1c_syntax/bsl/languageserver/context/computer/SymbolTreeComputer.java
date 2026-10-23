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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventHandlerClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Вычислитель дерева символов документа.
 * <p>
 * Создаёт иерархическую структуру символов документа (модуль, методы, регионы, переменные),
 * правильно расставляя родительско-дочерние связи между символами.
 */
public class SymbolTreeComputer implements Computer<SymbolTree> {

  private final DocumentContext documentContext;
  private final SelfMemberClassifier selfMemberClassifier;
  private final EventHandlerClassifier eventHandlerClassifier;

  public SymbolTreeComputer(DocumentContext documentContext, SelfMemberClassifier selfMemberClassifier,
                            EventHandlerClassifier eventHandlerClassifier) {
    this.documentContext = documentContext;
    this.selfMemberClassifier = selfMemberClassifier;
    this.eventHandlerClassifier = eventHandlerClassifier;
  }

  @Override
  public SymbolTree compute() {

    // Имя, признанное реквизитом, символа не получает: запомнить его можно только здесь.
    var bareSelfProperties = new HashSet<String>();
    SelfMemberClassifier recordingClassifier = (DocumentContext document, String name) -> {
      var bareSelfProperty = selfMemberClassifier.isBareSelfProperty(document, name);
      if (bareSelfProperty) {
        bareSelfProperties.add(name);
      }
      return bareSelfProperty;
    };
    ModuleSymbol moduleSymbol = new ModuleSymbolComputer(documentContext).compute();
    List<MethodSymbol> methods = new MethodSymbolComputer(documentContext, eventHandlerClassifier).compute();
    // Переменные и регионы — за один общий обход дерева (оба обходят его вглубь).
    var regionVariableComputer =
      new RegionVariableSymbolComputer(documentContext, moduleSymbol, methods, recordingClassifier);
    List<VariableSymbol> variables = regionVariableComputer.compute();
    List<RegionSymbol> regions = regionVariableComputer.getRegions();

    List<SourceDefinedSymbol> allOfThem = new ArrayList<>(methods);
    allOfThem.addAll(regions);
    allOfThem.addAll(variables);

    allOfThem.sort(Comparator.comparingInt(symbol -> symbol.getRange().getStart().getLine()));

    List<SourceDefinedSymbol> topLevelSymbols = new ArrayList<>();
    SourceDefinedSymbol currentParent = moduleSymbol;

    for (SourceDefinedSymbol symbol : allOfThem) {
      currentParent = placeSymbol(topLevelSymbols, currentParent, symbol);
    }

    collapseChildrenCollection(moduleSymbol);
    var basis = new SymbolTree.Basis(documentContext.getModuleType(), documentContext.getMdoRef(),
      Set.copyOf(bareSelfProperties));
    return new SymbolTree(moduleSymbol, basis);
  }

  /**
   * Годно ли для документа дерево, построенное по тому же его тексту.
   * <p>
   * Кроме текста, дерево зависит от привязки документа к метаданным и от того, как
   * классификаторы ответили на вопросы о его методах и голых присваиваниях, — а их ответы
   * держатся на членах чужих модулей и типов. Дерево годно, если привязка та же и
   * классификаторы отвечают то же самое. Голые присваивания переспрашиваются по
   * переменным с неявным объявлением: среди них и переменные циклов, о которых при
   * построении не спрашивали, поэтому проверка строже нужного, но не слабее.
   *
   * @param symbolTree дерево, построенное по тому же тексту документа.
   * @return {@code true}, если построение заново дало бы то же дерево.
   */
  public boolean isUpToDate(SymbolTree symbolTree) {
    var basis = symbolTree.getBasis();
    return basis != SymbolTree.Basis.NONE
      && basis.moduleType() == documentContext.getModuleType()
      && basis.mdoRef().equals(documentContext.getMdoRef())
      && basis.bareSelfProperties().stream()
        .allMatch(name -> selfMemberClassifier.isBareSelfProperty(documentContext, name))
      && classifiedAsBefore(symbolTree.getModule());
  }

  /**
   * Классифицируются ли потомки символа так же, как при построении дерева. Обход идёт по
   * детям, а не по плоским спискам дерева: те строятся лениво, и проверка не должна их
   * достраивать у документов, которые после неё сразу отпустят.
   */
  private boolean classifiedAsBefore(SourceDefinedSymbol symbol) {
    for (var child : symbol.getChildren()) {
      if (child instanceof MethodSymbol method
        && !(method instanceof ConstructorSymbol)
        && method instanceof EventMethodSymbol
          != eventHandlerClassifier.isEventHandler(documentContext, method.getName())) {
        return false;
      }
      if (child instanceof VariableSymbol variable
        && variable.getKind() == VariableKind.DYNAMIC
        && selfMemberClassifier.isBareSelfProperty(documentContext, variable.getName())) {
        return false;
      }
      if (!classifiedAsBefore(child)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Создает минимальное и пустое SymbolTree по контексту документа.
   * <p>
   * Дерево содержит только ModuleSymbol с пустыми диапазонами.
   *
   * @param documentContext Контекст документа
   *
   * @return Пустое дерево символов.
   */
  public static SymbolTree empty(DocumentContext documentContext) {
    var module = ModuleSymbol.builder()
      .owner(documentContext)
      .range(Ranges.create())
      .selectionRange(Ranges.create())
      .name("empty")
      .build();

    return new SymbolTree(module, SymbolTree.Basis.NONE);
  }

  private static SourceDefinedSymbol placeSymbol(
    List<SourceDefinedSymbol> topLevelSymbols,
    SourceDefinedSymbol currentParent,
    SourceDefinedSymbol symbol
  ) {

    if (Ranges.containsRange(currentParent.getRange(), symbol.getRange())) {
      currentParent.getChildren().add(symbol);
      symbol.setParent(Optional.of(currentParent));

      return symbol;
    }

    Optional<SourceDefinedSymbol> maybeParent = currentParent.getParent();
    if (maybeParent.isEmpty()) {
      topLevelSymbols.add(symbol);
      return symbol;
    }

    return placeSymbol(topLevelSymbols, maybeParent.get(), symbol);
  }

  private static void collapseChildrenCollection(SourceDefinedSymbol symbol) {
    var children = symbol.getChildren();
    if (children instanceof ArrayList) {
      ((ArrayList<SourceDefinedSymbol>) children).trimToSize();
    }

    children.forEach(SymbolTreeComputer::collapseChildrenCollection);
  }
}
