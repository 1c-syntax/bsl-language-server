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
package com.github._1c_syntax.bsl.languageserver.utils.variable.types;

import com.github._1c_syntax.bsl.languageserver.utils.variable.scope.ProgramScope;
import com.github._1c_syntax.bsl.languageserver.utils.variable.values.V8BasicValue;
import com.github._1c_syntax.bsl.languageserver.utils.variable.values.V8Value;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.ToString;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class V8TypeHelper {
  private static V8TypeFromPresentationSupplier anyTypeSupplier = (typeNameValue) -> Optional.of(new AnyType(typeNameValue));

  private static Function<BSLParser.ExpressionContext, Optional<? extends V8Value>> GET_CONST_VALUE_FROM_EXPRESSION = (paramListContext) -> {
    if (paramListContext.member().size() == 1) {
      return getConstValue(paramListContext.member(0).constValue());
    } else {
      return Optional.empty();
    }
  };


  private static Predicate<V8Value> CONST_VALUE_TYPE_IS_STRING = constValue -> V8BasicType.STRING_TYPE.equals(constValue.getType());
  private static Predicate<V8Value> CONST_VALUE_TYPE_IS_NUMBER = constValue -> V8BasicType.NUMBER_TYPE.equals(constValue.getType());
  private static Predicate<V8Value> CONST_VALUE_TYPE_IS_BOOLEAN = constValue -> V8BasicType.BOOLEAN_TYPE.equals(constValue.getType());

  /**
   * @param constValue Константное значение, тип которого необходимо получить
   * @return тип переданного выражения или null
   */
  public static V8Type getConstValueType(BSLParser.ConstValueContext constValue) {

    return getConstValue(constValue)
      .map(V8Value::getType)
      .orElse(null);

  }

  /**
   * @param constValue Константное значение, значение которого необходимо получить
   * @return значение переданного выражения или null
   */
  public static Optional<? extends V8Value> getConstValue(BSLParser.ConstValueContext constValue) {
    if (constValue == null) {
      return Optional.empty();
    }

    if (constValue.string() != null) {
      return V8BasicValue.fromStringLiteral(constValue.string().getText());
    } else if (constValue.DATETIME() != null) {
      // TODO need reimplement V8BasicType.DATE_TYPE;
    } else if (constValue.numeric() != null) {
      return V8BasicValue.fromNumberLiteral(constValue.numeric().getText());
    } else if (constValue.TRUE() != null) {
      return Optional.of(V8BasicValue.TRUE);
    } else if (constValue.FALSE() != null) {
      return Optional.of(V8BasicValue.FALSE);
    } else if (constValue.NULL() != null) {
      return Optional.of(V8BasicValue.NULL);
    } else if (constValue.UNDEFINED() != null) {
      return Optional.of(V8BasicValue.UNDEFINED);
    }
    return Optional.empty();
  }


  public static Optional<BSLParser.ExpressionContext> getParamByIndexInDoCallContext(BSLParser.DoCallContext doCallContext, int index) {
    return Optional.ofNullable(doCallContext)
      .map(BSLParser.DoCallContext::callParamList)
      .stream()
      .flatMap(e -> e.callParam().stream())
      .skip(index)
      .findFirst()
      .map(BSLParser.CallParamContext::expression);
  }

  public static Supplier<? extends Optional<String>> getStringConstantFromFirstParam(BSLParser.DoCallContext doCallContext) {
    return () -> getParamByIndexInDoCallContext(doCallContext, 0)
      .flatMap(GET_CONST_VALUE_FROM_EXPRESSION)
      .filter(CONST_VALUE_TYPE_IS_STRING)
      .map(V8Value::getValue)
      .filter(String.class::isInstance)
      .map(String.class::cast);
  }

  public static Supplier<? extends Optional<Float>> getFloatNumberConstantFromParam(BSLParser.DoCallContext doCallContext, int index) {
    return () -> getParamByIndexInDoCallContext(doCallContext, index)
      .flatMap(GET_CONST_VALUE_FROM_EXPRESSION)
      .filter(CONST_VALUE_TYPE_IS_NUMBER)
      .map(V8Value::getValue)
      .filter(Float.class::isInstance)
      .map(Float.class::cast);

  }

  public static Supplier<? extends Optional<Boolean>> getBooleanConstantFromParam(BSLParser.DoCallContext doCallContext, int index, Boolean defaultValue) {
    return () -> {
      var param = getParamByIndexInDoCallContext(doCallContext, index);
      if (param.isPresent()) {
        return param
          .flatMap(GET_CONST_VALUE_FROM_EXPRESSION)
          .filter(CONST_VALUE_TYPE_IS_BOOLEAN)
          .map(V8Value::getValue)
          .filter(Boolean.class::isInstance)
          .map(Boolean.class::cast);
      } else {
        return Optional.ofNullable(defaultValue);
      }
    };
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
      return V8TypeHelper.getLimitedText(parent, modifier);
    }
    return null;
  }

  public static String getVariableNameFromAccessCallContext(BSLParser.AccessCallContext accessCall) {
    String variableName = null;
    BSLParserRuleContext parent = (BSLParserRuleContext) accessCall.getParent();
    if (parent instanceof BSLParser.CallStatementContext) {
      variableName = V8TypeHelper.getVariableNameFromCallStatementContext((BSLParser.CallStatementContext) parent, accessCall);
    } else if (parent instanceof BSLParser.ModifierContext) {
      variableName = V8TypeHelper.getVariableNameFromModifierContext((BSLParser.ModifierContext) parent);
    }
    return variableName;
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

  public static Set<V8Type> getTypesFromExpressionContext(BSLParser.ExpressionContext ctx, ProgramScope programScope) {
    List<? extends BSLParser.MemberContext> members = ctx.member();
    if (members.size() != 1) {
      if (ctx.operation().size() == 0) {
        return null;
      }
      BSLParser.OperationContext firstOperation = ctx.operation(0);
      if (firstOperation.boolOperation() != null
        || firstOperation.compareOperation() != null) {
        return Collections.singleton(V8BasicType.BOOLEAN_TYPE);
      } else if(firstOperation.MODULO() != null
        || firstOperation.QUOTIENT() != null
        || firstOperation.MUL() != null) {
        return Collections.singleton(V8BasicType.NUMBER_TYPE);
      }
    }

    BSLParser.MemberContext firstMember = members.get(0);
    if (firstMember == null) {
      return null;
    }
    Set<V8Type> types;

    if (firstMember.complexIdentifier() != null) {
      types = V8TypeHelper.getTypesFromComplexIdentifier(firstMember.complexIdentifier(), programScope);
    } else if (firstMember.constValue() != null) {
      types = Collections.singleton(V8TypeHelper.getConstValueType(firstMember.constValue()));
    } else if (firstMember.expression() != null) {
      types = getTypesFromExpressionContext(firstMember.expression(), programScope);
    } else {
      types = Collections.singleton(V8BasicType.UNDEFINED_TYPE);
    }
    return types;
  }

  @ToString
  static class AnyType implements V8Type {

    String name;

    AnyType(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return this.name;
    }
  }
}

