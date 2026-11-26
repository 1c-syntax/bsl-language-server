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
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpWorkspaceFoldersTest {

  @Test
  void normalizesFileUri() {
    var expected = Absolute.path("src/test/resources/cli").toUri();

    assertThat(McpWorkspaceFolders.toWorkspaceFolderUri(expected.toString())).isEqualTo(expected);
  }

  @Test
  void normalizesPlainPath() {
    var expected = Absolute.path("src/test/resources/cli").toUri();

    assertThat(McpWorkspaceFolders.toWorkspaceFolderUri("src/test/resources/cli")).isEqualTo(expected);
    assertThat(McpWorkspaceFolders.toWorkspaceFolderUri("  src/test/resources/cli  ")).isEqualTo(expected);
    assertThat(McpWorkspaceFolders.toWorkspaceFolderUri(Absolute.path("src/test/resources/cli").toString()))
      .isEqualTo(expected);
  }

  @Test
  void normalizeWindowsFileUriRewritesDriveLetterAsHost() {
    assertThat(McpWorkspaceFolders.normalizeWindowsFileUri("file://D:\\git\\1C\\upp\\src\\cf"))
      .isEqualTo("file:///D:/git/1C/upp/src/cf");
  }

  @Test
  void normalizeWindowsFileUriRewritesDriveLetterAsHostWithForwardSlashes() {
    // Без этой нормализации Absolute.uri съедает двоеточие и получает хост `D`: путь теряется.
    assertThat(McpWorkspaceFolders.normalizeWindowsFileUri("file://D:/git/1C/upp/src/cf"))
      .isEqualTo("file:///D:/git/1C/upp/src/cf");
  }

  @Test
  void normalizeWindowsFileUriKeepsRfcCompliantValueUntouched() {
    assertThat(McpWorkspaceFolders.normalizeWindowsFileUri("file:///D:/git/1C/upp/src/cf"))
      .isEqualTo("file:///D:/git/1C/upp/src/cf");
  }

  @Test
  void normalizeWindowsFileUriIgnoresNonFileSchemes() {
    assertThat(McpWorkspaceFolders.normalizeWindowsFileUri("https://example.com/path"))
      .isEqualTo("https://example.com/path");
  }

  @Test
  void normalizeWindowsFileUriKeepsPlainPathUntouched() {
    // Обычный windows-путь без схемы разбирает файловая система — нормализатор его не трогает.
    assertThat(McpWorkspaceFolders.normalizeWindowsFileUri("D:\\git\\upp")).isEqualTo("D:\\git\\upp");
  }

  @Test
  void windowsDriveAsAuthorityKeepsDriveLetterInWorkspaceFolderUri() {
    // Клиент, чей корень приняли через MCP roots, обязан получить тот же URI и через параметр
    // workspaceFolder: иначе ни один инструмент не найдёт папку по присланному значению.
    assertThat(McpWorkspaceFolders.toWorkspaceFolderUri("file://D:/git/upp").toString())
      .startsWith("file:///D:/");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void explainsPathThatCannotBeCanonicalized() throws IOException {
    // Absolute.path приводит путь к каноническому виду через getCanonicalFile(), а тот бросает
    // IOException, не объявляя его (здесь — «File name too long»). Сообщение всё равно должно
    // объяснять, что передать, иначе агент получит сырую ошибку ввода-вывода.
    var tooLong = Files.createTempDirectory("mcp-too-long").resolve("x".repeat(5000)).toString();

    assertThatThrownBy(() -> McpWorkspaceFolders.toWorkspaceFolderUri(tooLong))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported workspace folder");
  }

  @Test
  void hintAsksToRegisterWhenNothingIsRegistered() {
    var hint = McpWorkspaceFolders.registrationHint(List.of());

    assertThat(hint)
      .contains("No workspace folder is registered")
      .contains("register_workspace_folder")
      .doesNotContain("Registered workspace folders");
  }

  @Test
  void hintListsRegisteredRootsSorted() {
    var first = URI.create("file:///a/first");
    var second = URI.create("file:///b/second");

    var hint = McpWorkspaceFolders.registrationHint(Set.of(second, first));

    assertThat(hint)
      .contains("Registered workspace folders: " + first + ", " + second)
      .contains("list_workspace_folders", "register_workspace_folder");
  }
}
