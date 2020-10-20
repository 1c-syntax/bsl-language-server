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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CyclomaticComplexityCodeLensSupplier implements CodeLensSupplier {

  private final LanguageServerConfiguration configuration;

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {

    if (!configuration.getCodeLensOptions().isShowCyclomaticComplexity()) {
      return Collections.emptyList();
    }

    Map<MethodSymbol, Integer> methodsComplexity = documentContext.getCyclomaticComplexityData()
      .getMethodsComplexity();

    List<CodeLens> codeLenses = new ArrayList<>(methodsComplexity.size());

    methodsComplexity.forEach((MethodSymbol methodSymbol, Integer complexity) -> {
      String title = String.format("Cyclomatic complexity is %d", complexity);
      Command command = new Command(title, "");
      CodeLens codeLens = new CodeLens(
        methodSymbol.getSubNameRange(),
        command,
        null
      );

      codeLenses.add(codeLens);
    });

    return codeLenses;
  }
}
