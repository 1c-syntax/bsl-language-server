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
package com.github._1c_syntax.bsl.languageserver.mcp.tools;

import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.CallDto;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.CallHierarchyItemDto;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент: иерархия вызовов метода/процедуры под курсором.
 * <p>
 * За один вызов возвращает один уровень иерархии — прямые входящие и исходящие вызовы,
 * как один раунд LSP-протокола ({@code prepareCallHierarchy} + {@code incomingCalls} +
 * {@code outgoingCalls}). Дерево рекурсивно не разворачивается, поэтому саморекурсивные
 * методы безопасны. Вся работа делегируется {@link CallHierarchyProvider}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class CallHierarchyTool {

  private final McpDocumentReader documentReader;
  private final CallHierarchyProvider callHierarchyProvider;

  /**
   * Результат построения иерархии вызовов.
   *
   * @param target Метод/процедура под курсором ({@code null}, если позиция не на вызываемом символе).
   * @param incoming Входящие вызовы (кто вызывает target).
   * @param outgoing Исходящие вызовы (кого вызывает target).
   */
  public record Result(
    @Nullable CallHierarchyItemDto target,
    List<CallDto> incoming,
    List<CallDto> outgoing
  ) {
  }

  @McpTool(
    name = "call_hierarchy",
    description = "Resolve the method/procedure at a zero-based position and return its direct "
      + "incoming and outgoing calls (one level).",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — null target / nullable detail). Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false)
  public Result callHierarchy(
    @McpToolParam(required = true,
      description = "Path to the .bsl/.os file (absolute or relative to the working directory).")
    String file,
    @McpToolParam(required = true, description = "Zero-based line number of the symbol.")
    int line,
    @McpToolParam(required = true, description = "Zero-based character offset within the line.")
    int character
  ) {
    return documentReader.read(file, documentContext -> {
      var prepareParams = new CallHierarchyPrepareParams();
      prepareParams.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
      prepareParams.setPosition(new Position(line, character));

      var items = callHierarchyProvider.prepareCallHierarchy(documentContext, prepareParams);
      if (items.isEmpty()) {
        return new Result(null, List.of(), List.of());
      }

      var item = items.get(0);

      var incoming = callHierarchyProvider
        .incomingCalls(documentContext, new CallHierarchyIncomingCallsParams(item))
        .stream().map(CallDto::incoming).toList();

      var outgoing = callHierarchyProvider
        .outgoingCalls(documentContext, new CallHierarchyOutgoingCallsParams(item))
        .stream().map(CallDto::outgoing).toList();

      return new Result(CallHierarchyItemDto.from(item), incoming, outgoing);
    });
  }
}
