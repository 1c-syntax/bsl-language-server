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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Сужение типа переменной охраняющим условием.
 * <p>
 * Поддержаны две проверки, которыми в 1С отсекают тип:
 * <ul>
 *   <li>{@code ТипЗнч(Х) = Тип("СправочникОбъект.Товары")} — на истинной ветке
 *       переменная имеет ровно проверенный тип;</li>
 *   <li>{@code Х <> Неопределено} — на истинной ветке {@code Неопределено} из набора
 *       уходит.</li>
 * </ul>
 * Оба сравнения работают и в обратную сторону ({@code <>} против {@code =}), и на
 * ложной ветке — с противоположным смыслом.
 * <p>
 * Условие разбирается только как <b>конъюнкция</b> таких проверок: каждая из них верна
 * на истинной ветке, поэтому сужения накладываются одно на другое. Дизъюнкция не сужает
 * ничего — из {@code А ИЛИ Б} на истинной ветке не следует ни одна из частей. На ложной
 * ветке сужение применяется только к условию из одной проверки: отрицание конъюнкции —
 * это дизъюнкция, и какая именно часть оказалась ложной, неизвестно.
 */
@Component
@RequiredArgsConstructor
public class GuardConditionNarrowing {

  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");

  private static final String TYPE_OF_RU = "ТИПЗНЧ";
  private static final String TYPE_OF_EN = "TYPEOF";
  private static final String TYPE_RU = "ТИП";
  private static final String TYPE_EN = "TYPE";

  private final TypeRegistry typeRegistry;
  private final ReferenceResolver referenceResolver;

  /**
   * Сузить тип переменной по охраняющему условию.
   *
   * @param condition       выражение условия.
   * @param whenTrue        ветка условия: {@code true} — истинная, {@code false} — ложная.
   * @param incoming        тип переменной перед условием.
   * @param variable        переменная, тип которой сужается.
   * @param documentContext контекст документа с условием.
   * @return суженный тип; исходный, если условие ничего про эту переменную не утверждает.
   */
  public TypeSet narrow(
    BSLParser.ExpressionContext condition,
    boolean whenTrue,
    TypeSet incoming,
    VariableSymbol variable,
    DocumentContext documentContext
  ) {
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(condition);
    if (tree == null) {
      return incoming;
    }
    var checks = new ArrayList<BslExpression>();
    if (!collectConjuncts(tree, checks)) {
      return incoming;
    }
    if (!whenTrue && checks.size() > 1) {
      return incoming;
    }
    var narrowed = incoming;
    for (var check : checks) {
      narrowed = applyCheck(check, whenTrue, narrowed, variable, documentContext);
    }
    return narrowed;
  }

  /**
   * Разложить условие на проверки, соединённые {@code И}.
   *
   * @param node   узел условия.
   * @param target список, куда складываются проверки.
   * @return {@code false}, если встретилась дизъюнкция — тогда сужать нельзя.
   */
  private static boolean collectConjuncts(BslExpression node, List<BslExpression> target) {
    if (node instanceof BinaryOperationNode binary) {
      if (binary.getOperator() == BslOperator.OR) {
        return false;
      }
      if (binary.getOperator() == BslOperator.AND) {
        return collectConjuncts(binary.getLeft(), target)
          && collectConjuncts(binary.getRight(), target);
      }
    }
    target.add(node);
    return true;
  }

  /** Применить одну проверку к типу. */
  private TypeSet applyCheck(
    BslExpression check,
    boolean whenTrue,
    TypeSet incoming,
    VariableSymbol variable,
    DocumentContext documentContext
  ) {
    if (!(check instanceof BinaryOperationNode binary)) {
      return incoming;
    }
    var operator = binary.getOperator();
    if (operator != BslOperator.EQUAL && operator != BslOperator.NOT_EQUAL) {
      return incoming;
    }
    // «Утверждается ли равенство на этой ветке»: `<>` переворачивает смысл, ложная ветка — ещё раз.
    var asserted = (operator == BslOperator.EQUAL) == whenTrue;

    var typeName = checkedTypeName(binary, variable, documentContext);
    if (typeName != null) {
      return narrowToType(incoming, typeName, asserted, documentContext);
    }
    if (isUndefinedCheck(binary, variable, documentContext)) {
      return asserted ? TypeSet.of(UNDEFINED) : incoming.without(UNDEFINED);
    }
    return incoming;
  }

  /**
   * Имя типа из проверки {@code ТипЗнч(Х) = Тип("Имя")}, либо {@code null}, если это
   * не она или проверяется другая переменная.
   */
  @Nullable
  private String checkedTypeName(
    BinaryOperationNode binary,
    VariableSymbol variable,
    DocumentContext documentContext
  ) {
    var name = typeNameFromPair(binary.getLeft(), binary.getRight(), variable, documentContext);
    return name != null
      ? name
      : typeNameFromPair(binary.getRight(), binary.getLeft(), variable, documentContext);
  }

  /** Имя типа, если слева — {@code ТипЗнч(Х)} нашей переменной, а справа — {@code Тип("Имя")}. */
  @Nullable
  private String typeNameFromPair(
    BslExpression typeOfSide,
    BslExpression typeSide,
    VariableSymbol variable,
    DocumentContext documentContext
  ) {
    var argument = callArgument(typeOfSide, TYPE_OF_RU, TYPE_OF_EN);
    if (argument == null || !isVariable(argument, variable, documentContext)) {
      return null;
    }
    var typeArgument = callArgument(typeSide, TYPE_RU, TYPE_EN);
    return typeArgument == null ? null : stringLiteral(typeArgument);
  }

  /** Проверка {@code Х = Неопределено} нашей переменной в любом порядке операндов. */
  private boolean isUndefinedCheck(
    BinaryOperationNode binary,
    VariableSymbol variable,
    DocumentContext documentContext
  ) {
    return isVariable(binary.getLeft(), variable, documentContext) && isUndefined(binary.getRight())
      || isVariable(binary.getRight(), variable, documentContext) && isUndefined(binary.getLeft());
  }

  /**
   * Заменить набор проверенным типом либо убрать его из набора.
   * <p>
   * На истинной ветке набор заменяется целиком: проверка типа в коде утверждает про
   * переменную больше, чем известно из присваиваний. Если тип уже был в наборе,
   * он остаётся вместе со своими уточнениями.
   */
  private TypeSet narrowToType(TypeSet incoming, String typeName, boolean asserted, DocumentContext documentContext) {
    var resolved = typeRegistry.resolve(typeName, documentContext.getFileType()).orElse(null);
    if (resolved == null) {
      return incoming;
    }
    if (!asserted) {
      return incoming.without(resolved);
    }
    var retained = incoming.retaining(resolved);
    return retained.isEmpty() ? TypeSet.of(resolved) : retained;
  }

  /**
   * Единственный аргумент вызова функции с одним из указанных имён, либо {@code null},
   * если это не такой вызов.
   */
  @Nullable
  private static BslExpression callArgument(BslExpression node, String nameRu, String nameEn) {
    if (!(node instanceof MethodCallNode call) || call.arguments().size() != 1) {
      return null;
    }
    var name = call.getName().getText().toUpperCase(Locale.ROOT);
    if (!name.equals(nameRu) && !name.equals(nameEn)) {
      return null;
    }
    return call.arguments().get(0);
  }

  /** Ссылается ли узел на нашу переменную — проверяется по индексу ссылок, не по имени. */
  private boolean isVariable(BslExpression node, VariableSymbol variable, DocumentContext documentContext) {
    if (node.getNodeType() != ExpressionNodeType.IDENTIFIER
      || !(node.getRepresentingAst() instanceof TerminalNode terminal)) {
      return false;
    }
    return referenceResolver.findReference(documentContext.getUri(), terminal)
      .map(reference -> variable.equals(reference.symbol()))
      .orElse(false);
  }

  /** Литерал {@code Неопределено}. */
  private static boolean isUndefined(BslExpression node) {
    if (node.getNodeType() != ExpressionNodeType.LITERAL) {
      return false;
    }
    var ast = node.getRepresentingAst();
    if (ast instanceof BSLParser.ConstValueContext constValue) {
      return constValue.UNDEFINED() != null;
    }
    return ast instanceof TerminalNode terminal
      && terminal.getSymbol().getType() == BSLParser.UNDEFINED;
  }

  /** Содержимое строкового литерала без кавычек, либо {@code null}. */
  @Nullable
  private static String stringLiteral(BslExpression node) {
    if (node.getNodeType() != ExpressionNodeType.LITERAL) {
      return null;
    }
    var text = node.getRepresentingAst().getText();
    if (text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
      return null;
    }
    return text.substring(1, text.length() - 1);
  }
}
