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

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider.KeywordDescription;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * JSON-fallback парсер встроенных языковых конструкций ({@code builtin-keywords.json}
 * для BSL и {@code builtin-oscript-keywords.json} для OS). Используется, когда
 * {@code bsl-context} недоступен — у пользователя нет установленной 1С / oscript-only
 * сборка / тесты.
 * <p>
 * Формат записи: {@code name} обязательный, опциональные {@code alias},
 * {@code category}, {@code description}, {@code descriptionEn},
 * {@code snippet.ru/en}, {@code descriptionByParent.<parent>.{ru,en}}.
 * <p>
 * В плоский список keywords (для no-dot completion) попадают только те, чья
 * категория проходит фильтр {@code includeInCompletion} (по соглашению с
 * bsl-context — LITERAL/STATEMENT/OPERATOR/DECLARATION). PRAGMA/ANNOTATION/
 * PREPROCESSOR_INSTRUCTION не дают completion-кандидата, но их сниппеты и
 * описания всё равно индексируются — нужны hover'у в соответствующих
 * синтаксических контекстах.
 */
@Slf4j
final class KeywordMetadataLoader {

  private static final String FIELD_NAME = "name";
  private static final String FIELD_ALIAS = "alias";
  private static final String FIELD_CATEGORY = "category";
  private static final String FIELD_SNIPPET = "snippet";
  private static final String FIELD_DESCRIPTION = "description";
  private static final String FIELD_DESCRIPTION_EN = "descriptionEn";
  private static final String FIELD_DESCRIPTION_BY_PARENT = "descriptionByParent";
  private static final String FIELD_KEYWORDS = "keywords";
  private static final String LOCALE_RU = "ru";
  private static final String LOCALE_EN = "en";

  private KeywordMetadataLoader() {
  }

  /**
   * Загружает {@code builtin-keywords.json}-подобный ресурс и возвращает
   * {@link KeywordMetadata}. При отсутствии/ошибке ресурса — {@link KeywordMetadata#EMPTY}.
   *
   * @param resourcePath          путь к JSON-ресурсу на classpath
   * @param scope                 язык, в чей scope попадают зарегистрированные имена
   * @param includeInCompletion   фильтр категорий, которые попадают в плоский список
   *                              (для no-dot completion)
   */
  static KeywordMetadata load(String resourcePath, LanguageScope scope,
                              Predicate<String> includeInCompletion) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      var root = readRoot(mapper, stream);
      var raw = readKeywordEntries(root);
      var builder = new Builder(scope, includeInCompletion);
      for (var entry : raw) {
        builder.add(entry);
      }
      return builder.build();
    } catch (IOException e) {
      LOGGER.warn("Builtin keywords resource not found or unreadable: {}", resourcePath, e);
      return KeywordMetadata.EMPTY;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readRoot(JsonMapper mapper, java.io.InputStream stream)
    throws IOException {
    return mapper.readValue(stream, Map.class);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> readKeywordEntries(Map<String, Object> root) {
    return (List<Map<String, Object>>) root.getOrDefault(FIELD_KEYWORDS, Collections.emptyList());
  }

  /**
   * Аккумулирует разбор записей JSON в {@link KeywordMetadata}: плоский список
   * имён + scope, snippet'ы и описания (включая {@code descriptionByParent}).
   */
  private static final class Builder {
    private final LanguageScope scope;
    private final Predicate<String> includeInCompletion;
    private final Set<String> seen = new HashSet<>();
    private final List<String> keywords = new ArrayList<>();
    private final Map<String, LanguageScope> scopes = new HashMap<>();
    private final Map<String, LanguageKeywordSnippet> snippets = new HashMap<>();
    private final Map<String, KeywordDescription> descriptions = new HashMap<>();

    Builder(LanguageScope scope, Predicate<String> includeInCompletion) {
      this.scope = scope;
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
      return new KeywordMetadata(List.copyOf(keywords), Map.copyOf(scopes),
        Map.copyOf(snippets), Map.copyOf(descriptions));
    }

    private void register(String written) {
      var lc = written.toLowerCase(Locale.ROOT);
      if (seen.add(lc)) {
        keywords.add(written);
        scopes.put(lc, scope);
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
      if (raw == null) {
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

    @SuppressWarnings("unchecked")
    private static Map<String, BilingualString> readDescriptionByParent(@Nullable Object raw) {
      if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
        return Map.of();
      }
      var result = new LinkedHashMap<String, BilingualString>();
      for (var e : ((Map<String, Map<String, String>>) raw).entrySet()) {
        var pair = e.getValue();
        if (pair == null) {
          continue;
        }
        var bi = BilingualString.of(
          pair.getOrDefault(LOCALE_RU, ""),
          pair.getOrDefault(LOCALE_EN, ""));
        if (!bi.isEmpty()) {
          result.put(e.getKey(), bi);
        }
      }
      return result;
    }

    private static String stringField(Map<String, Object> entry, String key) {
      var value = entry.get(key);
      return value instanceof String s ? s : "";
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, String> asStringMap(@Nullable Object raw) {
      return raw instanceof Map<?, ?> ? (Map<String, String>) raw : null;
    }
  }
}
