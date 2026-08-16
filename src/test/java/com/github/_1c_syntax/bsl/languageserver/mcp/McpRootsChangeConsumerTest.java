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
import io.modelcontextprotocol.spec.McpSchema.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Юнит-тесты разбора корней MCP: что доходит до синхронизации набора рабочих папок,
 * а что отбрасывается. Само владение папками проверяется в {@link McpWorkspaceBootstrapTest}.
 */
class McpRootsChangeConsumerTest {

  private final McpWorkspaceBootstrap bootstrap = mock(McpWorkspaceBootstrap.class);
  private final McpRootsChangeConsumer consumer = new McpRootsChangeConsumer(bootstrap);

  private static Root root(String dir) {
    return new Root(Absolute.path(dir).toUri().toString(), dir);
  }

  @Test
  void declaredRootsAreHandedOverAsWorkspaceFolders() {
    consumer.accept(null, List.of(root("src/test/resources/cli")));

    verify(bootstrap).syncRoots(List.of(Absolute.path("src/test/resources/cli")));
  }

  @Test
  void emptyRootListIsHandedOverAsIs() {
    consumer.accept(null, List.of());

    // Пустой список — это «корней больше нет», а не «нечего делать»: синхронизация обязана состояться.
    verify(bootstrap).syncRoots(List.of());
  }

  @Test
  void unsupportedRootUriIsSkipped() {
    consumer.accept(null, List.of(new Root("https://example.com/not-a-file", "bad")));

    verify(bootstrap).syncRoots(List.of());
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void normalizesWindowsStyleFileUriBeforeRegistration() {
    // Claude Code 2.1.178 на Windows шлёт roots вида `file://D:\path\with\backslashes`
    // (буква диска как «host» + backslash в path). Absolute.uri такое не разбирает —
    // нормализуем до RFC 8089 file:///D:/path до передачи в Absolute. Соответствие пути
    // Windows-стилю выводится из реального filesystem-роута тестового каталога, поэтому
    // end-to-end ассерт ограничен Windows; кросс-платформенно само нормализаторное правило
    // покрыто чистыми string-тестами normalizeWindowsFileUri* ниже.
    var path = Absolute.path("src/test/resources/cli");
    var driveLetter = path.getRoot().toString().substring(0, 2); // "D:"
    var withoutRoot = path.subpath(0, path.getNameCount()).toString(); // backslashes на Windows
    var brokenUri = "file://" + driveLetter + "\\" + withoutRoot;

    consumer.accept(null, List.of(new Root(brokenUri, "broken")));

    verify(bootstrap).syncRoots(List.of(path));
  }

  @Test
  void normalizeWindowsFileUriRewritesDriveLetterAsHost() {
    assertThat(McpRootsChangeConsumer.normalizeWindowsFileUri("file://D:\\git\\1C\\upp\\src\\cf"))
      .isEqualTo("file:///D:/git/1C/upp/src/cf");
  }

  @Test
  void normalizeWindowsFileUriKeepsRfcCompliantValueUntouched() {
    assertThat(McpRootsChangeConsumer.normalizeWindowsFileUri("file:///D:/git/1C/upp/src/cf"))
      .isEqualTo("file:///D:/git/1C/upp/src/cf");
  }

  @Test
  void normalizeWindowsFileUriIgnoresNonFileSchemes() {
    assertThat(McpRootsChangeConsumer.normalizeWindowsFileUri("https://example.com/path"))
      .isEqualTo("https://example.com/path");
  }

}
