/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Провайдер для предоставления семантических токенов.
 * <p>
 * Обрабатывает запросы {@code textDocument/semanticTokens/full}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens">Semantic Tokens specification</a>
 */
@Component
@RequiredArgsConstructor
public class SemanticTokensProvider {

  private final List<SemanticTokensSupplier> suppliers;

  /**
   * Получить семантические токены для всего документа.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return Семантические токены в дельта-кодированном формате
   */
  public SemanticTokens getSemanticTokensFull(DocumentContext documentContext, @SuppressWarnings("unused") SemanticTokensParams params) {
    // Collect tokens from all suppliers
    List<SemanticTokenEntry> entries = suppliers.stream()
      .map(supplier -> supplier.getSemanticTokens(documentContext))
      .flatMap(Collection::stream)
      .toList();

    // Build delta-encoded data
    List<Integer> data = toDeltaEncoded(entries);
    return new SemanticTokens(data);
  }

  private static List<Integer> toDeltaEncoded(List<SemanticTokenEntry> entries) {
    // de-dup and sort
    Set<SemanticTokenEntry> uniq = new HashSet<>(entries);
    List<SemanticTokenEntry> sorted = new ArrayList<>(uniq);
    sorted.sort(Comparator
      .comparingInt(SemanticTokenEntry::line)
      .thenComparingInt(SemanticTokenEntry::start));

    List<Integer> data = new ArrayList<>(sorted.size() * 5);
    var prevLine = 0;
    var prevChar = 0;
    var first = true;

    for (SemanticTokenEntry tokenEntry : sorted) {
      int deltaLine = first ? tokenEntry.line() : (tokenEntry.line() - prevLine);
      int prevCharOrZero = (deltaLine == 0) ? prevChar : 0;
      int deltaStart = first ? tokenEntry.start() : (tokenEntry.start() - prevCharOrZero);

      data.add(deltaLine);
      data.add(deltaStart);
      data.add(tokenEntry.length());
      data.add(tokenEntry.type());
      data.add(tokenEntry.modifiers());

      prevLine = tokenEntry.line();
      prevChar = tokenEntry.start();
      first = false;
    }
    return data;
  }
}
