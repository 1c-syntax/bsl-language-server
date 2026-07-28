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

import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш выведенных типов выражений, разрезанный по URI документа и ключуемый по
 * AST-узлу выражения.
 * <p>
 * Дополняет {@link InferredVariableTypeIndex} (кэш по символу переменной) для
 * <b>не-переменных</b> ресиверов — цепочек {@code А.Б.В}, обращений к менеджерам
 * конфигурации ({@code Справочники.X}, {@code РегистрыСведений.Y}) и общим
 * модулям, результатов вызовов. На большом модуле один и тот же ресивер
 * встречается во множестве член-доступов, а вложенные ресиверы цепочки — под
 * каждым охватывающим доступом; без этого кэша {@code ExpressionTypeInferencer}
 * переинферивает их (и триггерит повторный резолв ссылок) десятки раз за проход.
 * <p>
 * Наполняется <b>лениво</b> из {@code ExpressionTypeInferencer.inferInternal}
 * только для «чистого корня» инференса (вне рекурсии символов), когда результат
 * узла не зависит от контекста обхода. Кэшируются и пустые результаты
 * ({@link TypeSet#EMPTY}) — они экономят тот же дорогой резолв.
 * <p>
 * Ключ — сам {@link ParseTree}-узел (identity equals/hashCode ANTLR-контекста);
 * при перепарсинге документа узлы получают новые идентичности, а старый бакет
 * URI сбрасывается по событию изменения содержимого.
 * <p>
 * Инвалидация — та же модель, что у {@link InferredVariableTypeIndex}: per-URI
 * через {@link AbstractDocumentLifecycleClearableIndex} (изменение / очистка
 * вторичных данных / закрытие / удаление документа) плюс полный сброс на
 * регистрацию конфигурационных типов. Кросс-документные зависимости типа при
 * правке чужого модуля не сбрасываются — как и в {@link InferredVariableTypeIndex}
 * / {@link SymbolTypeIndex}.
 */
@Component
@WorkspaceScope
public class InferredExpressionTypeIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<ParseTree, TypeSet>> typesByUri = new ConcurrentHashMap<>();

  /**
   * Кэшированный тип узла-выражения либо {@code null}, если ещё не вычислялся.
   *
   * @param uri  URI документа, которому принадлежит узел.
   * @param node AST-узел выражения.
   * @return выведенный тип или {@code null} при промахе кэша.
   */
  @Nullable
  public TypeSet get(URI uri, ParseTree node) {
    var byNode = typesByUri.get(uri);
    return byNode == null ? null : byNode.get(node);
  }

  /**
   * Запомнить выведенный тип узла-выражения.
   *
   * @param uri   URI документа, которому принадлежит узел.
   * @param node  AST-узел выражения.
   * @param types выведенный тип.
   */
  public void put(URI uri, ParseTree node, TypeSet types) {
    typesByUri.computeIfAbsent(uri, k -> new ConcurrentHashMap<>()).put(node, types);
  }

  @Override
  public void clear(URI uri) {
    typesByUri.remove(uri);
  }

  /**
   * Полный сброс после регистрации конфигурационных типов: до неё член-доступы
   * на конфигурационных типах инферились в пусто (реестр ещё не заполнен), и эти
   * «пустые» результаты надо пересчитать. Симметрично
   * {@link InferredVariableTypeIndex#handleConfigurationTypesRegistered}.
   */
  @EventListener
  public void handleConfigurationTypesRegistered(ConfigurationTypesRegisteredEvent event) {
    typesByUri.clear();
  }
}
