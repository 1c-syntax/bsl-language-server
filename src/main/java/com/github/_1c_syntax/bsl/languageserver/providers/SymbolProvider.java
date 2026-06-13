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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.types.index.WorkspaceSymbolIndex;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Провайдер для поиска символов в рабочей области.
 * <p>
 * Обрабатывает запросы {@code workspace/symbol}, делегируя поиск инкрементальному
 * {@link WorkspaceSymbolIndex}: индекс хранит уже подготовленные записи символов и
 * возвращает ранжированный top-N, поэтому провайдер лишь маппит записи в
 * {@link WorkspaceSymbol} без обхода всех документов.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_symbol">Workspace Symbols Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class SymbolProvider {

  private final WorkspaceSymbolIndex workspaceSymbolIndex;

  /**
   * Выполняет поиск символов рабочей области по запросу {@code workspace/symbol} с поддержкой отмены.
   * <p>
   * Поиск делегируется {@link WorkspaceSymbolIndex#search(String, int, CancelChecker)}: совпадения
   * ранжируются по релевантности (точное совпадение, префикс, подстрока, подпоследовательность),
   * а выдача усекается по скору до {@link WorkspaceSymbolIndex#MAX_RESULTS} — наиболее релевантные
   * символы остаются сверху. Отмена проверяется индексом периодически в ходе поиска: если клиент
   * отменил запрос, поиск прерывается исключением {@link java.util.concurrent.CancellationException}.
   *
   * @param params        Параметры запроса {@code workspace/symbol}, в т.ч. строка запроса
   * @param cancelChecker Проверяющий отмену запроса
   * @return Ранжированный список найденных символов рабочей области, не длиннее {@link WorkspaceSymbolIndex#MAX_RESULTS}
   */
  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params, CancelChecker cancelChecker) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    return workspaceSymbolIndex.search(queryString, WorkspaceSymbolIndex.MAX_RESULTS, cancelChecker).stream()
      .map(SymbolProvider::createWorkspaceSymbol)
      .toList();
  }

  /**
   * Строит {@link WorkspaceSymbol} из записи индекса.
   * <p>
   * Имя контейнера и теги уже вычислены на момент индексации, поэтому повторный обход дерева
   * символов не требуется. Пустое имя контейнера трактуется как «контейнер отсутствует».
   *
   * @param entry запись индекса символов рабочей области
   * @return заполненный символ рабочей области
   */
  private static WorkspaceSymbol createWorkspaceSymbol(WorkspaceSymbolIndex.Entry entry) {
    var location = new Location(entry.uri().toString(), entry.range());

    var workspaceSymbol = new WorkspaceSymbol();
    workspaceSymbol.setName(entry.name());
    workspaceSymbol.setKind(entry.kind());
    workspaceSymbol.setLocation(Either.forLeft(location));
    workspaceSymbol.setTags(entry.tags());
    if (!entry.containerName().isEmpty()) {
      workspaceSymbol.setContainerName(entry.containerName());
    }

    return workspaceSymbol;
  }
}
