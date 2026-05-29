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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.Placeholder;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лёгкая ссылка-ключ на тип.
 * <p>
 * Идентичность типа определяется парой {@code (kind, qualifiedName)}.
 * Создаётся через {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry#intern(TypeKind, String)}
 * для канонических (интернированных) инстансов — это позволяет хранить
 * информацию о типах в индексе как одну ссылку на JVM.
 *
 * @param kind          категория типа
 * @param qualifiedName каноническое полное имя (например, {@code "Массив"},
 *                      {@code "Справочники.Контрагенты"}, mdoRef для общего модуля)
 */
public record TypeRef(TypeKind kind, String qualifiedName) implements Comparable<TypeRef> {

  public static final TypeRef UNKNOWN = new TypeRef(TypeKind.UNKNOWN, "Unknown");
  public static final TypeRef ANY = new TypeRef(TypeKind.ANY, "Any");

  /**
   * Глобальный кэш разобранных placeholder'ов по qualifiedName. Парсинг
   * угловых скобок выполняется один раз на каждое уникальное имя, дальше
   * {@link #specialize(TypeRef, Map)} работает со структурным представлением.
   * <p>
   * Размер кэша ограничен числом уникальных qualifiedName в системе
   * (для платформы 1С — сотни). Не освобождается — пожизненно на JVM.
   */
  private static final Map<String, List<Placeholder>> PLACEHOLDER_CACHE = new ConcurrentHashMap<>();

  /**
   * Каноническое (регистронезависимое) представление имени, используемое для
   * сравнения в {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry}.
   *
   * @return имя в нижнем регистре по {@link Locale#ROOT}
   */
  public String canonicalKey() {
    return qualifiedName.toLowerCase(Locale.ROOT);
  }

  /**
   * Краткое имя без квалификатора (часть после последней точки), удобно для
   * отображения в hover/completion.
   *
   * @return простое имя
   */
  public String simpleName() {
    var dot = qualifiedName.lastIndexOf('.');
    return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
  }

  /**
   * Структурное представление generic-плейсхолдеров в {@link #qualifiedName}.
   * Парсится один раз и кэшируется. Для не-generic типов — пустой список.
   * <p>
   * Используется {@link #specialize(TypeRef, Map)} для замены placeholder'ов
   * по позиции, без повторного парсинга угловых скобок.
   *
   * @return неизменяемый список placeholder'ов; пустой, если их нет
   */
  public List<Placeholder> placeholders() {
    return PLACEHOLDER_CACHE.computeIfAbsent(qualifiedName, ContextNames::placeholders);
  }

  /**
   * Применяет {@code bindings} (placeholder → имя заменителя) к {@link TypeRef}.
   * Подмена выполняется структурно: позиции placeholder'ов берутся из
   * {@link #placeholders()}, парсинг угловых скобок в качестве замены здесь
   * не повторяется. Соответствие имени placeholder'а ключу {@code bindings}
   * — регистронезависимое.
   * <p>
   * Если в qualifiedName нет placeholder'ов или ни один не входит в
   * {@code bindings} — возвращает тот же объект ({@code this}-эквивалент)
   * без аллокации. {@link #UNKNOWN} никогда не меняется.
   *
   * @param ref       исходный ref (может быть {@code null}/UNKNOWN)
   * @param bindings  подстановки имя placeholder'а (без угловых скобок) →
   *                  имя заменителя; пустой map — no-op
   * @return специализированный {@link TypeRef} или исходный
   */
  public static TypeRef specialize(TypeRef ref, Map<String, String> bindings) {
    if (ref == null || ref.equals(UNKNOWN) || bindings == null || bindings.isEmpty()) {
      return ref;
    }
    var placeholders = ref.placeholders();
    if (placeholders.isEmpty()) {
      return ref;
    }
    var sb = new StringBuilder(ref.qualifiedName.length());
    int cursor = 0;
    boolean anyReplaced = false;
    for (var placeholder : placeholders) {
      var replacement = lookup(bindings, placeholder.name());
      if (replacement == null) {
        // placeholder без соответствия в bindings — оставляем как есть
        // (на случай частичной специализации).
        sb.append(ref.qualifiedName, cursor, placeholder.end());
      } else {
        sb.append(ref.qualifiedName, cursor, placeholder.start())
          .append(replacement);
        anyReplaced = true;
      }
      cursor = placeholder.end();
    }
    if (!anyReplaced) {
      return ref;
    }
    sb.append(ref.qualifiedName, cursor, ref.qualifiedName.length());
    return new TypeRef(ref.kind, sb.toString());
  }

  /**
   * Применяет {@code bindings} к каждому {@link TypeRef} в {@link TypeSet}.
   * Возвращает исходный TypeSet, если ни один ref не специализировался.
   */
  public static TypeSet specialize(TypeSet typeSet, Map<String, String> bindings) {
    if (typeSet == null || typeSet.isEmpty() || bindings == null || bindings.isEmpty()) {
      return typeSet;
    }
    var rebuilt = new ArrayList<TypeRef>(typeSet.refs().size());
    boolean changed = false;
    for (var ref : typeSet.refs()) {
      var specialized = specialize(ref, bindings);
      if (specialized != ref) {
        changed = true;
      }
      rebuilt.add(specialized);
    }
    return changed ? TypeSet.of(rebuilt) : typeSet;
  }

  @Nullable
  private static String lookup(Map<String, String> bindings, String placeholder) {
    var key = placeholder.toLowerCase(Locale.ROOT);
    for (var entry : bindings.entrySet()) {
      if (entry.getKey().toLowerCase(Locale.ROOT).equals(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Порядок по {@code qualifiedName}, затем по {@code kind} — детерминированный
   * и согласованный с {@link #equals}. Нужен для использования {@link TypeRef}
   * как ключа в упорядоченных структурах.
   */
  @Override
  public int compareTo(TypeRef other) {
    var byName = qualifiedName.compareTo(other.qualifiedName);
    return byName != 0 ? byName : kind.compareTo(other.kind);
  }
}
