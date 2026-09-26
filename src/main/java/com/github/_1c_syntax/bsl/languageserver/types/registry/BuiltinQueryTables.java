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

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Встроенный пак таблиц языка запросов — то, чем подменяется синтакс-помощник,
 * когда 1С не установлена.
 * <p>
 * Пак снят с самой справки генератором {@code BuiltinQueryTablesGeneratorTest} и
 * несёт то, из чего складываются колонки: имена таблиц и полей в обоих
 * написаниях, типы значений и признак корреспонденции. Описаний в нём нет —
 * они занимают больше самих данных, а подсказке без установленной 1С хватает
 * имени и типа.
 * <p>
 * Отдаётся пак теми же интерфейсами, что и справка, поэтому дальше по коду
 * пути «с платформой» и «без платформы» неразличимы.
 */
@Slf4j
final class BuiltinQueryTables {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-query-tables.json";

  private static final String NAME_KEY = "name";
  private static final String NAME_EN_KEY = "nameEn";

  private static List<ContextQueryTable> cache;

  private BuiltinQueryTables() {
  }

  /**
   * Таблицы из встроенного пака.
   *
   * @return таблицы; пусто, если ресурс не прочитался.
   */
  static synchronized List<ContextQueryTable> load() {
    if (cache == null) {
      cache = List.copyOf(read());
    }
    return cache;
  }

  private static List<ContextQueryTable> read() {
    try (var stream = BuiltinQueryTables.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
      if (stream == null) {
        LOGGER.warn("Встроенный пак таблиц языка запросов не найден: {}", RESOURCE_PATH);
        return List.of();
      }
      List<?> raw = JsonMapper.builder().build().readValue(stream, List.class);
      return raw.stream()
        .filter(Map.class::isInstance)
        .map(rawTable -> table((Map<?, ?>) rawTable))
        .toList();
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Не удалось прочитать встроенный пак таблиц языка запросов", e);
      return List.of();
    }
  }

  private static ContextQueryTable table(Map<?, ?> raw) {
    var fields = new ArrayList<ContextQueryTableField>();
    if (raw.get("fields") instanceof List<?> rawFields) {
      for (var rawField : rawFields) {
        if (rawField instanceof Map<?, ?> rawFieldEntries) {
          fields.add(field(rawFieldEntries));
        }
      }
    }
    var correspondence = raw.get("correspondence") instanceof Boolean value
      ? Optional.of(value)
      : Optional.<Boolean>empty();
    return new PackTable(name(raw), List.copyOf(fields), correspondence);
  }

  private static ContextQueryTableField field(Map<?, ?> raw) {
    var types = new ArrayList<Context>();
    if (raw.get("types") instanceof List<?> rawTypes) {
      for (var rawType : rawTypes) {
        if (rawType instanceof String typeName && !typeName.isBlank()) {
          types.add(new PackType(new ContextName(typeName, "")));
        }
      }
    }
    return new PackField(name(raw), List.copyOf(types));
  }

  private static ContextName name(Map<?, ?> raw) {
    return new ContextName(string(raw, NAME_KEY), string(raw, NAME_EN_KEY));
  }

  private static String string(Map<?, ?> raw, String key) {
    return raw.get(key) instanceof String value ? value : "";
  }

  /**
   * Таблица пака. Описания в паке нет, остальное отдают реализации по умолчанию
   * самого интерфейса.
   */
  private record PackTable(ContextName name, List<ContextQueryTableField> fields,
                           Optional<Boolean> correspondence) implements ContextQueryTable {

    @Override
    public String description() {
      return "";
    }
  }

  /**
   * Поле таблицы пака.
   */
  private record PackField(ContextName name, List<Context> types) implements ContextQueryTableField {

    @Override
    public String description() {
      return "";
    }
  }

  /**
   * Тип значения поля: у пака от типа нужно только имя — вид ему проставит
   * реестр, у которого это имя уже зарегистрировано.
   */
  private record PackType(ContextName name) implements Context {

    @Override
    public ContextKind kind() {
      return ContextKind.TYPE;
    }
  }
}
