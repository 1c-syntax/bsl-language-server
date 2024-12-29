/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * Построитель контента для всплывающего окна для {@link AnnotationSymbol}.
 */
@Component
@RequiredArgsConstructor
public class AnnotationSymbolMarkupContentBuilder implements MarkupContentBuilder<AnnotationSymbol> {

  private final DescriptionFormatter descriptionFormatter;

  @Override
  public MarkupContent getContent(AnnotationSymbol symbol) {
    var maybeMethodSymbol = symbol.getParent();
    if (maybeMethodSymbol.filter(MethodSymbol.class::isInstance).isEmpty()) {
      return new MarkupContent(MarkupKind.MARKDOWN, "");
    }

    var markupBuilder = new StringJoiner("\n");
    var methodSymbol = (MethodSymbol) maybeMethodSymbol.get();

    // сигнатура
    // местоположение метода
    // описание метода
    // параметры
    // примеры
    // варианты вызова

    // сигнатура
    String signature = descriptionFormatter.getSignature(symbol, methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение метода
    String methodLocation = descriptionFormatter.getLocation(methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, methodLocation);

    // описание метода
    String purposeSection = descriptionFormatter.getPurposeSection(methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, purposeSection);

    // параметры
    String parametersSection = descriptionFormatter.getParametersSection(methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, parametersSection);

    // примеры
    String examplesSection = descriptionFormatter.getExamplesSection(methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, examplesSection);

    // варианты вызова
    String callOptionsSection = descriptionFormatter.getCallOptionsSection(methodSymbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, callOptionsSection);

    String content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.TypeParameter;
  }

}
