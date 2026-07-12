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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

/**
 * Символьное дерево документа. Содержит все символы документа, вложенные друг в друга по принципу родитель -&gt; дети
 */
@Value
public class SymbolTree {

  /**
   * Корневой символ модуля документа.
   */
  ModuleSymbol module;

  /**
   * Список всех символов всех уровней (за исключением символа модуля документа), преобразованных в плоский список.
   */
  @Getter(lazy = true)
  List<SourceDefinedSymbol> childrenFlat = createChildrenFlat();

  /**
   * Список методов документа.
   */
  @Getter(lazy = true)
  List<MethodSymbol> methods = createMethods();

  @Getter(lazy = true)
  Map<String, MethodSymbol> methodsByName = createMethodsByName();

  /**
   * Конструктор OneScript-класса ({@code ПриСозданииОбъекта} / {@code OnObjectCreate}),
   * если он объявлен в документе.
   */
  @Getter(lazy = true)
  Optional<ConstructorSymbol> constructor = createConstructor();

  /**
   * Плоский список всех переменных документа
   */
  @Getter(lazy = true)
  List<VariableSymbol> variables = createVariables();

  // TODO: value = AccessLevel.PRIVATE после окончания тестирования производительности
  @Getter(lazy = true)
  Map<SourceDefinedSymbol, Map<String, VariableSymbol>> variablesByName = createVariablesByName();

  /**
   * Индекс символов по строке их {@code selectionRange} (диапазона имени в
   * объявлении) для быстрого поиска «символ, чьё имя стоит в позиции».
   * Заменяет линейный скан {@link #getChildrenFlat()} в
   * {@code SourceDefinedSymbolDeclarationReferenceFinder} на O(1)-lookup по
   * строке. На одной строке может быть несколько объявлений (разные колонки) —
   * поэтому значение это список, разводимый по колонкам через
   * {@link Ranges#containsPosition}. Порядок в бакете — как в
   * {@code getChildrenFlat()} (pre-order DFS), чтобы сохранить tie-break
   * {@code findFirst} прежнего скана.
   */
  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  Map<Integer, List<SourceDefinedSymbol>> symbolsBySelectionLine = createSymbolsBySelectionLine();

  /**
   * @return Список символов верхнего уровня за исключением символа модуля документа.
   */
  public List<SourceDefinedSymbol> getChildren() {
    return module.getChildren();
  }

  /**
   * Список всех символов всех уровней указанного типа (за исключением символа модуля документа),
   * преобразованных в плоский список.
   *
   * @param clazz класс искомого символа.
   * @param <T>   тип искомого символа.
   * @return плоский список символов указанного типа.
   */
  public <T> List<T> getChildrenFlat(Class<T> clazz) {
    return getChildrenFlat().stream()
      .filter(clazz::isInstance)
      .map(clazz::cast)
      .collect(Collectors.toList());
  }

  /**
   * @return Список областей, расположенных на верхнем уровне документа.
   */
  public List<RegionSymbol> getModuleLevelRegions() {
    return getChildren().stream()
      .filter(RegionSymbol.class::isInstance)
      .map(RegionSymbol.class::cast)
      .collect(Collectors.toList());
  }

  /**
   * @return плоский список всех областей документа.
   */
  public List<RegionSymbol> getRegionsFlat() {
    return getChildrenFlat(RegionSymbol.class);
  }

  /**
   * Попытка поиска символа метода по узлу дерева разбора.
   * <p>
   * Implementation note - Поиск осуществляется по месту определения метода (declaration).
   *
   * @param ctx узел дерева разбора документа.
   * @return найденный символ метода.
   */
  public Optional<MethodSymbol> getMethodSymbol(ParserRuleContext ctx) {
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

    // subNameRange == selectionRange метода, поэтому ищем через O(1)-индекс
    // селекшн-рейнджей (тот же, что и findSymbolBySelectionRange), а не линейным
    // сканом getMethods() с Range.equals на каждом.
    // Точное равенство диапазона обязательно: часть вызывающих передаёт не узел
    // объявления, а произвольный/ошибочный контекст (напр. ctx.getParent()),
    // старт которого может попасть в selectionRange соседнего символа. Прежний
    // скан для таких возвращал пусто (точное равенство не выполнялось) —
    // сохраняем это, иначе резолвится «лишний» метод.
    return findSymbolBySelectionRange(subNameRange.getStart())
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .filter(methodSymbol -> methodSymbol.getSubNameRange().equals(subNameRange));
  }

  /**
   * Поиск MethodSymbol в дереве по указанному имени (без учета регистра).
   *
   * @param methodName Имя метода
   * @return MethodSymbol, если он был найден в дереве символов.
   */
  public Optional<MethodSymbol> getMethodSymbol(String methodName) {
    return Optional.ofNullable(getMethodsByName().get(methodName));
  }

  /**
   * Попытка поиска символа переменной по узлу дерева разбора.
   * <p>
   * Implementation note Поиск осуществляется по месту определения переменной (declaration).
   *
   * @param ctx узел дерева разбора документа.
   * @return найденный символ переменной.
   */
  public Optional<VariableSymbol> getVariableSymbol(ParserRuleContext ctx) {

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
    // O(1)-индекс селекшн-рейнджей, а не линейным сканом getVariables() с
    // Range.equals на каждом.
    // Точное равенство диапазона обязательно: часть вызывающих передаёт не узел
    // объявления, а произвольный/ошибочный контекст, старт которого может
    // попасть в selectionRange соседнего символа. Прежний скан для таких
    // возвращал пусто — сохраняем это, иначе резолвится «лишняя» переменная.
    return findSymbolBySelectionRange(variableNameRange.getStart())
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .filter(variableSymbol -> variableSymbol.getVariableNameRange().equals(variableNameRange));
  }

  /**
   * Поиск VariableSymbol в дереве по указанному имени (без учета регистра) и области объявления.
   *
   * @param variableName Имя переменной
   * @param scopeSymbol  Символ, внутри которого осуществляется поиск.
   *                     Например, {@link ModuleSymbol} или {@link MethodSymbol}.
   * @return VariableSymbol, если он был найден в дереве символов.
   */
  public Optional<VariableSymbol> getVariableSymbol(String variableName, SourceDefinedSymbol scopeSymbol) {
    return Optional.ofNullable(
      getVariablesByName().getOrDefault(scopeSymbol, Collections.emptyMap()).get(variableName)
    );
  }

  /**
   * Поиск самого вложенного символа, содержащего указанную позицию.
   * <p>
   * Использует иерархический спуск по дереву символов вместо линейного поиска.
   *
   * @param position Позиция в документе.
   * @return Символ, содержащий позицию, или символ модуля, если позиция вне всех символов.
   */
  public SourceDefinedSymbol getSymbolAtPosition(Position position) {
    return findSymbolAtPosition(module, position);
  }

  /**
   * Символ, чьё имя ({@code selectionRange}) накрывает позицию — то есть позиция
   * стоит на объявлении символа. O(1)-lookup по строке через
   * {@link #getSymbolsBySelectionLine()}; при нескольких объявлениях на строке
   * возвращает первый по порядку {@code getChildrenFlat()} накрывающий символ.
   *
   * @param position позиция в документе.
   * @return символ, на объявлении которого стоит позиция, либо empty.
   */
  public Optional<SourceDefinedSymbol> findSymbolBySelectionRange(Position position) {
    var bucket = getSymbolsBySelectionLine().get(position.getLine());
    if (bucket == null) {
      return Optional.empty();
    }
    for (var symbol : bucket) {
      if (Ranges.containsPosition(symbol.getSelectionRange(), position)) {
        return Optional.of(symbol);
      }
    }
    return Optional.empty();
  }

  private SourceDefinedSymbol findSymbolAtPosition(SourceDefinedSymbol parent, Position position) {
    // Ищем среди детей символ, содержащий позицию
    for (var child : parent.getChildren()) {
      if (Ranges.containsPosition(child.getRange(), position)) {
        // Рекурсивно ищем более вложенный символ
        var found = findSymbolAtPosition(child, position);
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

  private List<SourceDefinedSymbol> createChildrenFlat() {
    List<SourceDefinedSymbol> symbols = new ArrayList<>();
    getChildren().forEach(child -> flatten(child, symbols));

    return symbols;
  }

  private List<MethodSymbol> createMethods() {
    return getChildrenFlat(MethodSymbol.class);
  }

  private Optional<ConstructorSymbol> createConstructor() {
    return getChildrenFlat(ConstructorSymbol.class).stream().findFirst();
  }

  private List<VariableSymbol> createVariables() {
    return getChildrenFlat(VariableSymbol.class);
  }

  private Map<SourceDefinedSymbol, Map<String, VariableSymbol>> createVariablesByName() {
    return getVariables().stream()
      .collect(
        groupingBy(
          VariableSymbol::getScope,
          toMap(
            VariableSymbol::getName,
            Function.identity(),
            (existing, replacement) -> existing,
            () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
          )
        )
      );
  }

  private Map<String, MethodSymbol> createMethodsByName() {
    return getMethods().stream()
      .collect(
        toMap(
          MethodSymbol::getName,
          Function.identity(),
          (existing, replacement) -> existing,
          () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        )
      );
  }

  private Map<Integer, List<SourceDefinedSymbol>> createSymbolsBySelectionLine() {
    Map<Integer, List<SourceDefinedSymbol>> byLine = new HashMap<>();
    for (var symbol : getChildrenFlat()) {
      var selectionRange = symbol.getSelectionRange();
      if (selectionRange == null) {
        continue;
      }
      // selectionRange имени обычно однострочен, но индексируем по всем строкам,
      // которые он покрывает, — чтобы поиск оставался точной заменой линейного
      // скана даже для (редкого) многострочного диапазона.
      var startLine = selectionRange.getStart().getLine();
      var endLine = selectionRange.getEnd().getLine();
      for (var line = startLine; line <= endLine; line++) {
        byLine.computeIfAbsent(line, k -> new ArrayList<>()).add(symbol);
      }
    }
    // Списки строятся один раз и больше не растут — освобождаем лишнюю ёмкость.
    for (var bucket : byLine.values()) {
      ((ArrayList<SourceDefinedSymbol>) bucket).trimToSize();
    }
    return byLine;
  }

  private static void flatten(SourceDefinedSymbol symbol, List<SourceDefinedSymbol> symbols) {
    symbols.add(symbol);
    symbol.getChildren().forEach(child -> flatten(child, symbols));
  }

}
