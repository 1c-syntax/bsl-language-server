/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collections;
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
   * Плоский список всех переменных документа
   */
  @Getter(lazy = true)
  List<VariableSymbol> variables = createVariables();

  // TODO: value = AccessLevel.PRIVATE после окончания тестирования производительности
  @Getter(lazy = true)
  Map<SourceDefinedSymbol, Map<String, VariableSymbol>> variablesByName = createVariablesByName();

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
  public Optional<MethodSymbol> getMethodSymbol(BSLParserRuleContext ctx) {
    BSLParserRuleContext subNameNode;
    if (Trees.nodeContainsErrors(ctx)) {
      subNameNode = ctx;
    } else if (ctx instanceof BSLParser.SubContext) {
      if (((BSLParser.SubContext) ctx).function() == null) {
        subNameNode = ((BSLParser.SubContext) ctx).procedure().procDeclaration().subName();
      } else {
        subNameNode = ((BSLParser.SubContext) ctx).function().funcDeclaration().subName();
      }
    } else {
      subNameNode = ctx;
    }

    Range subNameRange = Ranges.create(subNameNode);

    return getMethods().stream()
      .filter(methodSymbol -> methodSymbol.getSubNameRange().equals(subNameRange))
      .findAny();
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
  public Optional<VariableSymbol> getVariableSymbol(BSLParserRuleContext ctx) {

    BSLParserRuleContext varNameNode;

    if (Trees.nodeContainsErrors(ctx)) {
      varNameNode = ctx;
    } else if (ctx instanceof BSLParser.ModuleVarDeclarationContext) {
      varNameNode = ((BSLParser.ModuleVarDeclarationContext) ctx).var_name();
    } else if (ctx instanceof BSLParser.SubVarDeclarationContext) {
      varNameNode = ((BSLParser.SubVarDeclarationContext) ctx).var_name();
    } else {
      varNameNode = ctx;
    }

    Range variableNameRange = Ranges.create(varNameNode);

    return getVariables().stream()
      .filter(variableSymbol -> variableSymbol.getVariableNameRange().equals(variableNameRange))
      .findAny();
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

  private List<SourceDefinedSymbol> createChildrenFlat() {
    List<SourceDefinedSymbol> symbols = new ArrayList<>();
    getChildren().forEach(child -> flatten(child, symbols));

    return symbols;
  }

  private List<MethodSymbol> createMethods() {
    return getChildrenFlat(MethodSymbol.class);
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

  private static void flatten(SourceDefinedSymbol symbol, List<SourceDefinedSymbol> symbols) {
    symbols.add(symbol);
    symbol.getChildren().forEach(child -> flatten(child, symbols));
  }

}
