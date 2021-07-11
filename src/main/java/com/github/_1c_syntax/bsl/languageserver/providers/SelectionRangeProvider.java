/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.util.Positions;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Провайдер, обрабатывающий запросы textDocument/selectionRange.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_selectionRange">Selection Range Request specification</a>.
 */
@Component
public class SelectionRangeProvider {

  private static final Set<Integer> SKIPPED_NODES = Set.of(
    BSLParser.RULE_doCall,
    BSLParser.RULE_accessCall,
    BSLParser.RULE_globalMethodCall,
    BSLParser.RULE_callStatement,
    BSLParser.RULE_waitStatement,
    BSLParser.RULE_compoundStatement,
    BSLParser.RULE_whileStatement,
    BSLParser.RULE_ifStatement,
    BSLParser.RULE_forStatement,
    BSLParser.RULE_forEachStatement,
    BSLParser.RULE_tryStatement,
    BSLParser.RULE_returnStatement,
    BSLParser.RULE_continueStatement,
    BSLParser.RULE_breakStatement,
    BSLParser.RULE_raiseStatement,
    BSLParser.RULE_executeStatement,
    BSLParser.RULE_gotoStatement,
    BSLParser.RULE_addHandlerStatement,
    BSLParser.RULE_removeHandlerStatement,
    BSLParser.RULE_assignment
  );

  /**
   * Получение данных о {@link SelectionRange} по позиции в документе.
   *
   * @param documentContext контекст документа.
   * @param params параметры вызова.
   * @return список найденных диапазонов.
   */
  public List<SelectionRange> getSelectionRange(DocumentContext documentContext, SelectionRangeParams params) {

    var positions = params.getPositions();
    var ast = documentContext.getAst();

    // Result must contains all elements from input
    return positions.stream()
      .map(position -> findNodeContainsPosition(ast, position))
      .map(terminalNode -> terminalNode.orElse(null))
      .map(SelectionRangeProvider::toSelectionRange)
      .collect(Collectors.toList());
  }

  @CheckForNull
  private static SelectionRange toSelectionRange(@Nullable ParseTree node) {
    if (node == null) {
      return null;
    }

    var range = Ranges.create(node);

    var selectionRange = new SelectionRange();
    selectionRange.setRange(range);

    nextParentWithDifferentRange(node)
      .map(SelectionRangeProvider::toSelectionRange)
      .ifPresent(selectionRange::setParent);

    return selectionRange;
  }

  private static Optional<ParseTree> nextParentWithDifferentRange(ParseTree ctx) {
    var parent = getParentContext(ctx);
    if (parent == null) {
      return Optional.empty();
    }

    var needToSkipNode = SKIPPED_NODES.contains(parent.getRuleIndex());
    needToSkipNode = needToSkipNode || ifBranchMatchesIfStatement(parent);

    var currentRange = Ranges.create(ctx);
    var parentRange = Ranges.create(parent);

    if (needToSkipNode || parentRange.equals(currentRange)) {
      return nextParentWithDifferentRange(parent);
    }

    return Optional.of(parent);
  }

  private static BSLParserRuleContext getParentContext(ParseTree ctx) {
    if (ctx instanceof BSLParser.StatementContext) {
      return getStatementParent((BSLParser.StatementContext) ctx);
    }

    return getDefaultParent(ctx);
  }

  @CheckForNull
  private static BSLParserRuleContext getDefaultParent(ParseTree ctx) {
    return  (BSLParserRuleContext) ctx.getParent();
  }

  private static BSLParserRuleContext getStatementParent(BSLParser.StatementContext statement) {

    var parent = getDefaultParent(statement);

    var statementLine = statement.getStart().getLine();

    var codeBlock = (BSLParser.CodeBlockContext) statement.getParent();
    var children = Trees.getChildren(codeBlock);
    var currentPosition = children.indexOf(statement);

    List<ParserRuleContext> nearbyStatements = new ArrayList<>();

    // Проверим узлы после текущего выражения.
    var localLine = statementLine;
    for (int i = currentPosition + 1; i < children.size(); i++) {
      var child = (BSLParserRuleContext) children.get(i);
      if (child.getStart().getLine() == localLine + 1) {
        nearbyStatements.add(child);
        localLine++;
      } else {
        break;
      }
    }

    // Проверим узлы перед текущим выражением
    localLine = statementLine;
    for (int i = currentPosition - 1; i >= 0; i--) {
      var child = (BSLParserRuleContext) children.get(i);
      if (child.getStart().getLine() == localLine - 1) {
        nearbyStatements.add(child);
        localLine--;
      } else {
        break;
      }
    }

    if (!nearbyStatements.isEmpty() && (nearbyStatements.size() + 1 != children.size())) {

      var statementsBlock = new BSLParserRuleContext();
      statementsBlock.setParent(parent);

      nearbyStatements.add(statement);
      nearbyStatements.sort(Comparator.comparing(ruleContext -> ruleContext.getStart().getLine()));

      // перезапись parent для прохождения ассерта внутри addChild.
      nearbyStatements.forEach(ruleContext -> ruleContext.setParent(statementsBlock));
      nearbyStatements.forEach(statementsBlock::addChild);
      // возвращение parent
      nearbyStatements.forEach(ruleContext -> ruleContext.setParent(codeBlock));

      statementsBlock.start = nearbyStatements.get(0).getStart();
      statementsBlock.stop = nearbyStatements.get(nearbyStatements.size() - 1).getStop();

      parent = statementsBlock;
    }

    return parent;
  }

  private static boolean ifBranchMatchesIfStatement(BSLParserRuleContext ctx) {
    if (!(ctx instanceof BSLParser.IfBranchContext)) {
      return false;
    }

    var ifBranch = (BSLParser.IfBranchContext) ctx;
    var ifStatement = (BSLParser.IfStatementContext) ifBranch.getParent();
    return ifStatement.elseBranch() == null && ifStatement.elsifBranch().isEmpty();
  }

  private static Optional<TerminalNode> findNodeContainsPosition(BSLParserRuleContext tree, Position position) {

    if (tree.getTokens().isEmpty()) {
      return Optional.empty();
    }

    var start = tree.getStart();
    var stop = tree.getStop();

    if (!(positionIsAfterOrOnToken(position, start) && positionIsBeforeOrOnToken(position, stop))) {
      return Optional.empty();
    }

    var children = Trees.getChildren(tree);

    for (Tree child : children) {
      if (child instanceof TerminalNode) {
        var terminalNode = (TerminalNode) child;
        var token = terminalNode.getSymbol();
        if (tokenContainsPosition(token, position)) {
          return Optional.of(terminalNode);
        }
      } else {
        Optional<TerminalNode> node = findNodeContainsPosition((BSLParserRuleContext) child, position);
        if (node.isPresent()) {
          return node;
        }
      }
    }

    return Optional.empty();
  }

  private static boolean tokenContainsPosition(Token token, Position position) {
    var tokenRange = Ranges.create(token);
    return Ranges.containsPosition(tokenRange, position);
  }

  private static boolean positionIsBeforeOrOnToken(Position position, Token token) {
    var tokenRange = Ranges.create(token);
    var end = tokenRange.getEnd();
    return Positions.isBefore(position, end) || end.equals(position);
  }

  private static boolean positionIsAfterOrOnToken(Position position, Token token) {
    var tokenRange = Ranges.create(token);
    var start = tokenRange.getStart();
    return Positions.isBefore(start, position) || start.equals(position);
  }

}
