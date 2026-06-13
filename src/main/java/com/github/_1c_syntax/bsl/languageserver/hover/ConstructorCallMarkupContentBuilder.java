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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.symbol.ConstructorCallSymbol;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.springframework.stereotype.Component;

/**
 * Построитель hover-контента для имени класса в выражении {@code Новый ИмяКласса(...)}.
 * Делегирует {@link ConstructorHoverBuilder}, выбирая подходящую сигнатуру
 * по фактической арности из {@link ConstructorCallSymbol#getArgCount()}.
 */
@Component
@RequiredArgsConstructor
public class ConstructorCallMarkupContentBuilder implements MarkupContentBuilder {

  private final ConstructorHoverBuilder constructorHoverBuilder;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (ConstructorCallSymbol) reference.symbol();
    var fileType = reference.from().getOwner().getFileType();
    var ctors = symbol.getConstructors();
    if (ctors.isEmpty()) {
      return constructorHoverBuilder.build(
        symbol.getTypeName(), symbol.getTypeRef(), null, ctors, false, symbol.getClassDescription(), fileType);
    }
    int chosenIndex = SignatureSelection.pickIndexByArity(ctors, symbol.getArgCount());
    boolean disclaim = false;
    SignatureDescriptor chosen;
    if (chosenIndex < 0) {
      chosen = ctors.get(0);
      disclaim = true;
    } else {
      chosen = ctors.get(chosenIndex);
    }
    return constructorHoverBuilder.build(
      symbol.getTypeName(), symbol.getTypeRef(), chosen, ctors, disclaim, symbol.getClassDescription(), fileType);
  }


  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return ConstructorCallSymbol.class;
  }
}
