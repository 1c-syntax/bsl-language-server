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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
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
@WorkspaceScope
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

  /**
   * Запись scope'а: символ + его роль.
   *
   * @param symbol символ, под которым зарегистрировано имя
   * @param role   семантическая роль имени
   */
  public record Entry(Symbol symbol, Role role) {
  }

  /** Запись вместе с её языковым скоупом. */
  private record ScopedEntry(Entry entry, LanguageScope scope) {
  }

  /**
   * Имена → записи (ключ — lowercased). Одно имя может иметь НЕСКОЛЬКО записей
   * с разными языковыми скоупами: например, {@code КодировкаТекста}
   * зарегистрирована и BSL-паком, и oscript-паком с разными описаниями —
   * выбор варианта делается при чтении по типу файла.
   */
  private final Map<String, List<ScopedEntry>> entries = new ConcurrentHashMap<>();
  /** Исходные написания (для отображения). */
  private final Map<String, String> displayNames = new ConcurrentHashMap<>();
  /** Имя символа → список ключей, под которыми он зарегистрирован (для unregister). */
  private final Map<String, List<String>> aliasesBySymbol = new ConcurrentHashMap<>();

  /**
   * Зарегистрировать имя в global scope со скоупом {@link LanguageScope#BOTH}.
   *
   * @param name   имя (как пишется в коде); ru/en-алиасы регистрируются отдельными вызовами
   * @param symbol символ
   * @param role   роль ({@link Role#VALUE} для глобал-значений, {@link Role#TYPE_NAME} для имён классов)
   */
  public void register(String name, Symbol symbol, Role role) {
    register(name, symbol, role, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #register(String, Symbol, Role)}, но с явным языковым скоупом.
   * Повторная регистрация имени с тем же скоупом заменяет предыдущую запись;
   * с другим скоупом — добавляет языковой вариант (выбор при чтении —
   * {@link #findEntry(String, FileType)}).
   *
   * @param name   имя (как пишется в коде); ru/en-алиасы регистрируются отдельными вызовами
   * @param symbol символ
   * @param role   роль ({@link Role#VALUE} для глобал-значений, {@link Role#TYPE_NAME} для имён классов)
   * @param scope  языковой скоуп записи
   */
  public void register(String name, Symbol symbol, Role role, LanguageScope scope) {
    if (name == null || name.isBlank() || symbol == null) {
      return;
    }
    var key = name.toLowerCase(Locale.ROOT);
    var list = entries.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (list) {
      list.removeIf(se -> se.scope() == scope);
      list.add(new ScopedEntry(new Entry(symbol, role), scope));
    }
    displayNames.putIfAbsent(key, name);
    aliasesBySymbol
      .computeIfAbsent(symbolKey(symbol), k -> Collections.synchronizedList(new ArrayList<>()))
      .add(key);
  }

  /**
   * Найти символ по имени (регистронезависимо), без фильтрации по типу файла.
   */
  public Optional<Symbol> findSymbol(String name) {
    return findEntry(name).map(Entry::symbol);
  }

  /**
   * Найти запись по имени (символ + роль), без фильтрации по типу файла.
   * Если имя имеет несколько языковых вариантов — возвращается первый
   * зарегистрированный.
   */
  public Optional<Entry> findEntry(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var list = entries.get(name.toLowerCase(Locale.ROOT));
    if (list == null) {
      return Optional.empty();
    }
    synchronized (list) {
      return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0).entry());
    }
  }

  /**
   * Найти запись по имени с выбором языкового варианта по типу файла:
   * сперва запись с точным скоупом ({@link LanguageScope#BSL} для BSL-файла,
   * {@link LanguageScope#OS} для OS-файла), затем — совместимая
   * ({@link LanguageScope#BOTH}).
   *
   * @param name     имя (регистронезависимо)
   * @param fileType тип файла-потребителя
   * @return запись подходящего языкового варианта; empty, если ни одна
   *         запись не видима в данном типе файла
   */
  public Optional<Entry> findEntry(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var list = entries.get(name.toLowerCase(Locale.ROOT));
    if (list == null) {
      return Optional.empty();
    }
    var exact = fileType == FileType.OS ? LanguageScope.OS : LanguageScope.BSL;
    synchronized (list) {
      return list.stream()
        .filter(se -> se.scope() == exact)
        .findFirst()
        .or(() -> list.stream().filter(se -> se.scope().matches(fileType)).findFirst())
        .map(ScopedEntry::entry);
    }
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
   * @return все записи scope'а (без дублирующих алиасов и языковых вариантов:
   *         по одной записи на уникальный символ).
   */
  public Collection<Entry> getEntries() {
    var seen = Collections.newSetFromMap(new IdentityHashMap<Symbol, Boolean>());
    var result = new ArrayList<Entry>();
    for (var list : entries.values()) {
      synchronized (list) {
        for (var se : list) {
          if (seen.add(se.entry().symbol())) {
            result.add(se.entry());
          }
        }
      }
    }
    return result;
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
  public void unregister(@Nullable Symbol symbol) {
    if (symbol == null) {
      return;
    }
    var aliases = aliasesBySymbol.remove(symbolKey(symbol));
    if (aliases == null) {
      return;
    }
    for (var alias : aliases) {
      var list = entries.get(alias);
      if (list != null) {
        synchronized (list) {
          list.removeIf(se -> se.entry().symbol() == symbol);
          if (list.isEmpty()) {
            entries.remove(alias);
            displayNames.remove(alias);
          }
        }
      }
    }
  }

  /**
   * Очистить весь scope (используется при полной переиндексации library-сущностей).
   */
  public void clear() {
    entries.clear();
    displayNames.clear();
    aliasesBySymbol.clear();
  }

  /**
   * Очистить только записи с указанной ролью.
   *
   * @param role роль, по которой фильтруются удаляемые записи
   */
  public void clear(Role role) {
    for (var list : entries.values()) {
      synchronized (list) {
        list.removeIf(se -> se.entry().role() == role);
      }
    }
    entries.entrySet().removeIf(e -> e.getValue().isEmpty());
    aliasesBySymbol.entrySet().removeIf(e -> e.getValue().stream().noneMatch(entries::containsKey));
    displayNames.keySet().removeIf(k -> !entries.containsKey(k));
  }

  private static String symbolKey(Symbol symbol) {
    return System.identityHashCode(symbol) + ":" + symbol.getName();
  }
}
