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

import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Компактные DTO для ответов MCP-инструментов и преобразования из типов lsp4j.
 * <p>
 * Цель — отдавать модели небольшой стабильный JSON вместо «толстых» объектов
 * lsp4j (с {@code Either}-полями и служебными данными). Все координаты
 * нумеруются с нуля, как в LSP.
 */
@UtilityClass
public class McpDtos {

  /**
   * Диапазон (нумерация строк и символов с нуля).
   */
  public record RangeDto(int startLine, int startCharacter, int endLine, int endCharacter) {
    public static RangeDto from(Range range) {
      return new RangeDto(
        range.getStart().getLine(),
        range.getStart().getCharacter(),
        range.getEnd().getLine(),
        range.getEnd().getCharacter()
      );
    }
  }

  /**
   * Диагностическое сообщение.
   */
  public record DiagnosticDto(
    RangeDto range,
    @Nullable String severity,
    @Nullable String code,
    @Nullable String source,
    String message
  ) {
    public static DiagnosticDto from(Diagnostic diagnostic) {
      var severity = diagnostic.getSeverity() == null ? null : diagnostic.getSeverity().name();

      String code = null;
      var rawCode = diagnostic.getCode();
      if (rawCode != null) {
        code = rawCode.isLeft() ? rawCode.getLeft() : String.valueOf(rawCode.getRight());
      }

      var rawMessage = diagnostic.getMessage();
      var message = rawMessage.isLeft() ? rawMessage.getLeft() : rawMessage.getRight().getValue();

      return new DiagnosticDto(
        RangeDto.from(diagnostic.getRange()),
        severity,
        code,
        diagnostic.getSource(),
        message
      );
    }
  }

  /**
   * Символ документа (метод, процедура, переменная и т.п.) с вложенными дочерними символами.
   */
  public record SymbolDto(
    String name,
    String kind,
    @Nullable String detail,
    RangeDto range,
    List<SymbolDto> children
  ) {
    public static SymbolDto from(DocumentSymbol symbol) {
      var children = symbol.getChildren() == null
        ? List.<SymbolDto>of()
        : symbol.getChildren().stream().map(SymbolDto::from).toList();

      return new SymbolDto(
        symbol.getName(),
        symbol.getKind().name(),
        symbol.getDetail(),
        RangeDto.from(symbol.getRange()),
        children
      );
    }
  }

  /**
   * Местоположение ссылки на символ.
   */
  public record LocationDto(String uri, RangeDto range) {
    public static LocationDto from(Location location) {
      return new LocationDto(location.getUri(), RangeDto.from(location.getRange()));
    }
  }

  /**
   * Элемент иерархии вызовов (метод/процедура).
   */
  public record CallHierarchyItemDto(
    String name,
    String kind,
    @Nullable String detail,
    String uri,
    RangeDto range
  ) {
    public static CallHierarchyItemDto from(CallHierarchyItem item) {
      return new CallHierarchyItemDto(
        item.getName(),
        item.getKind().name(),
        item.getDetail(),
        item.getUri(),
        RangeDto.from(item.getSelectionRange())
      );
    }
  }

  /**
   * Вызов в иерархии: связанный метод и диапазоны мест вызова.
   */
  public record CallDto(CallHierarchyItemDto item, List<RangeDto> ranges) {
    public static CallDto incoming(CallHierarchyIncomingCall call) {
      return new CallDto(
        CallHierarchyItemDto.from(call.getFrom()),
        call.getFromRanges().stream().map(RangeDto::from).toList()
      );
    }

    public static CallDto outgoing(CallHierarchyOutgoingCall call) {
      return new CallDto(
        CallHierarchyItemDto.from(call.getTo()),
        call.getFromRanges().stream().map(RangeDto::from).toList()
      );
    }
  }
}
