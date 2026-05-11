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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;

public class DiagnosticMessage extends Either<String, MarkupContent> {

  public DiagnosticMessage(String message) {
    super(message, null);
  }

  public DiagnosticMessage(String left, MarkupContent right) {
    super(left, right);
  }

  public String getStringValue() {
    return getStringValue(this);
  }

  public static String getStringValue(Either<String, MarkupContent> message) {
    return message.isLeft() ? message.getLeft() : message.getRight().getValue();
  }

  @Nullable
  public static String getStringValue(Diagnostic diagnostic) {
    var message = diagnostic.getMessage();
    if (message == null) {
      return null;
    }
    return getStringValue(message);
  }

}
