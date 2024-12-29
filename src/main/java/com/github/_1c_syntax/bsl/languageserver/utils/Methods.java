/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class Methods {

  public static Optional<Token> getMethodName(BSLParser.CallStatementContext ctx) {
    var modifiers = ctx.modifier();
    Optional<Token> methodName;
    if (ctx.globalMethodCall() != null) {
      methodName = getMethodName(ctx.globalMethodCall());
    } else {
      methodName = getMethodName(ctx.accessCall());
    }

    if (modifiers.isEmpty()) {
      return methodName;
    } else {
      return getMethodName(modifiers).or(() -> methodName);
    }
  }

  public static Optional<Token> getMethodName(BSLParser.GlobalMethodCallContext ctx) {
    return Optional.of(ctx.methodName().getStart());
  }

  public static Optional<Token> getMethodName(BSLParser.AccessCallContext ctx) {
    return Optional.of(ctx.methodCall().methodName().getStart());
  }

  public static Optional<Token> getMethodName(BSLParser.ComplexIdentifierContext ctx) {
    return getMethodName(ctx.modifier());
  }

  public static Optional<Token> getMethodName(List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .map(BSLParser.ModifierContext::accessCall)
      .filter(Objects::nonNull)
      .map(Methods::getMethodName)
      .findFirst()
      .orElse(Optional.empty());
  }

  public static Optional<Token> getMethodName(BSLParser.CallParamContext callParamContext) {
    return NotifyDescription.getFirstMember(callParamContext)
      .map(BSLParser.MemberContext::constValue)
      .map(BSLParser.ConstValueContext::string)
      .map(BSLParser.StringContext::getStart);
  }

  public static Optional<Token> getMethodName(BSLParser.LValueContext lValueContext) {
    return Optional.ofNullable(lValueContext.acceptor())
      .map(BSLParser.AcceptorContext::modifier)
      .flatMap(Methods::getMethodName);
  }

  public static Optional<MethodSymbol> getOscriptClassConstructor(SymbolTree symbolTree) {
    return symbolTree.getMethodSymbol("ПриСозданииОбъекта")
      .or(() -> symbolTree.getMethodSymbol("OnObjectCreate"));
  }

}
