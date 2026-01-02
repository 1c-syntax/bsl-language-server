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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.util.Positions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Утилитный класс для работы с синтаксическими деревьями ANTLR.
 * <p>
 * Предоставляет методы для навигации, поиска и анализа узлов
 * в дереве разбора BSL-кода.
 */
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

  /*
   * Обертки Trees
   */

  /**
   * Найти все узлы указанного правила в дереве.
   *
   * @param t         Дерево разбора
   * @param ruleIndex Индекс правила для поиска
   * @return Коллекция найденных узлов
   */
  public static <T extends ParseTree> Collection<T> findAllRuleNodes(ParseTree t, int ruleIndex) {
    return org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(t, ruleIndex);
  }

  /**
   * Получить список дочерних узлов.
   *
   * @param t Узел дерева
   * @return Список дочерних узлов
   */
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
  public static List<Token> getTokens(@Nullable ParseTree tree) {
    if (tree == null) {
      return Collections.emptyList();
    }
    return org.antlr.v4.runtime.tree.Trees.getTokens(tree);
  }

  /**
   * Найти все узлы указанного типа токена в дереве.
   *
   * @param t     Дерево разбора
   * @param ttype Тип токена для поиска
   * @return Коллекция найденных узлов
   */
  public static Collection<ParseTree> findAllTokenNodes(ParseTree t, int ttype) {
    return org.antlr.v4.runtime.tree.Trees.findAllTokenNodes(t, ttype);
  }

  /**
   * Получить список всех потомков узла (плоский список).
   *
   * @param t Узел дерева
   * @return Список всех потомков
   */
  public static List<ParseTree> getDescendants(ParseTree t) {
    return org.antlr.v4.runtime.tree.Trees.getDescendants(t);
  }

  /**
   * Ищем предка элемента по указанному типу BSLParser
   * Пример:
   * ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
   */
  @Nullable
  public static <T extends ParserRuleContext> T getAncestorByRuleIndex(ParserRuleContext element, int type) {
    return org.antlr.v4.runtime.tree.Trees.getAncestor(element, type);
  }

  /**
   * Проверяет среди всех дочерних элементов (рекурсивно) наличие узла с ошибкой
   *
   * @return true - если есть узел с ошибкой
   */
  public static boolean treeContainsErrors(ParseTree tnc) {
    return org.antlr.v4.runtime.tree.Trees.treeContainsErrors(tnc);
  }

  /**
   * Проверяет среди дочерних элементов узла наличие узла с ошибкой
   *
   * @return true - если есть узел с ошибкой
   */
  public static boolean nodeContainsErrors(ParseTree tnc) {
    return org.antlr.v4.runtime.tree.Trees.nodeContainsErrors(tnc);
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
    return org.antlr.v4.runtime.tree.Trees.getPreviousNode(parent, tnc, ruleindex);
  }

  /**
   * @param tokens     - полный список токенов (см. {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext#getTokens()}
   * @param tokenIndex - индекс текущего токена в переданном списке токенов
   * @param tokenType  - тип искомого токена (см. {@link com.github._1c_syntax.bsl.parser.BSLParser}
   * @return предыдущий токен, если он был найден
   */
  public Optional<Token> getPreviousTokenFromDefaultChannel(List<Token> tokens, int tokenIndex, int tokenType) {
    return org.antlr.v4.runtime.tree.Trees.getPreviousTokenFromDefaultChannel(tokens, tokenIndex, tokenType);
  }

  /**
   * @param tokens     - полный список токенов (см. {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext#getTokens()}
   * @param tokenIndex - индекс текущего токена в переданном списке токенов
   * @return предыдущий токен, если он был найден
   */
  public static Optional<Token> getPreviousTokenFromDefaultChannel(List<Token> tokens, int tokenIndex) {
    return org.antlr.v4.runtime.tree.Trees.getPreviousTokenFromDefaultChannel(tokens, tokenIndex);
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
    return org.antlr.v4.runtime.tree.Trees.getNextNode(parent, tnc, ruleindex);
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды
   */
  public static ParserRuleContext getRootParent(ParserRuleContext tnc) {
    return org.antlr.v4.runtime.tree.Trees.getAncestor(tnc);
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды нужного типа
   *
   * @param tnc       - нода, для которой ищем родителя
   * @param ruleindex - BSLParser.RULE_*
   * @return tnc - если родитель не найден, вернет null
   */
  @Nullable
  public static ParserRuleContext getRootParent(ParserRuleContext tnc, int ruleindex) {
    return org.antlr.v4.runtime.tree.Trees.getAncestor(tnc, ruleindex);
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды одного из нужных типов
   *
   * @param tnc     - нода, для которой ищем родителя
   * @param indexes - Collection of BSLParser.RULE_*
   * @return tnc - если родитель не найден, вернет null
   */
  @Nullable
  public static ParserRuleContext getRootParent(ParserRuleContext tnc, Collection<Integer> indexes) {
    return org.antlr.v4.runtime.tree.Trees.getAncestor(tnc, indexes);
  }

  /**
   * Получает детей с нужными типами
   */
  public static List<ParserRuleContext> getChildren(Tree t, Integer... ruleIndex) {
    return org.antlr.v4.runtime.tree.Trees.getChildren(t, ruleIndex);
  }

  /**
   * Получает первого ребенка с одним из нужных типов
   *
   * @param t         - нода, для которой ищем ребенка
   * @param ruleIndex - arrays of BSLParser.RULE_*
   * @return child - если первый ребенок не найден, вернет Optional
   */
  public static Optional<ParserRuleContext> getFirstChild(Tree t, Integer... ruleIndex) {
    return org.antlr.v4.runtime.tree.Trees.getFirstChild(t, ruleIndex);
  }

  /**
   * Получает дочерние ноды с нужными типами
   */
  public static Collection<ParserRuleContext> findAllRuleNodes(ParseTree t, Integer... index) {
    return org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(t, index);
  }

  /**
   * Получает дочерние ноды с нужными типами
   */
  public static Collection<ParserRuleContext> findAllRuleNodes(ParseTree t, Collection<Integer> indexes) {
    return org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(t, indexes);
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
    return org.antlr.v4.runtime.tree.Trees.findAllTopLevelDescendantNodes(root, indexes);
  }

  /**
   * Проверяет наличие дочерней ноды с указанным типом
   */
  public static boolean nodeContains(ParseTree t, Integer... index) {
    return org.antlr.v4.runtime.tree.Trees.nodeContains(t, index);
  }

  /**
   * Проверяет наличие дочерней ноды с указанным типом исключая переданную
   */
  public static boolean nodeContains(ParseTree t, ParseTree exclude, Integer... index) {
    return org.antlr.v4.runtime.tree.Trees.nodeContains(t, exclude, index);
  }

  /**
   * Получение ноды в дереве по позиции в документе.
   *
   * @param tree     - дерево, в котором ищем
   * @param position - искомая позиция
   * @return терминальная нода на указанной позиции, если есть
   */
  public static Optional<TerminalNode> findTerminalNodeContainsPosition(ParserRuleContext tree,
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
        var node = findTerminalNodeContainsPosition((ParserRuleContext) child, position);
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
