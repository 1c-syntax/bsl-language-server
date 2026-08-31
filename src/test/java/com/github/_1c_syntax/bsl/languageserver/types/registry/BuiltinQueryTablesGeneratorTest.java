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

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Генератор встроенного пака таблиц языка запросов.
 * <p>
 * Снимает таблицы с синтакс-помощника установленной 1С и переписывает ресурс
 * {@code builtin-query-tables.json}. В обычный прогон не входит: нужна
 * установленная платформа. Запускать при переходе на новую версию платформы;
 * расхождение пака с живой справкой ловит {@code BuiltinQueryTablesHbkTest}.
 */
@Disabled("Разовый генератор: переписывает ресурс по установленной платформе")
class BuiltinQueryTablesGeneratorTest {

  private static final Path RESOURCE = Path.of(
    "src/main/resources/com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-query-tables.json");

  @Test
  void generate() throws Exception {
    var install = PlatformFinder.findLatest().orElseThrow();
    var workDir = Files.createTempDirectory("builtin-query-tables-");
    var grabber = PlatformContextGrabber.fromPlatformBin(install.binDir(), workDir);
    grabber.parse();
    var tables = grabber.getQueryProvider().getTables();
    assertThat(tables).isNotEmpty();

    var lines = new ArrayList<String>();
    lines.add("[");
    for (var i = 0; i < tables.size(); i++) {
      lines.add(table(tables.get(i)) + (i == tables.size() - 1 ? "" : ","));
    }
    lines.add("]");
    Files.writeString(RESOURCE, String.join("\n", lines) + "\n");
    System.out.println("Таблиц: " + tables.size()
      + ", полей: " + tables.stream().mapToInt(table -> table.fields().size()).sum()
      + " → " + RESOURCE);
  }

  private static String table(ContextQueryTable table) {
    var head = new ArrayList<String>();
    head.add("\"name\": " + quote(table.name().getName()));
    if (!table.name().getAlias().isBlank()) {
      head.add("\"nameEn\": " + quote(table.name().getAlias()));
    }
    table.correspondence().ifPresent(value -> head.add("\"correspondence\": " + value));
    var fields = table.fields().stream().map(BuiltinQueryTablesGeneratorTest::field).toList();
    return "  {\n    " + String.join(",\n    ", head)
      + ",\n    \"fields\": [\n" + String.join(",\n", fields) + "\n    ]\n  }";
  }

  private static String field(ContextQueryTableField field) {
    var parts = new ArrayList<String>();
    parts.add("\"name\": " + quote(field.name().getName()));
    if (!field.name().getAlias().isBlank()) {
      parts.add("\"nameEn\": " + quote(field.name().getAlias()));
    }
    var types = field.types().stream().map(type -> quote(type.name().getName())).toList();
    if (!types.isEmpty()) {
      parts.add("\"types\": [" + String.join(", ", types) + "]");
    }
    return "      {" + String.join(", ", parts) + "}";
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
