/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CodeLensProvider {
  public static List<CodeLens> getCodeLens(CodeLensParams params, DocumentContext documentContext) {

    List<CodeLens> codeLenses = new ArrayList<>();

    Map<MethodSymbol, Integer> methodsComplexity = documentContext.getCognitiveComplexityData().getMethodsComplexity();
    methodsComplexity.forEach((MethodSymbol methodSymbol, Integer complexity) -> {
      BSLParserRuleContext node = methodSymbol.getNode();

      Token symbol;
      if (methodSymbol.isFunction()) {
        symbol = ((BSLParser.FunctionContext) node).funcDeclaration().FUNCTION_KEYWORD().getSymbol();
      } else {
        symbol = ((BSLParser.ProcedureContext) node).procDeclaration().PROCEDURE_KEYWORD().getSymbol();
      }
      Range range = RangeHelper.newRange(symbol);
      String title = String.format("Cognitive complexity is %d", complexity);
      Command command = new Command(title, "cognitiveComplexity");
      CodeLens codeLens = new CodeLens(
        range,
        command,
        null // methodSymbol?
      );

      codeLenses.add(codeLens);
    });

    return codeLenses;
  }
}
