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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Range;

import java.util.Optional;

/** Диапазоны объявлений и охватывающий метод для узлов AST, включая восстановленные после ошибок. */
final class SymbolDeclarationRanges {
  private SymbolDeclarationRanges() {
  }

  static Range methodName(ParserRuleContext ctx) {
    ParserRuleContext subNameNode;
    if (Trees.nodeContainsErrors(ctx)) {
      subNameNode = ctx;
    } else if (ctx instanceof BSLParser.SubContext subContext) {
      if (subContext.function() == null) {
        subNameNode = subContext.procedure().procDeclaration().subName();
      } else {
        subNameNode = subContext.function().funcDeclaration().subName();
      }
    } else {
      subNameNode = ctx;
    }

    return Ranges.create(subNameNode == null ? ctx : subNameNode);
  }

  static Range variableName(ParserRuleContext ctx) {
    ParserRuleContext varNameNode;

    if (Trees.nodeContainsErrors(ctx)) {
      varNameNode = ctx;
    } else if (ctx instanceof BSLParser.ModuleVarDeclarationContext moduleVarDeclarationContext) {
      varNameNode = moduleVarDeclarationContext.var_name();
    } else if (ctx instanceof BSLParser.SubVarDeclarationContext subVarDeclarationContext) {
      varNameNode = subVarDeclarationContext.var_name();
    } else {
      varNameNode = ctx;
    }

    return Ranges.create(varNameNode == null ? ctx : varNameNode);
  }

  static Optional<ParserRuleContext> enclosingMethod(ParserRuleContext node) {
    var sub = Trees.getRootParent(node, BSLParser.RULE_sub);
    return sub instanceof BSLParser.SubContext method ? Optional.of(method) : Optional.empty();
  }
}
