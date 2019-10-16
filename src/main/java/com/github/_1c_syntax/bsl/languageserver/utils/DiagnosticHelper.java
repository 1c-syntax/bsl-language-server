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
import org.antlr.v4.runtime.tree.ErrorNodeImpl;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

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

  public static boolean findErrorNode(ParseTree tnc) {

    if (tnc instanceof TerminalNode
      && tnc.getClass().equals(ErrorNodeImpl.class)) {
      return true;
    }

    for (int i = 0; i < tnc.getChildCount(); i++) {
      if (findErrorNode(tnc.getChild(i))) {
        return true;
      }
    }
    return false;
  }
}
