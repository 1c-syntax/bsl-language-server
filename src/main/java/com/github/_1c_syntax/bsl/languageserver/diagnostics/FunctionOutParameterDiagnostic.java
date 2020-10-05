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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.DESIGN
  }

)
public class FunctionOutParameterDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {

    List<ParameterDefinition> parameters = documentContext
      .getSymbolTree()
      .getMethodSymbol((BSLParserRuleContext) ctx.getParent())
      .stream()
      .map(MethodSymbol::getParameters)
      .flatMap(Collection::stream)
      .filter(param -> !param.isByValue())
      .collect(Collectors.toList());

    if (parameters.isEmpty()) {
      return ctx;
    }

    Map<String, BSLParserRuleContext> lvalues = Trees
      .findAllRuleNodes(ctx.subCodeBlock(), BSLParser.RULE_lValue)
      .stream()
      .collect(
        Collectors.toMap(
          ParseTree::getText,
          node -> (BSLParserRuleContext) node,
          (existing, replacement) -> existing,
          CaseInsensitiveMap::new)
      );

    parameters.
      stream()
      .filter(param -> lvalues.containsKey(param.getName()))
      .map(param -> lvalues.get(param.getName()))
      .filter(Objects::nonNull)
      .forEach(diagnosticStorage::addDiagnostic);

    return ctx;
  }
}
