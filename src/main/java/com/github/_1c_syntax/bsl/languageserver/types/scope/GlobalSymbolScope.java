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
package com.github._1c_syntax.bsl.languageserver.types.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальная область видимости символов workspace'а.
 *
 * <p>
 * Содержит имена, доступные из любого места без квалификации:
 * <ul>
 *   <li>платформенные глобалы ({@code Справочники}, {@code Истина}, {@code Сообщить} и т.п.)
 *       — как {@link com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol};</li>
 *   <li>OneScript-модули из {@code oscript_modules}/{@code lib.config}
 *       (например, {@code ФС}) — как обычные
 *       {@link com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol};</li>
 *   <li>OneScript-классы (имя класса доступно для {@code Новый}) — как
 *       {@link com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol}
 *       с ролью {@link Role#TYPE_NAME}.</li>
 * </ul>
 *
 * <p>
 * Один и тот же символ может быть зарегистрирован под разными именами
 * (Ru/En-алиасы). Имена резолвятся регистронезависимо.
 *
 * <p>
 * Заменяет namespace-индекс {@code TypeRegistry.namespaceIndex} и поля
 * {@code libraryModules}/{@code libraryClasses} из {@code GlobalScopeProvider}
 * единой Symbol-точкой входа.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class GlobalSymbolScope {

  /**
   * Семантическая роль глобальной записи.
   */
  public enum Role {
    /** Имя имеет значение и тип ({@code ФС}, {@code Справочники}). */
    VALUE,
    /** Имя — это имя типа, доступное в {@code Новый ИмяКласса()}. */
    TYPE_NAME
  }

  /** Запись scope'а: символ + его роль. */
  public record Entry(Symbol symbol, Role role) {
  }

  /** Имена → запись (ключ — lowercased). */
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  /** Исходные написания (для отображения). */
  private final Map<String, String> displayNames = new ConcurrentHashMap<>();
  /** Имя символа → список ключей, под которыми он зарегистрирован (для unregister). */
  private final Map<String, List<String>> aliasesBySymbol = new ConcurrentHashMap<>();

  /**
   * Зарегистрировать имя в global scope.
   *
   * @param name   имя (как пишется в коде); ru/en-алиасы регистрируются отдельными вызовами
   * @param symbol символ
   * @param role   роль ({@link Role#VALUE} для глобал-значений, {@link Role#TYPE_NAME} для имён классов)
   */
  public void register(String name, Symbol symbol, Role role) {
    if (name == null || name.isBlank() || symbol == null) {
      return;
    }
    var key = name.toLowerCase(Locale.ROOT);
    entries.put(key, new Entry(symbol, role));
    displayNames.putIfAbsent(key, name);
    aliasesBySymbol
      .computeIfAbsent(symbolKey(symbol), k -> Collections.synchronizedList(new java.util.ArrayList<>()))
      .add(key);
  }

  /**
   * Найти символ по имени (регистронезависимо).
   */
  public Optional<Symbol> findSymbol(String name) {
    return findEntry(name).map(Entry::symbol);
  }

  /**
   * Найти запись по имени (символ + роль).
   */
  public Optional<Entry> findEntry(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(entries.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * @return все зарегистрированные имена в исходном написании.
   */
  public Collection<String> getNames() {
    return entries.keySet().stream()
      .map(k -> displayNames.getOrDefault(k, k))
      .toList();
  }

  /**
   * @return все записи scope'а (без дублирующих алиасов).
   */
  public Collection<Entry> getEntries() {
    // Оставляем по одной записи на символ: первая регистрация — основное имя.
    return aliasesBySymbol.values().stream()
      .map(aliases -> entries.get(aliases.get(0)))
      .filter(java.util.Objects::nonNull)
      .toList();
  }

  /**
   * @return все уникальные символы scope'а (без дубликатов алиасов).
   */
  public java.util.stream.Stream<Symbol> streamSymbols() {
    return getEntries().stream().map(Entry::symbol);
  }

  /**
   * Удалить все имена, зарегистрированные за символом (по идентичности символа).
   */
  public void unregister(Symbol symbol) {
    if (symbol == null) {
      return;
    }
    var aliases = aliasesBySymbol.remove(symbolKey(symbol));
    if (aliases == null) {
      return;
    }
    for (var alias : aliases) {
      entries.remove(alias);
      displayNames.remove(alias);
    }
  }

  /**
   * Очистить scope (используется при полной переиндексации library-сущностей).
   *
   * @param role если указан — удаляются только записи с этой ролью; {@code null} — все.
   */
  public void clear(Role role) {
    if (role == null) {
      entries.clear();
      displayNames.clear();
      aliasesBySymbol.clear();
      return;
    }
    entries.entrySet().removeIf(e -> e.getValue().role() == role);
    aliasesBySymbol.entrySet().removeIf(e -> e.getValue().stream().allMatch(k -> !entries.containsKey(k)));
    displayNames.keySet().removeIf(k -> !entries.containsKey(k));
  }

  private static String symbolKey(Symbol symbol) {
    return System.identityHashCode(symbol) + ":" + symbol.getName();
  }
}
