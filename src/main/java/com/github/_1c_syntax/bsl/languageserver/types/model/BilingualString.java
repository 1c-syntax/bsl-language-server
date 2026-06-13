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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;

/**
 * Двуязычная (ru + en) пара строк. Используется для всех 1С-сущностей с
 * детерминированной двуязычностью: имена методов, параметров, типов,
 * описаний и т.п. Источник — {@code bsl-context} {@code ContextName} либо
 * JSON-fallback с явными полями {@code nameRu}/{@code nameEn}.
 * <p>
 * Поведение:
 * <ul>
 *   <li>{@link #forLanguage(Language)} — детерминированно отдаёт ru или en по
 *       LS-локали; если для выбранной локали поле пусто — возвращает
 *       непустое из оставшихся; если оба пусты — пустую строку.</li>
 *   <li>{@link #matches(String)} — case-insensitive проверка по обеим
 *       локалям (lookup независимо от того, какую раскладку пользователь
 *       набирает).</li>
 *   <li>{@link #primary()} — ru, если есть; иначе en (для legacy-callsite'ов,
 *       которые ожидали одиночное "канонически-русское" имя).</li>
 * </ul>
 *
 * @param ru русское написание (пустая строка, если отсутствует)
 * @param en английское написание (пустая строка, если отсутствует)
 */
public record BilingualString(String ru, String en) {

  public static final BilingualString EMPTY = new BilingualString("", "");

  public BilingualString {
    ru = ru == null ? "" : ru;
    en = en == null ? "" : en;
  }

  /**
   * Одноязычная строка — кладётся в {@code ru}; {@code en} пуст.
   */
  public static BilingualString of(String singleLocale) {
    return singleLocale == null || singleLocale.isEmpty()
      ? EMPTY
      : new BilingualString(singleLocale, "");
  }

  public static BilingualString of(String ru, String en) {
    if ((ru == null || ru.isEmpty()) && (en == null || en.isEmpty())) {
      return EMPTY;
    }
    return new BilingualString(ru, en);
  }

  public boolean isEmpty() {
    return ru.isEmpty() && en.isEmpty();
  }

  /**
   * Возвращает строку для отображения в указанной локали. Если для неё
   * поле пусто — fallback на другое непустое поле.
   */
  public String forLanguage(Language language) {
    if (language == Language.EN) {
      return en.isEmpty() ? ru : en;
    }
    return ru.isEmpty() ? en : ru;
  }

  /**
   * @return ru, если непусто; иначе en. Для legacy-callsite'ов и compat.
   */
  public String primary() {
    return ru.isEmpty() ? en : ru;
  }

  /**
   * Есть ли у строки написание в указанной локали. Используется для отбора
   * применимости сущности (например, члена типа) к языку: двуязычные и
   * нейтральные строки (оба слота заполнены) применимы к обеим локалям, а
   * одноязычные (заполнен только один слот) — лишь к своей.
   */
  public boolean hasLanguage(Language language) {
    return language == Language.EN ? !en.isEmpty() : !ru.isEmpty();
  }

  /**
   * Case-insensitive сравнение с {@code candidate} по обеим локалям.
   */
  public boolean matches(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return false;
    }
    return (!ru.isEmpty() && ru.equalsIgnoreCase(candidate))
      || (!en.isEmpty() && en.equalsIgnoreCase(candidate));
  }
}
