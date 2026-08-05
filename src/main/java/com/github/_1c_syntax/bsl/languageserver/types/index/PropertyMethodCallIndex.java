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

import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Индекс вызовов {@code Свойство(Ключ, Приёмник)} документа по имени переменной-приёмника,
 * разрезанный по URI.
 * <p>
 * Близнец {@link CallStatementByReceiverIndex} и {@link AssignmentByReceiverIndex}, но с
 * другим ключом: там переменная стоит слева и меняет себя сама, здесь она отдана
 * <b>вторым аргументом</b>, и типизирует её чужой вызов. Индексируется поэтому не
 * {@code callStatement}, а {@code methodCall}: у {@code Свойство} есть возвращаемое
 * значение, и в подавляющем большинстве кода вызов стоит не отдельным оператором, а внутри
 * условия ({@code Если Параметры.Свойство("Ключ", Значение) Тогда}) — индекс по операторам
 * такие вызовы не увидел бы вовсе.
 * <p>
 * Индекс держит AST-узлы, поэтому инвалидируется per-URI на событиях жизненного цикла
 * документа через {@link AbstractDocumentLifecycleClearableIndex}. Строится лениво.
 */
@Component
@WorkspaceScope
public class PropertyMethodCallIndex extends AbstractDocumentLifecycleClearableIndex {

  /**
   * Имя метода-читателя свойства в обеих локалях.
   * <p>
   * Публичные: то же правило нужно сужению по условию
   * ({@code GuardConditionNarrowing}) — оно решает, какие условия считать проверкой
   * наличия ключа. Разойдись эти два места, расхождение было бы молчаливым: условие
   * сузило бы приёмник, вызов которого индекс не записал, либо наоборот.
   */
  public static final String PROPERTY_METHOD_RU = "Свойство";
  public static final String PROPERTY_METHOD_EN = "Property";

  /** Позиция переменной-приёмника среди аргументов — общая с сужением по условию. */
  public static final int OUT_PARAMETER_INDEX = 1;

  /**
   * Аргумент, целиком состоящий из одного идентификатора. Текст узла ANTLR склеен без
   * пробелов, поэтому у любого более сложного выражения здесь окажется точка, скобка либо
   * знак операции, и такой аргумент в индекс не попадёт: типизировать можно только
   * переменную, а не выражение.
   */
  private static final Pattern BARE_IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

  private final Map<URI, Map<String, List<BSLParser.MethodCallContext>>> byUri = new ConcurrentHashMap<>();

  /**
   * Вызовы {@code Свойство}, отдавшие переменную с таким именем вторым аргументом
   * (регистр не важен).
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param variableName имя переменной-приёмника.
   * @return вызовы в порядке следования в документе; пустой список, если таких нет.
   */
  public List<BSLParser.MethodCallContext> byOutParameter(URI uri, BSLParser.FileContext ast,
                                                          String variableName) {
    // Та же модель гонки clear<->computeIfAbsent, что у соседних индексов: осевший индекс
    // по прежнему AST уберёт следующая инвалидация.
    // Построение идёт ВНЕ computeIfAbsent: обход всего AST под замком корзины выстраивал бы
    // на нём потоки пакетного анализа. Двойная работа при гонке безвредна — индекс зависит
    // только от AST (так же поступают VariableFlowAnalyzer.layoutOf и GuardConditionNarrowing).
    var index = byUri.get(uri);
    if (index == null) {
      var built = build(ast);
      var previous = byUri.putIfAbsent(uri, built);
      index = previous == null ? built : previous;
    }
    return index.getOrDefault(variableName.toLowerCase(Locale.ROOT), List.of());
  }

  private static Map<String, List<BSLParser.MethodCallContext>> build(BSLParser.FileContext ast) {
    var index = new HashMap<String, List<BSLParser.MethodCallContext>>();
    for (var call : Trees.<BSLParser.MethodCallContext>findAllRuleNodes(ast, BSLParser.RULE_methodCall)) {
      var name = outParameterName(call);
      if (name != null) {
        index.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>(1)).add(call);
      }
    }
    return index;
  }

  /**
   * Имя переменной, отданной вызову вторым аргументом.
   *
   * @return имя; {@code null}, если это не {@code Свойство} либо второй аргумент —
   *     не голая переменная.
   */
  private static @Nullable String outParameterName(BSLParser.MethodCallContext call) {
    if (!isPropertyMethod(call.methodName())) {
      return null;
    }
    var argument = outParameterArgument(call);
    if (argument == null) {
      return null;
    }
    var text = argument.getText();
    return BARE_IDENTIFIER.matcher(text).matches() ? text : null;
  }

  /** Тот ли это метод — {@code Свойство} в любом из двух написаний. */
  private static boolean isPropertyMethod(BSLParser.@Nullable MethodNameContext methodName) {
    if (methodName == null) {
      return false;
    }
    var text = methodName.getText();
    return PROPERTY_METHOD_RU.equalsIgnoreCase(text) || PROPERTY_METHOD_EN.equalsIgnoreCase(text);
  }

  /**
   * Выражение, отданное вызову вторым аргументом.
   *
   * @return выражение; {@code null}, если аргументов меньше двух.
   */
  private static BSLParser.@Nullable ExpressionContext outParameterArgument(BSLParser.MethodCallContext call) {
    var paramList = call.doCall() == null ? null : call.doCall().callParamList();
    if (paramList == null) {
      return null;
    }
    var params = paramList.callParam();
    return params.size() <= OUT_PARAMETER_INDEX ? null : params.get(OUT_PARAMETER_INDEX).expression();
  }

  /**
   * Удалить индекс по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    byUri.remove(uri);
  }
}
