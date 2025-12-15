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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.hover.DescriptionFormatter;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Поставщик подсказок о параметрах вызываемого метода.
 */
@Component
@RequiredArgsConstructor
public class SourceDefinedMethodCallInlayHintSupplier implements InlayHintSupplier {

  // TODO: высчитать позицию хинта относительно последнего параметра.
  private static final boolean DEFAULT_SHOW_ALL_PARAMETERS = false;
  private static final boolean DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME = false;
  private static final boolean DEFAULT_DEFAULT_VALUES = true;

  private final ReferenceIndex referenceIndex;
  private final LanguageServerConfiguration configuration;
  private final DescriptionFormatter descriptionFormatter;


  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var range = params.getRange();
    return referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(reference -> Ranges.containsPosition(range, reference.getSelectionRange().getStart()))
      .filter(Reference::isSourceDefinedSymbolReference)
      .map(this::toInlayHints)
      .flatMap(Collection::stream)
      .toList();
  }

  private List<InlayHint> toInlayHints(Reference reference) {

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

          if (!showParametersWithTheSameName() && Strings.CI.contains(passedValue, parameter.getName())) {
            continue;
          }

          var inlayHint = new InlayHint();
          inlayHint.setKind(InlayHintKind.Parameter);

          setLabelAndPadding(inlayHint, parameter, passedValue);
          setPosition(inlayHint, callParam);
          setTooltip(inlayHint, parameter);

          hints.add(inlayHint);
        }

        return hints;
      })
      .flatMap(Collection::stream)
      .toList();

  }

  private void setLabelAndPadding(
    InlayHint inlayHint,
    ParameterDefinition parameter,
    String passedValue
  ) {

    var defaultValue = parameter.getDefaultValue();

    var labelBuilder = new StringBuilder();
    labelBuilder.append(parameter.getName());

    if (showDefaultValues()
      && passedValue.isBlank()
      && !defaultValue.equals(ParameterDefinition.DefaultValue.EMPTY)
    ) {
      labelBuilder.append(" (");
      labelBuilder.append(defaultValue.value());
      labelBuilder.append(")");
    } else {
      labelBuilder.append(":");
      inlayHint.setPaddingRight(Boolean.TRUE);
    }

    inlayHint.setLabel(labelBuilder.toString());
  }

  private static void setPosition(InlayHint inlayHint, BSLParser.CallParamContext callParam) {
    var position = new Position(callParam.getStart().getLine() - 1, callParam.getStart().getCharPositionInLine());
    inlayHint.setPosition(position);
  }

  private void setTooltip(InlayHint inlayHint, ParameterDefinition parameter) {
    var markdown = descriptionFormatter.parameterToString(parameter);
    var tooltip = new MarkupContent(MarkupKind.MARKDOWN, markdown);
    inlayHint.setTooltip(tooltip);
  }


  private boolean showParametersWithTheSameName() {
    var parameters = configuration.getInlayHintOptions().getParameters().getOrDefault(getId(), Either.forLeft(true));
    if (parameters.isLeft()) {
      return DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME;
    } else {
      return (boolean) parameters.getRight().getOrDefault(
        "showParametersWithTheSameName",
        DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME
      );
    }
  }

  private boolean showDefaultValues() {
    var parameters = configuration.getInlayHintOptions().getParameters().getOrDefault(getId(), Either.forLeft(true));
    if (parameters.isLeft()) {
      return DEFAULT_DEFAULT_VALUES;
    } else {
      return (boolean) parameters.getRight().getOrDefault("showDefaultValues", DEFAULT_DEFAULT_VALUES);
    }
  }


  private static boolean isRightMethod(ParserRuleContext doCallParent, Reference reference) {
    var selectionRange = reference.getSelectionRange();

    if (doCallParent instanceof BSLParser.MethodCallContext methodCallContext) {
      return selectionRange.equals(Ranges.create(methodCallContext.methodName()));
    } else if (doCallParent instanceof BSLParser.GlobalMethodCallContext globalMethodCallContext) {
      return selectionRange.equals(Ranges.create(globalMethodCallContext.methodName()));
    } else {
      return false;
    }
  }
}
