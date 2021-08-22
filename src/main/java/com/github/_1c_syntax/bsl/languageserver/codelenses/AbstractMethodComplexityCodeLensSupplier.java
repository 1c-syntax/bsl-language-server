/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Базовый класс для реализации линз, показывающих сложность методов в документе.
 * <p>
 * Конкретный сапплаер должен иметь ресурс-бандл со свойством {@code title}, имеющим один числовой параметр {@code %d}.
 */
@RequiredArgsConstructor
public abstract class AbstractMethodComplexityCodeLensSupplier implements CodeLensSupplier {

  private static final String TITLE_KEY = "title";

  protected final LanguageServerConfiguration configuration;

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
    if (!supplierIsEnabled()) {
      return Collections.emptyList();
    }

    Map<MethodSymbol, Integer> methodsComplexity = getMethodsComplexity(documentContext);

    List<CodeLens> codeLenses = new ArrayList<>(methodsComplexity.size());

    methodsComplexity.forEach((MethodSymbol methodSymbol, Integer complexity) -> {
      var title = Resources.getResourceString(configuration.getLanguage(), getClass(), TITLE_KEY, complexity);
      var command = new Command(title, "");
      var codeLens = new CodeLens(
        methodSymbol.getSubNameRange(),
        command,
        null
      );

      codeLenses.add(codeLens);
    });

    return codeLenses;
  }

  /**
   * @return Нужно ли применять конкретный сапплаер.
   */
  protected abstract boolean supplierIsEnabled();

  /**
   * @param documentContext Документ, для которого нужно рассчитать информацию о сложностях методов.
   * @return Данные о сложности методов.
   */
  protected abstract Map<MethodSymbol, Integer> getMethodsComplexity(DocumentContext documentContext);
}
