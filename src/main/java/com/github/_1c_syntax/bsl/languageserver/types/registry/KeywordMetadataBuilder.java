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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider.KeywordDescription;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Аккумулирует разбор записей {@code builtin-keywords.json} в {@link KeywordMetadata}:
 * плоский список имён + scope (для no-dot completion), сниппеты и описания
 * (включая {@code descriptionByParent}). Каждое имя добавляется по обоим
 * написаниям (ru-canonical и en-alias) lower-cased — независимо от того, какое
 * пишет пользователь.
 */
final class KeywordMetadataBuilder {

  private static final String FIELD_NAME = "name";
  private static final String FIELD_ALIAS = "alias";
  private static final String FIELD_CATEGORY = "category";
  private static final String FIELD_SNIPPET = "snippet";
  private static final String FIELD_DESCRIPTION = "description";
  private static final String FIELD_DESCRIPTION_EN = "descriptionEn";
  private static final String FIELD_DESCRIPTION_BY_PARENT = "descriptionByParent";
  private static final String LOCALE_RU = "ru";
  private static final String LOCALE_EN = "en";

  private final Predicate<String> includeInCompletion;
  private final Set<String> seen = new HashSet<>();
  private final List<String> keywords = new ArrayList<>();
  private final Map<String, LanguageKeywordSnippet> snippets = new HashMap<>();
  private final Map<String, KeywordDescription> descriptions = new HashMap<>();

  KeywordMetadataBuilder(Predicate<String> includeInCompletion) {
    this.includeInCompletion = includeInCompletion;
  }

  void add(Map<String, Object> entry) {
    var name = stringField(entry, FIELD_NAME);
    if (name.isBlank()) {
      return;
    }
    var alias = stringField(entry, FIELD_ALIAS);
    if (includeInCompletion.test(stringField(entry, FIELD_CATEGORY))) {
      register(name);
      if (!alias.isBlank()) {
        register(alias);
      }
    }
    indexSnippet(entry, name, alias);
    indexDescription(entry, name, alias);
  }

  KeywordMetadata build() {
    return new KeywordMetadata(List.copyOf(keywords),
      Map.copyOf(snippets), Map.copyOf(descriptions));
  }

  private void register(String written) {
    if (seen.add(written.toLowerCase(Locale.ROOT))) {
      keywords.add(written);
    }
  }

  private void indexSnippet(Map<String, Object> entry, String name, String alias) {
    var snippet = readSnippet(entry);
    if (snippet.isEmpty()) {
      return;
    }
    snippets.put(name.toLowerCase(Locale.ROOT), snippet);
    if (!alias.isBlank()) {
      snippets.put(alias.toLowerCase(Locale.ROOT), snippet);
    }
  }

  private void indexDescription(Map<String, Object> entry, String name, String alias) {
    var description = readKeywordDescription(entry);
    if (description.isEmpty()) {
      return;
    }
    descriptions.put(name.toLowerCase(Locale.ROOT), description);
    if (!alias.isBlank()) {
      descriptions.put(alias.toLowerCase(Locale.ROOT), description);
    }
  }

  private static LanguageKeywordSnippet readSnippet(Map<String, Object> entry) {
    var raw = asStringMap(entry.get(FIELD_SNIPPET));
    if (raw.isEmpty()) {
      return LanguageKeywordSnippet.EMPTY;
    }
    var ru = raw.getOrDefault(LOCALE_RU, "");
    var en = raw.getOrDefault(LOCALE_EN, "");
    if (ru.isEmpty() && en.isEmpty()) {
      return LanguageKeywordSnippet.EMPTY;
    }
    return new LanguageKeywordSnippet(ru, en);
  }

  private static KeywordDescription readKeywordDescription(Map<String, Object> entry) {
    var primary = BilingualString.of(
      stringField(entry, FIELD_DESCRIPTION),
      stringField(entry, FIELD_DESCRIPTION_EN));
    var byParent = readDescriptionByParent(entry.get(FIELD_DESCRIPTION_BY_PARENT));
    if (primary.isEmpty() && byParent.isEmpty()) {
      return KeywordDescription.EMPTY;
    }
    return new KeywordDescription(primary, byParent);
  }

  private static Map<String, BilingualString> readDescriptionByParent(@Nullable Object raw) {
    if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
      return Map.of();
    }
    var result = new LinkedHashMap<String, BilingualString>();
    for (var e : rawMap.entrySet()) {
      var pair = asStringMap(e.getValue());
      if (pair.isEmpty()) {
        continue;
      }
      var bi = BilingualString.of(
        pair.getOrDefault(LOCALE_RU, ""),
        pair.getOrDefault(LOCALE_EN, ""));
      if (!bi.isEmpty()) {
        result.put(String.valueOf(e.getKey()), bi);
      }
    }
    return result;
  }

  private static String stringField(Map<String, Object> entry, String key) {
    var value = entry.get(key);
    return value instanceof String s ? s : "";
  }

  /**
   * Sanitized read of a {@code Map<String,String>}-shaped value: для {@code Map}
   * собирает только записи со строковыми ключом и значением. Для не-Map'а или
   * для отсутствующего значения — пустая мапа (downstream избегает null-проверок).
   */
  private static Map<String, String> asStringMap(@Nullable Object raw) {
    if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
      return Map.of();
    }
    var result = LinkedHashMap.<String, String>newLinkedHashMap(rawMap.size());
    for (var e : rawMap.entrySet()) {
      if (e.getKey() instanceof String key && e.getValue() instanceof String value) {
        result.put(key, value);
      }
    }
    return result;
  }
}
