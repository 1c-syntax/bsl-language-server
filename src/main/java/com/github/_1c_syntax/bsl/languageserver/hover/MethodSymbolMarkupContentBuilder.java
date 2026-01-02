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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * Построитель контента для всплывающего окна для {@link MethodSymbol}.
 */
@Component
@RequiredArgsConstructor
public class MethodSymbolMarkupContentBuilder implements MarkupContentBuilder<MethodSymbol> {
  private final DescriptionFormatter descriptionFormatter;

  @Override
  public MarkupContent getContent(MethodSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // местоположение метода
    // описание метода
    // параметры
    // возвращаемое значение
    // примеры
    // варианты вызова

    // сигнатура
    var signature = descriptionFormatter.getSignature(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение метода
    var methodLocation = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, methodLocation);

    // описание метода
    var purposeSection = descriptionFormatter.getPurposeSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, purposeSection);

    // параметры
    var parametersSection = descriptionFormatter.getParametersSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, parametersSection);

    // возвращаемое значение
    var returnedValueSection = descriptionFormatter.getReturnedValueSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, returnedValueSection);

    // примеры
    var examplesSection = descriptionFormatter.getExamplesSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, examplesSection);

    // варианты вызова
    var callOptionsSection = descriptionFormatter.getCallOptionsSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, callOptionsSection);

    var content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Method;
  }

}
