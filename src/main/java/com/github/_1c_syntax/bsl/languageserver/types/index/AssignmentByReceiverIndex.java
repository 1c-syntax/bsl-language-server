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
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс присваиваний документа по базовому идентификатору левой части, разрезанный по URI.
 * <p>
 * Близнец {@link CallStatementByReceiverIndex}: часть состояния объект получает не
 * вызовом, а присваиванием свойству. Так, у элемента формы, созданного в коде
 * ({@code Элементы.Добавить(…)}), конкретный вид задаётся отдельной строкой
 * {@code Элемент.Вид = ВидГруппыФормы.ОбычнаяГруппа}, и без разбора присваиваний тип
 * остаётся базовым.
 * <p>
 * Индекс держит AST-узлы, поэтому инвалидируется per-URI на событиях жизненного цикла
 * документа через {@link AbstractDocumentLifecycleClearableIndex}. Строится лениво.
 */
@Component
@WorkspaceScope
public class AssignmentByReceiverIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<String, List<BSLParser.AssignmentContext>>> byUri = new ConcurrentHashMap<>();

  /**
   * Присваивания документа, базовый идентификатор левой части которых равен
   * {@code receiverName} (без учёта регистра). Пустой список, если таких нет.
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param receiverName имя ресивера (базового идентификатора левой части).
   * @return присваивания с таким ресивером.
   */
  public List<BSLParser.AssignmentContext> byReceiver(URI uri, BSLParser.FileContext ast, String receiverName) {
    // Гонка clear<->computeIfAbsent осознанно не закрывается — см. CallStatementByReceiverIndex.
    var index = byUri.computeIfAbsent(uri, k -> build(ast));
    return index.getOrDefault(receiverName.toLowerCase(Locale.ROOT), List.of());
  }

  private static Map<String, List<BSLParser.AssignmentContext>> build(BSLParser.FileContext ast) {
    var index = new HashMap<String, List<BSLParser.AssignmentContext>>();
    for (var assignment : Trees.<BSLParser.AssignmentContext>findAllRuleNodes(ast, BSLParser.RULE_assignment)) {
      var lValue = assignment.lValue();
      if (lValue == null) {
        continue;
      }
      var identifier = lValue.IDENTIFIER();
      if (identifier != null) {
        index.computeIfAbsent(identifier.getText().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
          .add(assignment);
      }
    }
    return index;
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
