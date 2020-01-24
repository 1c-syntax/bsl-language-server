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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Trees {

  private Trees() {
    // only statics
  }

  /**
   * Обертки Trees
   */

  public static Collection<ParseTree> findAllRuleNodes(ParseTree t, int ruleIndex) {
    return org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(t, ruleIndex);
  }

  public static List<Tree> getChildren(Tree t) {
    return org.antlr.v4.runtime.tree.Trees.getChildren(t);
  }

  public static Collection<ParseTree> findAllTokenNodes(ParseTree t, int ttype) {
    return org.antlr.v4.runtime.tree.Trees.findAllTokenNodes(t, ttype);
  }

  public static List<ParseTree> getDescendants(ParseTree t) {
    return org.antlr.v4.runtime.tree.Trees.getDescendants(t);
  }

  /**
   * Собственная реализация
   */

  private static int getRuleIndex(ParseTree node) {
    if (node instanceof TerminalNode) {
      return ((TerminalNode) node).getSymbol().getType();
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
        .filter(node -> node instanceof BSLParserRuleContext)
        .filter(node -> (node.equals(tnc)
          || getRuleIndex(node) == ruleindex))
        .collect(Collectors.toList());
    }
    return descendants;
  }

  /**
   * Ищем предка элемента по указанному типу BSLParser
   * Пример:
   * ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
   */
  @CheckForNull
  public static ParserRuleContext getAncestorByRuleIndex(ParserRuleContext element, int type) {
    ParserRuleContext parent = element.getParent();
    if (parent == null) {
      return null;
    }
    if (parent.getRuleIndex() == type) {
      return parent;
    }
    return getAncestorByRuleIndex(parent, type);
  }

  /**
   * Проверяет среди дочерних элементов наличие ноды с ошибкой
   *
   * @return true - если есть нода с ошибкой
   */
  public static boolean findErrorNode(ParseTree tnc) {

    if (tnc instanceof BSLParserRuleContext) {
      if (((BSLParserRuleContext) tnc).exception != null) {
        return true;
      }

      for (int i = 0; i < tnc.getChildCount(); i++) {
        if (findErrorNode(tnc.getChild(i))) {
          return true;
        }
      }
    }
    return false;
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
      return getRootParent((BSLParserRuleContext) tnc.getParent());
    }

    return tnc;
  }

  /**
   * Рекурсивно находит самого верхнего родителя текущей ноды нужно типа
   */
  public static BSLParserRuleContext getRootParent(BSLParserRuleContext tnc, int ruleindex) {
    if (tnc.getParent() == null) {
      return null;
    }

    if (getRuleIndex(tnc.getParent()) == ruleindex) {
      return (BSLParserRuleContext) tnc.getParent();
    } else {
      return getRootParent((BSLParserRuleContext) tnc.getParent(), ruleindex);
    }
  }

  /**
   * Получает детей с нужными типомами
   */
  public static List<BSLParserRuleContext> getChildren(Tree t, Integer... ruleIndex) {
    return IntStream.range(0, t.getChildCount())
      .mapToObj(t::getChild)
      .filter((Tree child) ->
        child instanceof BSLParserRuleContext
          && Arrays.asList(ruleIndex).contains(((BSLParserRuleContext) child).getRuleIndex()))
      .map(child -> (BSLParserRuleContext) child)
      .collect(Collectors.toList());
  }

  /**
   * Получает дочерние ноды с нужными типами
   */
  public static Collection<ParserRuleContext> findAllRuleNodes(ParseTree t, Integer... index) {
    List<ParserRuleContext> nodes = new ArrayList<>();
    List<Integer> indexes = Arrays.asList(index);

    if (t instanceof ParserRuleContext
      && indexes.contains(((ParserRuleContext) t).getRuleIndex())) {
      nodes.add((ParserRuleContext) t);
    }

    IntStream.range(0, t.getChildCount())
      .mapToObj(i -> findAllRuleNodes(t.getChild(i), index))
      .forEachOrdered(nodes::addAll);

    return nodes;
  }
}
