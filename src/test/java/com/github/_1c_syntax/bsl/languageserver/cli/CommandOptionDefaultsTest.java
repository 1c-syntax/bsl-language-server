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
package com.github._1c_syntax.bsl.languageserver.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Опция команды, названная ключом свойства Spring ({@code --server.port}), Spring читает прямо
 * из аргументов, а значение по умолчанию берёт из {@code application.properties}. Значение
 * по умолчанию у опции picocli нужно только справке — и обязано совпадать с файлом свойств,
 * иначе справка обещает не то, что запускается.
 */
class CommandOptionDefaultsTest {

  private static final List<Class<?>> COMMANDS = List.of(
    AnalyzeCommand.class, FormatCommand.class, LanguageServerStartCommand.class, McpCommand.class,
    VersionCommand.class, WebsocketCommand.class);

  @Test
  void optionsNamedAfterSpringPropertiesShowTheirDefaultFromApplicationProperties() throws IOException {
    // given
    var properties = new Properties();
    try (var stream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
      properties.load(stream);
    }

    // when
    var checked = new ArrayList<String>();
    var mismatched = new ArrayList<String>();
    for (var command : COMMANDS) {
      for (var field : command.getDeclaredFields()) {
        var option = field.getAnnotation(Option.class);
        if (option == null || option.defaultValue().equals(Option.NULL_VALUE)) {
          continue;
        }
        for (var name : option.names()) {
          var key = name.startsWith("--") ? name.substring(2) : name;
          var expected = properties.getProperty(key);
          if (expected == null) {
            continue;
          }
          checked.add(name);
          if (!expected.equals(option.defaultValue())) {
            mismatched.add("%s.%s: %s ≠ %s".formatted(command.getSimpleName(), name, option.defaultValue(), expected));
          }
        }
      }
    }

    // then — проверка не пустая: такие опции есть
    assertThat(checked).contains("--server.port", "--app.websocket.lsp-path");
    assertThat(mismatched).isEmpty();
  }
}
