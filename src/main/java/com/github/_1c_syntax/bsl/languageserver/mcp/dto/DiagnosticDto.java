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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import org.eclipse.lsp4j.Diagnostic;
import org.jspecify.annotations.Nullable;

/**
 * Диагностическое сообщение.
 *
 * @param range Диапазон кода, к которому относится диагностика.
 * @param severity Уровень важности ({@code ERROR}, {@code WARNING}, ...); {@code null}, если не задан.
 * @param code Код диагностики; {@code null}, если не задан.
 * @param source Источник диагностики; {@code null}, если не задан.
 * @param message Текст сообщения.
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
