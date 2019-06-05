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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNodeImpl;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticHelper {

  private DiagnosticHelper() {
    // Utility class
  }

  public static boolean equalNodes(Tree leftNode, Tree rightNode) {

    if (leftNode.getChildCount() != rightNode.getChildCount()
      || !leftNode.getClass().equals(rightNode.getClass())) {
      return false;
    }

    if (leftNode instanceof TerminalNode) {

      int leftNodeType = ((TerminalNode) leftNode).getSymbol().getType();
      int rightNodeType = ((TerminalNode) rightNode).getSymbol().getType();

      if (leftNodeType != rightNodeType
        || (leftNodeType == BSLParser.STRING
          && !leftNode.toString().equals(rightNode.toString()))
        || (!leftNode.toString().equalsIgnoreCase(rightNode.toString()))) {
        return false;
      }

    }

    for (int i = 0; i < leftNode.getChildCount(); i++) {
      if (!equalNodes(leftNode.getChild(i), rightNode.getChild(i))) {
        return false;
      }
    }

    return true;
  }

  public static boolean isStructureType(ParseTree tnc) {
    return "Структура".equalsIgnoreCase(tnc.getText()) || "Structure".equalsIgnoreCase(tnc.getText());
  }

  public static boolean isFixedStructureType(ParseTree tnc) {
    return "ФиксированнаяСтруктура".equalsIgnoreCase(tnc.getText()) || "FixedStructure".equalsIgnoreCase(tnc.getText());
  }

   public static boolean isStructureConstructor(BSLParser.NewExpressionContext ctx) {
    String typeName = newExpressionTypeName(ctx);
    return "Структура".equalsIgnoreCase(typeName) || "Structure".equalsIgnoreCase(typeName);
  }

  public static boolean isFixedStructureConstructor(BSLParser.NewExpressionContext ctx) {
    String typeName = newExpressionTypeName(ctx);
    return "ФиксированнаяСтруктура".equalsIgnoreCase(typeName) || "FixedStructure".equalsIgnoreCase(typeName);
  }

  public static String newExpressionTypeName(BSLParser.NewExpressionContext ctx) {

    BSLParser.TypeNameContext typeName = ctx.typeName();
    if (typeName != null) {
      return typeName.getText();
    }

    BSLParser.DoCallContext doCallContext = ctx.doCall();
    if (doCallContext == null) {
      return null;
    }

    BSLParser.CallParamContext callParamContext = doCallContext.callParamList().callParam(0);

    if (callParamContext.start.getType() == BSLParser.STRING) {
      return callParamContext.getText().replaceAll("\"", "");
    }

    return "";

  }
  public static BSLParserRuleContext findFirstRuleNode(ParseTree t, ArrayList<Integer> ruleIndex) {
    List<ParseTree> nodes = new ArrayList<ParseTree>();
    _findRuleNodes(t, ruleIndex, nodes, 1);

    if (nodes.size() != 0)
      return (BSLParserRuleContext) nodes.get(0);

    return null;

  }

  private static void _findRuleNodes(ParseTree t, ArrayList<Integer> ruleIndex, List<? super ParseTree> nodes, int count){

    if (nodes.size() == count)
      return;

    if (t instanceof BSLParserRuleContext) {
      BSLParserRuleContext ctx = (BSLParserRuleContext) t;
      if (ruleIndex.contains(ctx.getRuleIndex())) {
        nodes.add(ctx);
      }
    }

    // check children
    for (int i = 0; i < t.getChildCount(); i++) {
      _findRuleNodes(t.getChild(i), ruleIndex, nodes, count);
    }
  }
  
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
}

