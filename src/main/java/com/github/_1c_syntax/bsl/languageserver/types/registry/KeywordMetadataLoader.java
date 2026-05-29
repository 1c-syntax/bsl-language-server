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
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Collections;
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
   * @param scope                 язык, в чей scope попадают зарегистрированные имена
   * @param includeInCompletion   фильтр категорий, которые попадают в плоский список
   *                              (для no-dot completion)
   */
  static KeywordMetadata load(String resourcePath, LanguageScope scope,
                              Predicate<String> includeInCompletion) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      var root = mapper.readValue(stream, ROOT_TYPE);
      var builder = new KeywordMetadataBuilder(scope, includeInCompletion);
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
   * Возвращает узел {@code keywords} как список Map'ов; не-Map записи
   * отсеиваются (downstream получает только корректные объекты).
   */
  private static List<Map<String, Object>> keywordEntries(Map<String, Object> root) {
    var raw = root.getOrDefault(FIELD_KEYWORDS, Collections.emptyList());
    if (!(raw instanceof List<?> rawList)) {
      return List.of();
    }
    return rawList.stream()
      .filter(Map.class::isInstance)
      .<Map<String, Object>>map(KeywordMetadataLoader::asEntryMap)
      .toList();
  }

  /**
   * Узкая helper-обёртка над {@code Map.class}-cast: каждая запись JSON-массива
   * {@code keywords} приходит как {@code Map<String,Object>} с string-ключами
   * (определено Jackson'ом для JSON-объекта); здесь только сужение типа без
   * проверок (фильтр {@code Map.class::isInstance} стоит выше).
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> asEntryMap(Object raw) {
    return (Map<String, Object>) raw;
  }
}
