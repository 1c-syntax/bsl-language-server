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
package com.github._1c_syntax.bsl.languageserver.configuration;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт «значение по умолчанию в JSON-схеме = значение по умолчанию в коде».
 * <p>
 * Дефолты настроек живут в полях классов опций, а схема конфигурации (подсказки и валидация в
 * редакторе) повторяет их в {@code default}. Тест обходит вложенность {@code properties} схемы и
 * сверяет каждый скалярный и списковый {@code default} с сериализованной конфигурацией по
 * умолчанию по тому же пути. Сравнение строк — без учёта регистра: перечисления в схеме записаны
 * так, как их пишут в файле, а читаются без учёта регистра.
 */
class ConfigurationSchemaDefaultsTest {

  private static final String SCHEMA = "/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void schemaDefaultsMatchConfigurationDefaults() throws IOException {
    JsonNode schema;
    try (var stream = getClass().getResourceAsStream(SCHEMA)) {
      schema = MAPPER.readTree(stream);
    }
    JsonNode workspaceDefaults = MAPPER.valueToTree(new LanguageServerConfiguration());
    JsonNode globalDefaults = MAPPER.valueToTree(new GlobalLanguageServerConfiguration());

    var mismatches = new ArrayList<String>();
    collectMismatches(schema, "", workspaceDefaults, globalDefaults, mismatches);

    assertThat(mismatches).isEmpty();
  }

  private static void collectMismatches(
    JsonNode schemaNode,
    String path,
    @Nullable JsonNode workspaceValue,
    @Nullable JsonNode globalValue,
    List<String> mismatches
  ) {
    var properties = schemaNode.get("properties");
    if (properties == null) {
      return;
    }
    for (var property : properties.properties()) {
      var name = property.getKey();
      var propertySchema = property.getValue();
      var propertyPath = path + "/" + name;
      var workspaceChild = workspaceValue == null ? null : workspaceValue.get(name);
      var globalChild = globalValue == null ? null : globalValue.get(name);

      var expected = propertySchema.get("default");
      if (expected != null && !expected.isNull() && !expected.isObject()) {
        var actual = workspaceChild != null ? workspaceChild : globalChild;
        if (actual == null) {
          mismatches.add(propertyPath + ": в конфигурации по умолчанию нет значения, в схеме " + expected);
        } else if (!equivalent(expected, actual)) {
          mismatches.add(propertyPath + ": в схеме " + expected + ", в коде " + actual);
        }
      }

      collectMismatches(propertySchema, propertyPath, workspaceChild, globalChild, mismatches);
    }
  }

  private static boolean equivalent(JsonNode expected, JsonNode actual) {
    if (expected.isString() && actual.isString()) {
      return expected.asString().equalsIgnoreCase(actual.asString());
    }
    if (expected.isArray() && actual.isArray()) {
      // Списки по умолчанию в коде бывают множествами (порядок не задан) — сравниваем состав.
      return expected.size() == actual.size()
        && expected.valueStream().allMatch(item -> actual.valueStream().anyMatch(item::equals));
    }
    return expected.equals(actual);
  }
}
