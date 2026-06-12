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

  /**
   * Записи каждого языка по отдельности (ключ внутренней мапы — lowercased имя).
   * Одно имя может присутствовать в обоих разрезах: например,
   * {@code КодировкаТекста} зарегистрирована и BSL-паком, и oscript-паком
   * с разными описаниями — читается разрез языка файла-потребителя.
   */
  private final Map<FileType, Map<String, Entry>> entries = Map.of(
    FileType.BSL, new ConcurrentHashMap<>(),
    FileType.OS, new ConcurrentHashMap<>()
  );
  /** Исходные написания (для отображения). */
  private final Map<String, String> displayNames = new ConcurrentHashMap<>();
  /** Имя символа → список ключей, под которыми он зарегистрирован (для unregister). */
  private final Map<String, List<String>> aliasesBySymbol = new ConcurrentHashMap<>();

  /**
   * Зарегистрировать имя в global scope с привязкой к языку файлов.
   * Повторная регистрация имени с тем же языком заменяет предыдущую запись;
   * с другим языком — добавляет языковой вариант (выбор при чтении —
   * {@link #findEntry(String, FileType)}). Сущность, видимая в обоих языках,
   * регистрируется двумя вызовами.
   *
   * @param name     имя (как пишется в коде); ru/en-алиасы регистрируются отдельными вызовами
   * @param symbol   символ
   * @param role     роль ({@link Role#VALUE} для глобал-значений, {@link Role#TYPE_NAME} для имён классов)
   * @param fileType язык файлов, в которых имя видимо
   */
  public void register(String name, Symbol symbol, Role role, FileType fileType) {
    if (name == null || name.isBlank() || symbol == null) {
      return;
    }
    var key = name.toLowerCase(Locale.ROOT);
    entries.get(fileType).put(key, new Entry(symbol, role));
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
   * Найти запись по имени (символ + роль), без фильтрации по типу файла:
   * первый найденный языковой вариант (в порядке BSL, OS).
   */
  public Optional<Entry> findEntry(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var key = name.toLowerCase(Locale.ROOT);
    for (var fileType : FileType.values()) {
      var entry = entries.get(fileType).get(key);
      if (entry != null) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  /**
   * Найти запись по имени в разрезе указанного языка.
   *
   * @param name     имя (регистронезависимо)
   * @param fileType тип файла-потребителя
   * @return запись данного языка; empty, если имя в этом языке не видимо
   */
  public Optional<Entry> findEntry(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(entries.get(fileType).get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * @return все зарегистрированные имена в исходном написании.
   */
  public Collection<String> getNames() {
    return entries.values().stream()
      .flatMap(byName -> byName.keySet().stream())
      .distinct()
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
    for (var byName : entries.values()) {
      for (var entry : byName.values()) {
        if (seen.add(entry.symbol())) {
          result.add(entry);
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
      for (var byName : entries.values()) {
        var entry = byName.get(alias);
        if (entry != null && entry.symbol() == symbol) {
          byName.remove(alias);
        }
      }
      if (!isRegisteredName(alias)) {
        displayNames.remove(alias);
      }
    }
  }

  /**
   * Очистить весь scope (используется при полной переиндексации library-сущностей).
   */
  public void clear() {
    entries.values().forEach(Map::clear);
    displayNames.clear();
    aliasesBySymbol.clear();
  }

  /**
   * Очистить только записи с указанной ролью.
   *
   * @param role роль, по которой фильтруются удаляемые записи
   */
  public void clear(Role role) {
    for (var byName : entries.values()) {
      byName.values().removeIf(entry -> entry.role() == role);
    }
    aliasesBySymbol.entrySet().removeIf(e -> e.getValue().stream().noneMatch(this::isRegisteredName));
    displayNames.keySet().removeIf(k -> !isRegisteredName(k));
  }

  private boolean isRegisteredName(String key) {
    return entries.values().stream().anyMatch(byName -> byName.containsKey(key));
  }

  private static String symbolKey(Symbol symbol) {
    return System.identityHashCode(symbol) + ":" + symbol.getName();
  }
}
