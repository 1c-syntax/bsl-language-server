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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс callStatement'ов документа по базовому идентификатору-ресиверу, разрезанный
 * по URI.
 * <p>
 * Накопление полей структур ({@code X.Вставить(...)}) и колонок таблиц значений
 * ({@code X.Колонки.Добавить(...)}) в {@code ExpressionTypeInferencer} ищет mutation-
 * вызовы для конкретной переменной. Раньше это был полный скан AST модуля
 * ({@code findAllRuleNodes(RULE_callStatement)}) на каждую такую переменную. Индекс
 * строит карту {@code базовый идентификатор → callStatement'ы} один раз на документ,
 * и поиск становится hash-lookup'ом по имени переменной.
 * <p>
 * Индекс держит AST-узлы, поэтому инвалидируется per-URI на событиях жизненного
 * цикла документа через {@link AbstractDocumentLifecycleClearableIndex} (изменение
 * содержимого / освобождение вторичных данных / закрытие / удаление). Освобождение
 * вторичных данных особенно важно: так batch-анализ выбрасывает AST после каждого
 * файла, иначе индекс удерживал бы разобранные деревья всех файлов на весь прогон.
 * Строится лениво.
 */
@Component
@WorkspaceScope
public class CallStatementByReceiverIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<String, List<BSLParser.CallStatementContext>>> byUri = new ConcurrentHashMap<>();

  /**
   * callStatement'ы документа, базовый идентификатор которых равен {@code receiverName}
   * (без учёта регистра). Пустой список, если таких нет.
   *
   * @param uri          URI документа.
   * @param ast          корень AST документа (для ленивого построения индекса).
   * @param receiverName имя ресивера (базового идентификатора цепочки).
   * @return callStatement'ы с таким ресивером.
   */
  public List<BSLParser.CallStatementContext> byReceiver(URI uri, BSLParser.FileContext ast, String receiverName) {
    // Гонка clear<->computeIfAbsent осознанно не закрывается: если документ
    // инвалидируется ровно между clear и завершением build, в карте может осесть
    // индекс по предыдущему AST. Следующая инвалидация его уберёт, а инференс читает
    // свежий AST явно — устаревший индекс лишь продлевает жизнь старым узлам до
    // следующего события (та же модель «без кросс-документной инвалидации»).
    var index = byUri.computeIfAbsent(uri, k -> build(ast));
    return index.getOrDefault(receiverName.toLowerCase(Locale.ROOT), List.of());
  }

  private static Map<String, List<BSLParser.CallStatementContext>> build(BSLParser.FileContext ast) {
    var index = new HashMap<String, List<BSLParser.CallStatementContext>>();
    for (var call : Trees.<BSLParser.CallStatementContext>findAllRuleNodes(ast, BSLParser.RULE_callStatement)) {
      var identifier = call.IDENTIFIER();
      if (identifier != null) {
        index.computeIfAbsent(identifier.getText().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(call);
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
