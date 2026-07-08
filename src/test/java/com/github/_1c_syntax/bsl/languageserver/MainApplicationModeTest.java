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
package com.github._1c_syntax.bsl.languageserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет выбор Spring-профилей, типа веб-приложения и эндпоинта MCP по аргументам командной строки.
 */
class MainApplicationModeTest {

  private static final String MCP_ENDPOINT_PROPERTY = "spring.ai.mcp.server.streamable-http.mcp-endpoint";

  @Test
  void activeProfilesPerMode() {
    assertThat(MainApplication.getActiveProfiles(new String[]{})).isEmpty();
    assertThat(MainApplication.getActiveProfiles(new String[]{"lsp"})).isEmpty();
    assertThat(MainApplication.getActiveProfiles(new String[]{"websocket"})).isEmpty();
    assertThat(MainApplication.getActiveProfiles(new String[]{"mcp"}))
      .containsExactly("mcp", "mcp-stdio");
    assertThat(MainApplication.getActiveProfiles(new String[]{"mcp", "--protocol", "sse"}))
      .containsExactly("mcp", "mcp-sse");
    assertThat(MainApplication.getActiveProfiles(new String[]{"mcp", "--protocol", "streamable"}))
      .containsExactly("mcp", "mcp-streamable");
    assertThat(MainApplication.getActiveProfiles(new String[]{"--mcp"}))
      .containsExactly("mcp", "lsp-mcp");
    assertThat(MainApplication.getActiveProfiles(new String[]{"websocket", "--mcp"}))
      .containsExactly("mcp", "websocket-mcp");
  }

  @Test
  void webApplicationTypePerMode() {
    assertThat(MainApplication.getWebApplicationType(new String[]{})).isEqualTo(WebApplicationType.NONE);
    assertThat(MainApplication.getWebApplicationType(new String[]{"mcp"})).isEqualTo(WebApplicationType.NONE);
    assertThat(MainApplication.getWebApplicationType(new String[]{"mcp", "--protocol", "sse"}))
      .isEqualTo(WebApplicationType.SERVLET);
    assertThat(MainApplication.getWebApplicationType(new String[]{"mcp", "--protocol", "streamable"}))
      .isEqualTo(WebApplicationType.SERVLET);
    assertThat(MainApplication.getWebApplicationType(new String[]{"--mcp"}))
      .isEqualTo(WebApplicationType.SERVLET);
    assertThat(MainApplication.getWebApplicationType(new String[]{"websocket"}))
      .isEqualTo(WebApplicationType.SERVLET);
  }

  @Test
  void debugFlagsStrippedWithoutOptIn() {
    System.clearProperty("debug");
    System.clearProperty("trace");
    try {
      var result = MainApplication.guardSpringDebugMode(new String[]{"analyze", "--debug", "--trace"});

      assertThat(result).containsExactly("analyze");
      assertThat(System.getProperty("debug")).isEqualTo("false");
      assertThat(System.getProperty("trace")).isEqualTo("false");
    } finally {
      System.clearProperty("debug");
      System.clearProperty("trace");
    }
  }

  @Test
  void debugFlagsKeptWithOptIn() {
    System.clearProperty("debug");
    System.clearProperty("trace");
    try {
      var result = MainApplication.guardSpringDebugMode(
        new String[]{"analyze", "--debug", MainApplication.ENABLE_DEBUG_OPTION + "=true"});

      // Флаг оставлен, а сама опция-подтверждение убрана из аргументов.
      assertThat(result).containsExactly("analyze", "--debug");
      assertThat(System.getProperty("debug")).isNull();
    } finally {
      System.clearProperty("debug");
      System.clearProperty("trace");
    }
  }

  @Test
  void optInWithFalseValueDoesNotEnableDebug() {
    System.clearProperty("debug");
    System.clearProperty("trace");
    try {
      var result = MainApplication.guardSpringDebugMode(
        new String[]{"--debug", MainApplication.ENABLE_DEBUG_OPTION + "=false"});

      assertThat(result).isEmpty();
      assertThat(System.getProperty("debug")).isEqualTo("false");
    } finally {
      System.clearProperty("debug");
      System.clearProperty("trace");
    }
  }

  @Test
  void guardIsNoOpWithoutDebugRequest() {
    System.clearProperty("debug");
    System.clearProperty("trace");
    try {
      var result = MainApplication.guardSpringDebugMode(new String[]{"analyze", "--srcDir", "."});

      assertThat(result).containsExactly("analyze", "--srcDir", ".");
      // Без запроса на отладку глобальное состояние не трогаем.
      assertThat(System.getProperty("debug")).isNull();
    } finally {
      System.clearProperty("debug");
      System.clearProperty("trace");
    }
  }

  @Test
  void debugFromSystemPropertyForcedOff() {
    // Запрос на отладку без флага командной строки — через свойство debug (как relaxed-binding DEBUG).
    System.clearProperty("debug");
    System.clearProperty("trace");
    try {
      System.setProperty("debug", "true");
      var result = MainApplication.guardSpringDebugMode(new String[]{"analyze"});

      assertThat(result).containsExactly("analyze");
      assertThat(System.getProperty("debug")).isEqualTo("false");
    } finally {
      System.clearProperty("debug");
      System.clearProperty("trace");
    }
  }

  @Test
  void mcpEndpointPathAppliedOnlyForMcpHttp() {
    System.clearProperty(MCP_ENDPOINT_PROPERTY);
    try {
      // Без флага --mcp путь не применяется.
      MainApplication.applyMcpEndpointPath(new String[]{"mcp"});
      assertThat(System.getProperty(MCP_ENDPOINT_PROPERTY)).isNull();

      MainApplication.applyMcpEndpointPath(new String[]{"--mcp", "--mcp-path=/custom"});
      assertThat(System.getProperty(MCP_ENDPOINT_PROPERTY)).isEqualTo("/custom");
    } finally {
      System.clearProperty(MCP_ENDPOINT_PROPERTY);
    }
  }
}
