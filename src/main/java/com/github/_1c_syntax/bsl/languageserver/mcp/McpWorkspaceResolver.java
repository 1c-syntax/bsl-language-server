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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Выбор workspace для MCP-инструментов, у которых нет явной привязки к конкретному
 * файлу (например, {@code type_info}, {@code global_member_info}).
 * <p>
 * Если клиент явно указал {@code root} (одно из значений объявленных им MCP-roots),
 * резолвится конкретно этот workspace; в противном случае берётся любой
 * зарегистрированный. Если зарегистрированных нет — бросается осмысленное исключение.
 * <p>
 * Сравнение URI ведётся через {@link Absolute#uri(String)} — чтобы клиентское
 * представление ({@code file://D:/repo} / {@code file:///D:/repo/} / разный
 * regex-эскейпинг) сходилось с тем URI, под которым workspace зарегистрирован.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class McpWorkspaceResolver {

  private final ServerContextProvider serverContextProvider;

  /**
   * Выбрать workspace для tool-запроса.
   *
   * @param requestedRoot URI workspace-root'а, на который ссылается запрос, либо {@code null}.
   * @return URI зарегистрированного workspace.
   * @throws IllegalArgumentException если {@code requestedRoot} задан, но не совпадает ни с одним
   *   зарегистрированным workspace; либо если {@code requestedRoot} не задан и нет
   *   ни одного зарегистрированного workspace.
   */
  public URI resolveWorkspaceUri(@Nullable String requestedRoot) {
    var contexts = serverContextProvider.getAllContexts();
    if (requestedRoot != null && !requestedRoot.isBlank()) {
      var normalized = Absolute.uri(requestedRoot);
      return contexts.keySet().stream()
        .filter(uri -> uri.equals(normalized))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
          "No registered workspace matches root: " + requestedRoot));
    }
    return contexts.values().stream()
      .findFirst()
      .map(ServerContext::getWorkspaceUri)
      .orElseThrow(() -> new IllegalArgumentException(
        "No registered workspace. Open a workspace via MCP roots first."));
  }
}
