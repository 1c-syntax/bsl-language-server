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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;

/**
 * Адаптер описания, полученного {@code bsl-parser}'ом из doc-комментариев
 * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol},
 * к нашему интерфейсу {@link SymbolDescription}.
 */
public final class ParserSymbolDescriptionAdapter implements SymbolDescription {

  private final SourceDefinedSymbolDescription delegate;

  private ParserSymbolDescriptionAdapter(SourceDefinedSymbolDescription delegate) {
    this.delegate = delegate;
  }

  public static SymbolDescription of(SourceDefinedSymbolDescription description) {
    if (description == null) {
      return SymbolDescription.EMPTY;
    }
    return new ParserSymbolDescriptionAdapter(description);
  }

  @Override
  public String getPurposeDescription() {
    var purpose = delegate.getPurposeDescription();
    return purpose == null ? "" : purpose;
  }

  @Override
  public boolean isDeprecated() {
    return delegate.isDeprecated();
  }

  @Override
  public String getDeprecationInfo() {
    var info = delegate.getDeprecationInfo();
    return info == null ? "" : info;
  }
}
