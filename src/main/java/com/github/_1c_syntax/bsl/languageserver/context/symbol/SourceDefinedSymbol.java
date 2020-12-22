/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Optional;

public interface SourceDefinedSymbol extends Symbol {
  DocumentContext getOwner();

  Range getRange();

  Range getSelectionRange();

  Optional<SourceDefinedSymbol> getParent();

  void setParent(Optional<SourceDefinedSymbol> symbol);

  List<SourceDefinedSymbol> getChildren();

  default Optional<SourceDefinedSymbol> getRootParent() {
    return getParent().flatMap(SourceDefinedSymbol::getRootParent).or(() -> Optional.of(this));
  }
}
