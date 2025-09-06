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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.util.Positions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
public final class Trees {

  private static final Set<Integer> VALID_TOKEN_TYPES_FOR_COMMENTS_SEARCH = Set.of(
    BSLParser.ANNOTATION_ATCLIENT_SYMBOL,
    BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL,
    BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL,
    BSLParser.ANNOTATION_ATSERVER_SYMBOL,
    BSLParser.ANNOTATION_CUSTOM_SYMBOL,
    BSLParser.ANNOTATION_UNKNOWN,
    BSLParser.LINE_COMMENT,
    BSLParser.WHITE_SPACE,
    BSLParser.AMPERSAND
  );

  /**
   * Обертки Trees
   */
  @SuppressWarnings("unchecked")
  public static <T extends ParseTree>Collection<T> findAllRuleNodes(ParseTree t, int ruleIndex) {
    return (Collection<T>) org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(t, ruleIndex);
  }

  public static List<Tree> getChildren(Tree t) {
    return org.antlr.v4.runtime.tree.Trees.getChildren(t);
  }

  /**
   * Список токенов дерева разбора.
   * <p>
   * Токены формируются на основании всех потомков вида {@link TerminalNode} переданного дерева.
   *
   * @param tree Дерево разбора
   * @return Список токенов
   */
  public static List<Token> getTokens(ParseTree tree) {
    if (tree instanceof BSLParserRuleContext parserRuleContext) {
      return parserRuleContext.getTokens();
    }

    if (tree instanceof TerminalNode node) {
      var token = node.getSymbol();
      return List.of(token);
    }

    if (tree.getChildCount() == 0) {
      return Collections.emptyList();
    }

    List<Token> results = new ArrayList<>();
    getTokensFromParseTree(tree, results);
    return Collections.unmodifiableList(results);
  }

  private static void getTokensFromParseTree(ParseTree tree, List<Token> tokens) {
    for (var i = 0; i < tree.getChildCount(); i++) {
      ParseTree child = tree.getChild(i);
      if (child instanceof TerminalNode node) {
        var token = node.getSymbol();
        tokens.add(token);
      } else {
        getTokensFromParseTree(child, tokens);
      }
    }
  }

  public static Collection<ParseTree> findAllTokenNodes(ParseTree t, int ttype) {
    return org.antlr.v4.runtime.tree.Trees.findAllTokenNodes(t, ttype);
  }

  public static List<ParseTree> getDescendants(ParseTree t) {
    List<ParseTree> nodes = new ArrayList<>(t.getChildCount());
    flatten(t, nodes);
    return nodes;
  }

  private static void flatten(ParseTree t, List<ParseTree> flatList) {
    flatList.add(t);

    int n = t.getChildCount();
    for (var i = 0; i < n; i++) {
      flatten(t.getChild(i), flatList);
    }
  }

  /**
   * Собственная реализация
   */

  private static int getRuleIndex(ParseTree node) {
    if (node instanceof TerminalNode terminalNode) {
      return terminalNode.getSymbol().getType();
    } else {
      return ((BSLParserRuleContext) node).getRuleIndex();
    }
  }

  private static List<ParseTree> getDescendantsWithFilter(ParseTree parent, ParseTree tnc, int ruleindex) {
    List<ParseTree> descendants;
    if (getRuleIndex(tnc) == ruleindex) {
      descendants = new ArrayList<>(org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(parent, ruleindex));
    } else {
      descendants = org.antlr.v4.runtime.tree.Trees.getDescendants(parent)
        .stream()
        .filter(BSLParserRuleContext.class::isInstance)
        .filter(node -> (node.equals(tnc) || getRuleIndex(node) == ruleindex))
        .toList();
    }
    return descendants;
  }

  /**
   * Ищем предка элемента по указанному типу BSLParser
   * Пример:
   * BSLParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public static <T extends BSLParserRuleContext> T getAncestorByRuleIndex(BSLParserRuleContext element, int type) {
    var parent = element.getParent();
    if (parent == null) {
      return null;
    }
    if (parent.getRuleIndex() == type) {
      return (T) parent;
    }
    return getAncestorByRuleIndex(parent, type);
  }

  /**
   * Проверяет среди всех дочерних элементов (рекурсивно) наличие узла с ошибкой
   *
   * @return true - если есть узел с ошибкой
   */
  public static boolean treeContainsErrors(ParseTree tnc) {
    return treeContainsErrors(tnc, true);
  }

  /**
   * Проверяет среди дочерних элементов узла наличие узла с ошибкой
   *
   * @return true - если есть узел с ошибкой
   */
  public static boolean nodeContainsErrors(ParseTree tnc) {
    return treeContainsErrors(tnc, false);
  }

  /**
   * Выполняет поиск предыдущей ноды нужного типа
   *
   * @param parent    - родительская нода, среди дочерних которой производится поиск
   * @param tnc       - нода, для которой ищем предыдущую
   * @param ruleindex - BSLParser.RULE_*
   * @return tnc - если предыдущая нода не найдена, вернет текущую
   */
  public static ParseTree getPreviousNode(ParseTree parent, ParseTree tnc, int ruleindex) {
    List<ParseTree> descendants = getDescendantsWithFilter(parent, tnc, ruleindex);
    int pos = descendants.indexOf(tnc);
    if (pos > 0) {
      return descendants.get(pos - 1);
    }
    return tnc;
  }

  /**
   * @param tokens     - полный список токенов (см. {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext#getTokens()}
   * @param tokenIndex - индекс текущего токена в переданном списке токенов
   * @param tokenType  - тип искомого токена (см. {@link com.github._1c_syntax.bsl.parser.BSLParser}
   * @return предыдущий токен, если он был найден
   */
  public Optional<Token> getPreviousTokenFromDefaultChannel(List<Token> tokens, int tokenIndex, int tokenType) {
    while (true) {
      if (tokenIndex == 0) {
        return Optional.empty();
      }
      var token = tokens.get(tokenIndex);
      if (token.getChannel() != Token.DEFAULT_CHANNEL
        || token.getType() != tokenType) {
        tokenIndex = tokenIndex - 1;
        continue;
      }

      return Optional.of(token);
    }
  }

  /**
   * @param tokens     - полный список токенов (см. {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext#getTokens()}
   * @param tokenIndex - индекс текущего токена в переданном списке токенов
   * @return предыдущий токен, если он был найден
   */
  public static Optional<Token> getPreviousTokenFromDefaultChannel(List<Token> tokens, int tokenIndex) {
    while (true) {
      if (tokenIndex == 0) {
        return Optional.empty();
      }
      var token = tokens.get(tokenIndex);
      if (token.getChannel() != Token.DEFAULT_CHANNEL) {
        tokenIndex = tokenIndex - 1;
        continue;
      }

      return Optional.of(token);
    }
  }

  /**
   * Выполняет поиск следующей ноды нужного типа
   *
   * @param parent    - родительская нода, среди дочерних которой производится поиск
   * @param tnc       - нода, для которой ищем следующую
   * @param ruleindex - BSLParser.RULE_*
   * @return tnc - если следующая нода не найдена, вернет текущую
   */
  public static ParseTree getNextNode(ParseTree parent, ParseTree tnc, int ruleindex) {
    List<ParseTree> descendants = getDescendantsWithFilter(parent, tnc, ruleindex);
    int pos = descendants.indexOf(tnc);
    if (pos + 1 < descendants.size()) {
      return descendants.get(pos + 1);
    }
    return tnc;
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды
   */
  public static BSLParserRuleContext getRootParent(BSLParserRuleContext tnc) {
    if (tnc.getParent() != null) {
      return getRootParent(tnc.getParent());
    }
    return tnc;
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды нужного типа
   *
   * @param tnc       - нода, для которой ищем родителя
   * @param ruleindex - BSLParser.RULE_*
   * @return tnc - если родитель не найден, вернет null
   */
  @Nullable
  public static BSLParserRuleContext getRootParent(BSLParserRuleContext tnc, int ruleindex) {
    final var parent = tnc.getParent();
    if (parent == null) {
      return null;
    }

    if (getRuleIndex(parent) == ruleindex) {
      return parent;
    } else {
      return getRootParent(parent, ruleindex);
    }
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды одного из нужных типов
   *
   * @param tnc     - нода, для которой ищем родителя
   * @param indexes - Collection of BSLParser.RULE_*
   * @return tnc - если родитель не найден, вернет null
   */
  @Nullable
  public static BSLParserRuleContext getRootParent(BSLParserRuleContext tnc, Collection<Integer> indexes) {
    final var parent = tnc.getParent();
    if (parent == null) {
      return null;
    }

    if (indexes.contains(getRuleIndex(parent))) {
      return parent;
    } else {
      return getRootParent(parent, indexes);
    }
  }

  /**
   * Получает детей с нужными типами
   */
  public static List<BSLParserRuleContext> getChildren(Tree t, Integer... ruleIndex) {
    return getChildrenStream(t, ruleIndex)
      .collect(Collectors.toList());
  }

  /**
   * Получает первого ребенка с одним из нужных типов
   *
   * @param t         - нода, для которой ищем ребенка
   * @param ruleIndex - arrays of BSLParser.RULE_*
   * @return child - если первый ребенок не найден, вернет Optional
   */
  public static Optional<BSLParserRuleContext> getFirstChild(Tree t, Integer... ruleIndex) {
    return getChildrenStream(t, ruleIndex)
      .findFirst();
  }

  private static Stream<BSLParserRuleContext> getChildrenStream(Tree t, Integer[] ruleIndex) {
    List<Integer> indexes = Arrays.asList(ruleIndex);
    return IntStream.range(0, t.getChildCount())
      .mapToObj(t::getChild)
      .filter(child -> child instanceof BSLParserRuleContext rule && indexes.contains(rule.getRuleIndex()))
      .map(BSLParserRuleContext.class::cast);
  }

  /**
   * Получает дочерние ноды с нужными типами
   */
  public static Collection<ParserRuleContext> findAllRuleNodes(ParseTree t, Integer... index) {
    return findAllRuleNodes(t, Arrays.asList(index));
  }

  /**
   * Получает дочерние ноды с нужными типами
   */
  public static Collection<ParserRuleContext> findAllRuleNodes(ParseTree t, Collection<Integer> indexes) {
    List<ParserRuleContext> nodes = new ArrayList<>();

    if (t instanceof ParserRuleContext parserRuleContext && indexes.contains(parserRuleContext.getRuleIndex())) {
      nodes.add((ParserRuleContext) t);
    }

    IntStream.range(0, t.getChildCount())
      .mapToObj(i -> findAllRuleNodes(t.getChild(i), indexes))
      .forEachOrdered(nodes::addAll);

    return nodes;
  }

  /**
   * Получает "первые" дочерние ноды с нужными типами
   * ВАЖНО: поиск вглубь найденной ноды с нужными индексами не выполняется
   * Например, если указать RULE_codeBlock, то найдется только самый верхнеуровневый блок кода, все
   * вложенные найдены не будут
   * ВАЖНО: начальная нода не проверяется на условие, т.к. тогда она единственная и вернется в результате
   *
   * @param root    - начальный узел дерева
   * @param indexes - коллекция индексов
   * @return найденные узлы
   */
  public static Collection<ParserRuleContext> findAllTopLevelDescendantNodes(ParserRuleContext root,
                                                                             Collection<Integer> indexes) {
    var result = new ArrayList<ParserRuleContext>();
    root.children.stream()
      .map(node -> findAllTopLevelDescendantNodesInner(node, indexes))
      .forEach(result::addAll);
    return result;
  }

  private static Collection<ParserRuleContext> findAllTopLevelDescendantNodesInner(ParseTree root,
                                                                                   Collection<Integer> indexes) {
    if (root instanceof ParserRuleContext rule && indexes.contains(rule.getRuleIndex())) {
      return List.of(rule);
    }

    List<ParserRuleContext> result = new ArrayList<>();
    IntStream.range(0, root.getChildCount())
      .mapToObj(i -> findAllTopLevelDescendantNodesInner(root.getChild(i), indexes))
      .forEachOrdered(result::addAll);

    return result;
  }

  /**
   * Проверяет наличие дочерней ноды с указанным типом
   */
  public static boolean nodeContains(ParseTree t, Integer... index) {
    Set<Integer> indexes = new HashSet<>(Arrays.asList(index));

    if (t instanceof ParserRuleContext rule && indexes.contains(rule.getRuleIndex())) {
      return true;
    }

    return IntStream.range(0, t.getChildCount())
      .anyMatch(i -> nodeContains(t.getChild(i), index));
  }

  /**
   * Проверяет наличие дочерней ноды с указанным типом исключая переданную
   */
  public static boolean nodeContains(ParseTree t, ParseTree exclude, Integer... index) {
    Set<Integer> indexes = new HashSet<>(Arrays.asList(index));

    if (t instanceof ParserRuleContext rule && !t.equals(exclude) && indexes.contains(rule.getRuleIndex())) {
      return true;
    }

    return IntStream.range(0, t.getChildCount())
      .anyMatch(i -> nodeContains(t.getChild(i), exclude, index));
  }

  /**
   * Получение ноды в дереве по позиции в документе.
   *
   * @param tree     - дерево, в котором ищем
   * @param position - искомая позиция
   * @return терминальная нода на указанной позиции, если есть
   */
  public static Optional<TerminalNode> findTerminalNodeContainsPosition(BSLParserRuleContext tree,
                                                                        Position position) {

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
      if (child instanceof TerminalNode terminalNode) {
        var token = terminalNode.getSymbol();
        if (tokenContainsPosition(token, position)) {
          return Optional.of(terminalNode);
        }
      } else {
        Optional<TerminalNode> node = findTerminalNodeContainsPosition((BSLParserRuleContext) child, position);
        if (node.isPresent()) {
          return node;
        }
      }
    }

    return Optional.empty();
  }

  /**
   * @param tokens - список токенов из DocumentContext
   * @param token  - токен, на строке которого требуется найти висячий комментарий
   * @return - токен с комментарием, если он найден
   */
  public static Optional<Token> getTrailingComment(List<Token> tokens, Token token) {
    int index = token.getTokenIndex();
    int size = tokens.size();
    int currentIndex = index + 1;
    int line = token.getLine();

    while (currentIndex < size) {
      var nextToken = tokens.get(currentIndex);
      if (nextToken.getLine() > line) {
        break;
      }
      if (nextToken.getType() == BSLParser.LINE_COMMENT) {
        return Optional.of(nextToken);
      }
      currentIndex++;
    }

    return Optional.empty();

  }

  /**
   * Поиск комментариев назад от указанного токена
   *
   * @param tokens - список токенов DocumentContext
   * @param token  - токен, для которого требуется найти комментарии
   * @return - список найденных комментариев lines
   */
  public static List<Token> getComments(List<Token> tokens, Token token) {
    List<Token> comments = new ArrayList<>();
    fillCommentsCollection(tokens, token, comments);
    return comments;
  }

  private static void fillCommentsCollection(List<Token> tokens, Token currentToken, List<Token> lines) {
    int index = currentToken.getTokenIndex();
    if (index == 0) {
      return;
    }

    var previousToken = tokens.get(index - 1);
    if (abortSearchComments(previousToken, currentToken)) {
      return;
    }

    fillCommentsCollection(tokens, previousToken, lines);
    int type = previousToken.getType();
    if (type == BSLParser.LINE_COMMENT) {
      lines.add(previousToken);
    }
  }

  private static boolean abortSearchComments(Token previousToken, Token currentToken) {
    int type = previousToken.getType();
    return !VALID_TOKEN_TYPES_FOR_COMMENTS_SEARCH.contains(type) || isBlankLine(previousToken, currentToken);
  }

  private static boolean isBlankLine(Token previousToken, Token currentToken) {
    return previousToken.getType() == BSLParser.WHITE_SPACE
      && (previousToken.getTokenIndex() == 0
      || (previousToken.getLine() + 1) != currentToken.getLine());
  }

  private static boolean treeContainsErrors(ParseTree tnc, boolean recursive) {
    if (!(tnc instanceof BSLParserRuleContext ruleContext)) {
      return false;
    }

    if (ruleContext.exception != null) {
      return true;
    }

    return recursive
      && ruleContext.children != null
      && ruleContext.children.stream().anyMatch(Trees::treeContainsErrors);
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
