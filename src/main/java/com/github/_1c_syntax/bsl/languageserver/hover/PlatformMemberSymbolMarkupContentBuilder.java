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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.springframework.stereotype.Component;

/**
 * Построитель hover-контента для членов платформенных/конфигурационных типов
 * и глобальных функций/свойств. Делегирует рендеринг
 * {@link PlatformMemberHoverBuilder}.
 */
@Component
@RequiredArgsConstructor
public class PlatformMemberSymbolMarkupContentBuilder implements MarkupContentBuilder {

  private final PlatformMemberHoverBuilder platformMemberHoverBuilder;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (PlatformMemberSymbol) reference.symbol();
    return platformMemberHoverBuilder.build(
      symbol.getOwner(), symbol.getDescriptor(), symbol.getCallArgCount(), symbol.getArgTypes());
  }


  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return PlatformMemberSymbol.class;
  }
}
