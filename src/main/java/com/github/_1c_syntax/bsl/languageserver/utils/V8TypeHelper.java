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

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class V8TypeHelper {
  private static V8TypeFromPresentationSupplier anyTypeSupplier = (typeNameValue) -> Optional.of(new AnyType(typeNameValue));
  private static Function<BSLParser.CallParamListContext, Optional<? extends BSLParser.CallParamContext>> GET_FIRST_CALL_PARAM = (paramListContext) -> paramListContext.callParam().stream().findFirst();
  private static Function<List<? extends BSLParser.MemberContext>, Optional<? extends BSLParser.MemberContext>> GET_FIRST_MEMBER = (paramListContext) -> paramListContext.stream().findFirst();
  private static Predicate<BSLParser.ConstValueContext> CONST_VALUE_TYPE_IS_STRING = constValue -> V8BasicType.STRING_TYPE.equals(V8TypeHelper.getConstValueType(constValue));
  private static Predicate<BSLParser.ConstValueContext> CONST_VALUE_TYPE_IS_NUMBER = constValue -> V8BasicType.NUMBER_TYPE.equals(V8TypeHelper.getConstValueType(constValue));
  private static Predicate<BSLParser.ConstValueContext> CONST_VALUE_TYPE_IS_BOOLEAN = constValue -> V8BasicType.BOOLEAN_TYPE.equals(V8TypeHelper.getConstValueType(constValue));

  public static V8Type getConstValueType(BSLParser.ConstValueContext constValue) {

    if (constValue.string() != null) {
      return V8BasicType.STRING_TYPE;
    } else if (constValue.DATETIME() != null) {
      return V8BasicType.DATE_TYPE;
    } else if (constValue.numeric() != null) {
      return V8BasicType.NUMBER_TYPE;
    } else if (constValue.TRUE() != null) {
      return V8BasicType.BOOLEAN_TYPE;
    } else if (constValue.FALSE() != null) {
      return V8BasicType.BOOLEAN_TYPE;
    } else if (constValue.NULL() != null) {
      return V8BasicType.NULL_TYPE;
    } else if (constValue.UNDEFINED() != null) {
      return V8BasicType.UNDEFINED_TYPE;
    } else return null;
  }

  public static Supplier<? extends Optional<String>> getStringConstantFromFirstParam(BSLParser.DoCallContext doCallContext) {
    return () -> Optional.ofNullable(doCallContext)
      .map(BSLParser.DoCallContext::callParamList)
      .flatMap(GET_FIRST_CALL_PARAM)
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .flatMap(GET_FIRST_MEMBER)
      .map(BSLParser.MemberContext::constValue)
      .filter(CONST_VALUE_TYPE_IS_STRING)
      .map(RuleContext::getText)
      .map(constValueText -> constValueText.substring(1, constValueText.length() - 1));
  }

  public static Supplier<? extends Optional<Float>> getFloatNumberConstantFromParam(BSLParser.DoCallContext doCallContext, int index) {
    return () -> Optional.ofNullable(doCallContext)
      .map(BSLParser.DoCallContext::callParamList)
      .stream()
      .flatMap(e -> e.callParam().stream())
      .skip(index)
      .findFirst()
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .flatMap(GET_FIRST_MEMBER)
      .map(BSLParser.MemberContext::constValue)
      .filter(CONST_VALUE_TYPE_IS_NUMBER)
      .map(RuleContext::getText)
      .map(Float::valueOf);
  }

  public static Supplier<? extends Optional<Boolean>> getBooleanConstantFromParam(BSLParser.DoCallContext doCallContext, int index) {
    return () -> Optional.ofNullable(doCallContext)
      .map(BSLParser.DoCallContext::callParamList)
      .stream()
      .flatMap(e -> e.callParam().stream())
      .skip(index)
      .findFirst()
      .map(BSLParser.CallParamContext::expression)
      .map(BSLParser.ExpressionContext::member)
      .flatMap(GET_FIRST_MEMBER)
      .map(BSLParser.MemberContext::constValue)
      .filter(CONST_VALUE_TYPE_IS_BOOLEAN)
      .map(e -> e.TRUE() != null);
  }

  public static V8Type getTypeFromNewExpressionContext(BSLParser.NewExpressionContext newExpression, V8TypeSupplier supplier) {

    Optional<String> typeName = Optional.ofNullable(newExpression.typeName())
      .map(RuleContext::getText)
      .or(getStringConstantFromFirstParam(newExpression.doCall()));

    if (typeName.isPresent()) {
      String typeNameValue = typeName.get();
      Optional<V8Type> calculatedType = Optional.empty();
      if (supplier instanceof V8TypeFromPresentationSupplier) {
        calculatedType = ((V8TypeFromPresentationSupplier) supplier).getTypeFromPresentation(typeNameValue);
      }
      return calculatedType.orElseGet(anyTypeSupplier.getTypeFromPresentation(typeNameValue)::get);
    } else {
      return V8BasicType.UNDEFINED_TYPE;
    }
  }

  public static String getVariableNameFromComplexIdentifierContext(BSLParser.ComplexIdentifierContext complexIdentifier) {
    return getLimitedText(complexIdentifier, null);
  }

  public static String getLimitedText(ParserRuleContext context, BSLParserRuleContext limitedChild) {
    if (limitedChild == null) {
      return context.getText();
    }

    return context.children.stream()
      .takeWhile(Predicate.not(Predicate.isEqual(limitedChild)))
      .map(ParseTree::getText)
      .collect(Collectors.joining(""));
  }

  public static String getVariableNameFromCallStatementContext(BSLParser.CallStatementContext callStatement, BSLParserRuleContext limitedChild) {
    return getLimitedText(callStatement, limitedChild);
  }

  public static String getVariableNameFromModifierContext(BSLParser.ModifierContext modifier) {
    ParserRuleContext parent = modifier.getParent();
    if (parent instanceof BSLParser.ComplexIdentifierContext) {
      return V8TypeHelper.getLimitedText(parent, modifier);
    } else if (parent instanceof BSLParser.CallStatementContext) {
      return V8TypeHelper.getVariableNameFromCallStatementContext((BSLParser.CallStatementContext) parent, modifier);
    }
    return null;
  }

  public static Set<V8Type> getTypesFromComplexIdentifier(BSLParser.ComplexIdentifierContext complexId, V8TypeSupplier supplier) {
    if (complexId.newExpression() != null) {
      return Set.of(getTypeFromNewExpressionContext(complexId.newExpression(), supplier));
    } else if (complexId.IDENTIFIER() != null) {
      Optional<Set<V8Type>> calculatedTypes = Optional.empty();
      if (supplier instanceof V8TypeFromVariableSupplier) {
        calculatedTypes = ((V8TypeFromVariableSupplier) supplier).getTypesFromVariable(getVariableNameFromComplexIdentifierContext(complexId));
      }
      return calculatedTypes.orElse(Set.of(V8BasicType.UNDEFINED_TYPE));
    }
    return Set.of();
  }

  static class AnyType implements V8Type {

    String name;

    AnyType(String name) {
      this.name = name;
    }

    @Override
    public String presentation() {
      return this.name;
    }
  }
}

