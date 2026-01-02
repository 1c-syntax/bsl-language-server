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
package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.Getter;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Класс хранит информацию о директиве компиляции.
 * См. {@link com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol}
 */
public enum CompilerDirectiveKind {
  AT_SERVER_NO_CONTEXT(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL),
  AT_CLIENT_AT_SERVER_NO_CONTEXT(BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL),
  AT_CLIENT_AT_SERVER(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL),
  AT_CLIENT(BSLParser.ANNOTATION_ATCLIENT_SYMBOL),
  AT_SERVER(BSLParser.ANNOTATION_ATSERVER_SYMBOL);

  @Getter
  private final int tokenType;

  CompilerDirectiveKind(int tokenType) {
    this.tokenType = tokenType;
  }

  public static Optional<CompilerDirectiveKind> of(int tokenType) {
    return Stream.of(values())
      .filter(compilerDirective -> compilerDirective.getTokenType() == tokenType)
      .findAny();
  }
}
