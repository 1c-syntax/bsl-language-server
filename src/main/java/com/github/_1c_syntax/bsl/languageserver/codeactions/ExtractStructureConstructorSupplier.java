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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.languageserver.utils.Strings;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Поставщик code action для извлечения конструктора структуры.
 * <p>
 * Преобразует явную инициализацию полей структуры
 * в вызов конструктора с параметрами.
 */
@Component
@RequiredArgsConstructor
public class ExtractStructureConstructorSupplier implements CodeActionSupplier {

  private final LanguageServerConfiguration configuration;

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    var start = params.getRange().getStart();
    if (start == null) {
      return Collections.emptyList();
    }

    var parseTree = documentContext.getAst();

    var maybeDoCall = Trees.findTerminalNodeContainsPosition(parseTree, start)
      .map(TerminalNode::getParent)
      .filter(BSLParser.TypeNameContext.class::isInstance)
      .map(BSLParser.TypeNameContext.class::cast)
      .filter(DiagnosticHelper::isStructureType)
      .map(ParserRuleContext::getParent)
      .map(BSLParser.NewExpressionContext.class::cast)
      .map(BSLParser.NewExpressionContext::doCall);

    if (maybeDoCall.isEmpty()) {
      return Collections.emptyList();
    }

    var parameters = maybeDoCall
      .map(BSLParser.DoCallContext::callParamList)
      .map(callParamListContext -> callParamListContext.children)
      .orElse(Collections.emptyList())
      .stream()
      .filter(Predicate.not(TerminalNode.class::isInstance))
      .map(BSLParser.CallParamContext.class::cast)
      .toList();

    if (parameters.isEmpty()) {
      return Collections.emptyList();
    }

    var firstParam = parameters.get(0);
    if (firstParam.getTokens().isEmpty()) {
      return Collections.emptyList();
    }

    var firstToken = firstParam.getTokens().get(0);
    if (firstToken.getType() != BSLLexer.STRING) {
      return Collections.emptyList();
    }

    var doCall = maybeDoCall.get();
    var assignment = (BSLParser.AssignmentContext) Trees.getAncestorByRuleIndex(doCall, BSLParser.RULE_assignment);
    if (assignment == null || isParentAssignment(doCall, assignment)) {
      return Collections.emptyList();
    }

    var lValue = assignment.lValue();
    if (lValue == null) {
      return Collections.emptyList();
    }

    var lValueName = lValue.getText();
    var insert = Resources.getResourceString(configuration.getLanguage(), getClass(), "insert");

    var keys = Strings.trimQuotes(firstToken.getText()).split(",");
    var workspaceEdit = new WorkspaceEdit();
    var changes = new ArrayList<TextEdit>();

    var constructorEdit = new TextEdit(Ranges.create(doCall), "()");
    changes.add(constructorEdit);

    var indentSize = Ranges.create(lValue).getStart().getCharacter();

    var rparenRange = Ranges.create(doCall.RPAREN());
    var constructorLine = rparenRange.getEnd().getLine();
    var position = new Position(constructorLine + 1, 0);
    var range = new Range(position, position);

    var indent = documentContext.getContentList()[constructorLine].substring(0, indentSize);

    for (var i = 0; i < keys.length; i++) {
      var key = keys[i].trim();
      var value = "";
      var separator = "";
      if (parameters.size() > i + 1) {
        value = parameters.get(i + 1).getText();
        separator = ", ";
      }

      var newText = String.format("%s%s.%s(\"%s\"%s%s);\n", indent, lValueName, insert, key, separator, value);
      var textEdit = new TextEdit(range, newText);
      changes.add(textEdit);
    }

    workspaceEdit.setChanges(Map.of(documentContext.getUri().toString(), changes));

    var codeAction = new CodeAction();
    codeAction.setEdit(workspaceEdit);
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setIsPreferred(Boolean.TRUE);
    codeAction.setTitle(Resources.getResourceString(configuration.getLanguage(), getClass(), "title"));

    return Collections.singletonList(codeAction);

  }

  private static boolean isParentAssignment(BSLParser.DoCallContext doCall, BSLParser.AssignmentContext assignment) {
    return assignment.expression().member().stream()
      .map(BSLParser.MemberContext::complexIdentifier)
      .map(BSLParser.ComplexIdentifierContext::newExpression)
      .filter(newExpressionContext -> newExpressionContext == doCall.getParent())
      .findAny().isEmpty();
  }
}
