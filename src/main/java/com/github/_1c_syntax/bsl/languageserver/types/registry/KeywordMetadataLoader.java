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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static final String FIELD_KEYWORDS = "keywords";
  private static final TypeReference<Map<String, Object>> ROOT_TYPE = new TypeReference<>() {
  };

  private KeywordMetadataLoader() {
  }

  /**
   * Загружает {@code builtin-keywords.json}-подобный ресурс и возвращает
   * {@link KeywordMetadata}. При отсутствии/ошибке ресурса — {@link KeywordMetadata#EMPTY}.
   *
   * @param resourcePath          путь к JSON-ресурсу на classpath
   * @param includeInCompletion   фильтр категорий, которые попадают в плоский список
   *                              (для no-dot completion)
   */
  static KeywordMetadata load(String resourcePath,
                              Predicate<String> includeInCompletion) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      var root = mapper.readValue(stream, ROOT_TYPE);
      var builder = new KeywordMetadataBuilder(includeInCompletion);
      for (var entry : keywordEntries(root)) {
        builder.add(entry);
      }
      return builder.build();
    } catch (IOException e) {
      LOGGER.warn("Builtin keywords resource not found or unreadable: {}", resourcePath, e);
      return KeywordMetadata.EMPTY;
    }
  }

  /**
   * Возвращает узел {@code keywords} как список Map'ов. Для каждой записи
   * строится новый {@code Map<String,Object>} проходом по {@code entrySet()}
   * со строгой {@code instanceof String}-проверкой ключа — без unchecked-cast'a
   * raw-мапы Jackson'a; не-Map'ы и записи без string-ключей отсеиваются.
   */
  private static List<Map<String, Object>> keywordEntries(Map<String, Object> root) {
    var raw = root.getOrDefault(FIELD_KEYWORDS, Collections.emptyList());
    if (!(raw instanceof List<?> rawList)) {
      return List.of();
    }
    var result = new ArrayList<Map<String, Object>>(rawList.size());
    for (var item : rawList) {
      if (item instanceof Map<?, ?> rawMap) {
        result.add(asEntryMap(rawMap));
      }
    }
    return result;
  }

  /** Узкая helper-обёртка: новый Map со строковыми ключами, без cast'a. */
  private static Map<String, Object> asEntryMap(Map<?, ?> rawMap) {
    var result = HashMap.<String, Object>newHashMap(rawMap.size());
    for (var e : rawMap.entrySet()) {
      if (e.getKey() instanceof String key) {
        result.put(key, e.getValue());
      }
    }
    return result;
  }
}
