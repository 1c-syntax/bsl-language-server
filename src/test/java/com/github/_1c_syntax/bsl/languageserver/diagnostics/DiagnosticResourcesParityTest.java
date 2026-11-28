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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт «сообщения диагностик есть в обеих локалях с одинаковым набором ключей».
 * <p>
 * Ключ, заведённый только в одной локали, в другой показывается пользователю как есть.
 */
class DiagnosticResourcesParityTest {

  private static final Path RESOURCES =
    Path.of("src/main/resources/com/github/_1c_syntax/bsl/languageserver/diagnostics");

  @Test
  void ruAndEnBundlesHaveTheSameKeys() throws IOException {
    var mismatches = new ArrayList<String>();

    try (Stream<Path> files = Files.list(RESOURCES)) {
      for (var ruBundle : files.filter(file -> file.toString().endsWith("_ru.properties")).toList()) {
        var name = ruBundle.getFileName().toString();
        var enBundle = ruBundle.resolveSibling(name.replace("_ru.properties", "_en.properties"));
        if (!Files.exists(enBundle)) {
          mismatches.add(name + ": нет английской локали");
          continue;
        }
        var ruKeys = keys(ruBundle);
        var enKeys = keys(enBundle);
        if (!ruKeys.equals(enKeys)) {
          mismatches.add(name + ": ключи ru " + ruKeys + ", en " + enKeys);
        }
      }
    }

    assertThat(mismatches).isEmpty();
  }

  private static Set<String> keys(Path bundle) throws IOException {
    var properties = new Properties();
    try (Reader reader = Files.newBufferedReader(bundle, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    return new TreeSet<>(properties.stringPropertyNames());
  }
}
