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
import com.github._1c_syntax.bsl.languageserver.index.SourceBoundRecords;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Индекс мест документа, где переменная выступает приёмником, по её имени (без учёта
 * регистра), разрезанный по URI:
 * <ul>
 *   <li><b>вызовы через неё</b> — {@code callStatement} с этим базовым идентификатором:
 *       {@code X.Вставить(...)}, {@code X.Колонки.Добавить(...)};</li>
 *   <li><b>присваивания ей или её члену</b> — {@code assignment} с этим базовым
 *       идентификатором левой части: часть состояния объект получает не вызовом, а
 *       присваиванием свойству (у элемента формы, созданного в коде, вид задаётся строкой
 *       {@code Элемент.Вид = ВидГруппыФормы.ОбычнаяГруппа});</li>
 *   <li><b>вызовы {@code Свойство(Ключ, Приёмник)}</b>, отдавшие её вторым аргументом: здесь
 *       переменную типизирует чужой вызов. Индексируется {@code methodCall}, а не
 *       {@code callStatement}: у {@code Свойство} есть возвращаемое значение, и в
 *       подавляющем большинстве кода вызов стоит внутри условия
 *       ({@code Если Параметры.Свойство("Ключ", Значение) Тогда}), а не отдельным оператором.</li>
 * </ul>
 * Без индекса вывод типа переменной сканировал бы весь модуль на каждую переменную. Все три
 * вида собираются за один обход дерева разбора: выводу типа обычно нужны все три, а обход
 * большого модуля дороже самих карт.
 * <p>
 * Индекс держит AST-узлы, поэтому привязан к дереву разбора, по которому построен
 * ({@link SourceBoundRecords}): после правки документа узлы прежнего текста не отдаются,
 * даже если событие о ней до индекса ещё не дошло. Освобождается per-URI на событиях
 * жизненного цикла документа через {@link AbstractDocumentLifecycleClearableIndex}.
 * Освобождение вторичных данных особенно важно: так batch-анализ выбрасывает AST после
 * каждого файла, иначе индекс удерживал бы разобранные деревья всех файлов на весь прогон.
 * Строится лениво.
 */
@Component
@WorkspaceScope
public class ReceiverSitesIndex extends AbstractDocumentLifecycleClearableIndex {

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

  /** Позиция переменной-приёмника среди аргументов {@code Свойство} — общая с сужением по условию. */
  public static final int OUT_PARAMETER_INDEX = 1;

  /**
   * Аргумент, целиком состоящий из одного идентификатора. Текст узла ANTLR склеен без
   * пробелов, поэтому у любого более сложного выражения здесь окажется точка, скобка либо
   * знак операции, и такой аргумент в индекс не попадёт: типизировать можно только
   * переменную, а не выражение.
   */
  private static final Pattern BARE_IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");

  private final SourceBoundRecords<BSLParser.FileContext, Sites> byUri = new SourceBoundRecords<>();

  /**
   * Вызовы через переменную: {@code callStatement}'ы документа, базовый идентификатор
   * которых равен {@code receiverName}.
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param receiverName имя ресивера (базового идентификатора цепочки).
   * @return вызовы в порядке следования в документе; пустой список, если таких нет.
   */
  public List<BSLParser.CallStatementContext> callStatements(URI uri, BSLParser.FileContext ast,
                                                             String receiverName) {
    return sites(uri, ast).callStatements().getOrDefault(key(receiverName), List.of());
  }

  /**
   * Присваивания переменной или её члену: базовый идентификатор левой части равен
   * {@code receiverName}.
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param receiverName имя ресивера (базового идентификатора левой части).
   * @return присваивания в порядке следования в документе; пустой список, если таких нет.
   */
  public List<BSLParser.AssignmentContext> assignments(URI uri, BSLParser.FileContext ast, String receiverName) {
    return sites(uri, ast).assignments().getOrDefault(key(receiverName), List.of());
  }

  /**
   * Вызовы {@code Свойство}, отдавшие переменную с таким именем вторым аргументом.
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param variableName имя переменной-приёмника.
   * @return вызовы в порядке следования в документе; пустой список, если таких нет.
   */
  public List<BSLParser.MethodCallContext> propertyCalls(URI uri, BSLParser.FileContext ast, String variableName) {
    return sites(uri, ast).propertyCalls().getOrDefault(key(variableName), List.of());
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

  private Sites sites(URI uri, BSLParser.FileContext ast) {
    return byUri.get(uri, ast, ReceiverSitesIndex::build);
  }

  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static Sites build(BSLParser.FileContext ast) {
    var sites = new Sites(new HashMap<>(), new HashMap<>(), new HashMap<>());
    collect(ast, sites);
    return sites;
  }

  /**
   * Обойти узлы правил поддерева в прямом порядке — так списки идут в порядке следования в
   * документе. Листья-токены не обходятся: искомые узлы — только правила.
   */
  private static void collect(ParserRuleContext node, Sites sites) {
    switch (node.getRuleIndex()) {
      case BSLParser.RULE_callStatement -> addCallStatement((BSLParser.CallStatementContext) node, sites);
      case BSLParser.RULE_assignment -> addAssignment((BSLParser.AssignmentContext) node, sites);
      case BSLParser.RULE_methodCall -> addPropertyCall((BSLParser.MethodCallContext) node, sites);
      default -> {
        // остальные правила интересны только своими потомками
      }
    }
    var children = node.getChildren();
    for (var i = 0; i < children.size(); i++) {
      if (children.get(i) instanceof ParserRuleContext child) {
        collect(child, sites);
      }
    }
  }

  private static void addCallStatement(BSLParser.CallStatementContext call, Sites sites) {
    var identifier = call.IDENTIFIER();
    if (identifier != null) {
      sites.callStatements().computeIfAbsent(key(identifier.getText()), k -> new ArrayList<>()).add(call);
    }
  }

  private static void addAssignment(BSLParser.AssignmentContext assignment, Sites sites) {
    var lValue = assignment.lValue();
    if (lValue == null) {
      return;
    }
    var identifier = lValue.IDENTIFIER();
    if (identifier != null) {
      sites.assignments().computeIfAbsent(key(identifier.getText()), k -> new ArrayList<>()).add(assignment);
    }
  }

  private static void addPropertyCall(BSLParser.MethodCallContext call, Sites sites) {
    var name = outParameterName(call);
    if (name != null) {
      sites.propertyCalls().computeIfAbsent(key(name), k -> new ArrayList<>(1)).add(call);
    }
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

  /** Места документа по ключу — имени приёмника в нижнем регистре. */
  private record Sites(
    Map<String, List<BSLParser.CallStatementContext>> callStatements,
    Map<String, List<BSLParser.AssignmentContext>> assignments,
    Map<String, List<BSLParser.MethodCallContext>> propertyCalls
  ) {
  }
}
