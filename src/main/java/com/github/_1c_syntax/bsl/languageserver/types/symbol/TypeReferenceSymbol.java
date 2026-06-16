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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Symbol, представляющий ссылку на тип по его имени — например, имя типа в
 * описании метода (BSLDoc, секции {@code // Параметры:} и
 * {@code // Возвращаемое значение:}).
 *
 * <p>Несёт имя типа и разрешённую ссылку на тип; этого достаточно для рендеринга
 * hover'а с документацией типа (как инстанцируемого, так и неинстанцируемого) без
 * обращения consumer'ов в другие сервисы за резолвом.
 */
@Getter
@EqualsAndHashCode(of = "typeName")
@RequiredArgsConstructor
public final class TypeReferenceSymbol implements Symbol {

  private final String typeName;
  private final TypeRef typeRef;

  @Override
  public String getName() {
    return typeName;
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Class;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    // synthetic — не участвует в обходе symbol-tree.
  }
}
