/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

  private static final String PROCEDURE_KEY = "procedure";
  private static final String FUNCTION_KEY = "function";
  private static final String EXPORT_KEY = "export";
  private static final String VAL_KEY = "val";
  private static final String PARAMETERS_KEY = "parameters";
  private static final String RETURNED_VALUE_KEY = "returnedValue";
  private static final String EXAMPLES_KEY = "examples";
  private static final String CALL_OPTIONS_KEY = "callOptions";
  private static final String PARAMETER_TEMPLATE = "* **%s**: %s";

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
    String signature = descriptionFormatter.getSignature(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение метода
    String methodLocation = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, methodLocation);

    // описание метода
    String purposeSection = descriptionFormatter.getPurposeSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, purposeSection);

    // параметры
    String parametersSection = descriptionFormatter.getParametersSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, parametersSection);

    // возвращаемое значение
    String returnedValueSection = descriptionFormatter.getReturnedValueSection(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, returnedValueSection);

    // примеры
    String examplesSection = descriptionFormatter.getExamplesSection(symbol);
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
