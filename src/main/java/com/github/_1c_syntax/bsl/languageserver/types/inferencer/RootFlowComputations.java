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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Координация независимых внешних запросов; сами значения принадлежат обычному кешу потока. */
final class RootFlowComputations {
  /** Общий для workspace-экземпляров признак: владелец расчёта никогда не ждёт другой расчёт. */
  private static final ThreadLocal<Boolean> OWNER = new ThreadLocal<>();
  private final Map<Key, CompletableFuture<Void>> running = new ConcurrentHashMap<>();

  private record Key(URI uri, Object source, @Nullable ExecutionContext context) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Key(var otherUri, var otherSource, var otherContext)
        && uri.equals(otherUri) && source == otherSource && context == otherContext;
    }

    @Override
    public int hashCode() {
      return 31 * (31 * uri.hashCode() + System.identityHashCode(source)) + (context == null ? 0 : context.hashCode());
    }
  }

  <T> T compute(URI uri, BSLParser.CodeBlockContext body, @Nullable ExecutionContext context,
                Supplier<@Nullable T> cached, Supplier<T> calculation) {
    return this.<T>computeSource(uri, body, context, cached, calculation);
  }

  <T> T computeCells(URI uri, SymbolTree tree, @Nullable ExecutionContext context,
                     Supplier<@Nullable T> cached, Supplier<T> calculation) {
    return this.<T>computeSource(uri, tree, context, cached, calculation);
  }

  private <T> T computeSource(URI uri, Object source, @Nullable ExecutionContext context,
                              Supplier<@Nullable T> cached, Supplier<T> calculation) {
    var ready = cached.get();
    if (ready != null) {
      return ready;
    }
    // Даже запрос с новой локальной сессией может быть вложен во внешний расчёт этого
    // потока. Владелец идёт самостоятельным путём: так A -> B и B -> A не ждут друг друга.
    if (OWNER.get() != null) {
      return calculation.get();
    }
    var key = new Key(uri, source, context);
    var completed = new CompletableFuture<Void>();
    var existing = running.putIfAbsent(key, completed);
    if (existing != null) {
      existing.join();
      // Передаётся только сигнал завершения. Неполный или сорвавшийся результат не
      // публикуется обычным кешем, поэтому следующий запрос должен посчитать его заново.
      ready = cached.get();
      if (ready != null) {
        return ready;
      }
      OWNER.set(Boolean.TRUE);
      try {
        return calculation.get();
      } finally {
        OWNER.remove();
      }
    }
    OWNER.set(Boolean.TRUE);
    try {
      ready = cached.get();
      return ready != null ? ready : calculation.get();
    } finally {
      OWNER.remove();
      running.remove(key, completed);
      completed.complete(null);
    }
  }
}
