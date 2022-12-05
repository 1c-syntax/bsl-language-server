/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.hover.MethodSymbolMarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SourceDefinedMethodCallInlayHintSupplier implements InlayHintSupplier {

  private final ReferenceIndex referenceIndex;

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var range = params.getRange();

//    var ast = documentContext.getAst();
//    Trees.findAllRuleNodes(
//      ast,
//      BSLParser.RULE_methodCall,
//      BSLParser.RULE_globalMethodCall
//    );

    return referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(reference -> Ranges.containsPosition(range, reference.getSelectionRange().getStart()))
      .filter(Reference::isSourceDefinedSymbolReference)
      .map(SourceDefinedMethodCallInlayHintSupplier::toInlayHints)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }


  private static List<InlayHint> toInlayHints(Reference reference) {

    var methodSymbol = (MethodSymbol) reference.getSymbol();
    var parameters = methodSymbol.getParameters();

    var ast = reference.getFrom().getOwner().getAst();
    var doCalls = Trees.findAllRuleNodes(ast, BSLParser.RULE_doCall);

    return doCalls.stream()
      .map(BSLParser.DoCallContext.class::cast)
      .filter(doCall -> isRightMethod(doCall.getParent(), reference))
      .map(BSLParser.DoCallContext::callParamList)
      .map(BSLParser.CallParamListContext::callParam)
      .map((List<? extends BSLParser.CallParamContext> callParams) -> {
        var hints = new ArrayList<InlayHint>();
        for (var i = 0; i < parameters.size(); i++) {

          // todo: show all parameters (in config)?
          if (callParams.size() < i + 1) {
            break;
          }

          var parameter = parameters.get(i);
          var callParam = callParams.get(i);

          var passedValue = callParam.getText();
          var defaultValue = parameter.getDefaultValue();

          if (StringUtils.containsIgnoreCase(passedValue, parameter.getName())) {
            continue;
          }

          var inlayHint = new InlayHint();
          inlayHint.setKind(InlayHintKind.Parameter);

          var labelBuilder = new StringBuilder();
          labelBuilder.append(parameter.getName());
          if (passedValue.isBlank() && !defaultValue.equals(ParameterDefinition.DefaultValue.EMPTY)) {
            labelBuilder.append(" (");
            labelBuilder.append(defaultValue.getValue());
            labelBuilder.append(")");
          } else {
            labelBuilder.append(":");
            inlayHint.setPaddingRight(Boolean.TRUE);
          }

          inlayHint.setLabel(labelBuilder.toString());

          var position = new Position(callParam.getStart().getLine() - 1, callParam.getStart().getCharPositionInLine());
          inlayHint.setPosition(position);

          // todo: refactor
          var markdown = MethodSymbolMarkupContentBuilder.parameterToString(parameter);
          var tooltip = new MarkupContent(MarkupKind.MARKDOWN, markdown);
          inlayHint.setTooltip(tooltip);

          hints.add(inlayHint);
        }

        return hints;
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

  }

  private static boolean isRightMethod(BSLParserRuleContext doCallParent, Reference reference) {
    var selectionRange = reference.getSelectionRange();

    if (doCallParent instanceof BSLParser.MethodCallContext) {
      var methodCallContext = (BSLParser.MethodCallContext) doCallParent;
      return selectionRange.equals(Ranges.create(methodCallContext.methodName()));
    } else if (doCallParent instanceof BSLParser.GlobalMethodCallContext) {
      var globalMethodCallContext = (BSLParser.GlobalMethodCallContext) doCallParent;
      return selectionRange.equals(Ranges.create(globalMethodCallContext.methodName()));
    }
    return false;
  }
}
