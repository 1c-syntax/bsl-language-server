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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TernaryOperatorNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Ленивый инференсер типов выражений.
 * <p>
 * Работает поверх {@link BslExpression}; устойчив к битым/неполным выражениям
 * (любая ошибка → {@link TypeSet#EMPTY} плюс {@code UNKNOWN}-семантика на
 * верхнем уровне). Защита от циклов — стек посещённых символов в
 * {@link InferenceContext}, ограниченный по глубине.
 * <p>
 * Резолв идентификаторов — через {@link ReferenceResolver}, который дёшев и
 * накапливает finder'ы из всего проекта (variable, method, module и т.д.).
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ExpressionTypeInferencer {

  private static final int MAX_DEPTH = 32;

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef BOOLEAN = new TypeRef(TypeKind.PRIMITIVE, "Булево");
  private static final TypeRef DATE = new TypeRef(TypeKind.PRIMITIVE, "Дата");
  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");
  private static final TypeRef NULL = new TypeRef(TypeKind.PRIMITIVE, "Null");

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;
  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Вывести типы выражения в контексте документа.
   */
  public TypeSet infer(BslExpression expression, DocumentContext documentContext) {
    if (expression == null) {
      return TypeSet.EMPTY;
    }
    var ctx = new InferenceContext(documentContext);
    try {
      return inferInternal(expression, ctx);
    } catch (StackOverflowError | RuntimeException e) {
      return TypeSet.EMPTY;
    }
  }

  /**
   * Вывести типы символа (метод, переменная, параметр).
   */
  public TypeSet inferSymbol(SourceDefinedSymbol symbol) {
    if (symbol instanceof MethodSymbol method) {
      return symbolTypeIndex.getDeclaredReturnTypes(method);
    }
    if (symbol instanceof VariableSymbol variable) {
      var ctx = new InferenceContext(variable.getOwner());
      return inferVariable(variable, ctx);
    }
    return TypeSet.EMPTY;
  }

  // ---------------------------------------------------------------------------
  // Core dispatch
  // ---------------------------------------------------------------------------

  private TypeSet inferInternal(BslExpression node, InferenceContext ctx) {
    if (node == null || ctx.depth >= MAX_DEPTH) {
      return TypeSet.EMPTY;
    }
    ctx.depth++;
    try {
      return switch (node.getNodeType()) {
        case LITERAL -> inferLiteral(node);
        case IDENTIFIER -> inferIdentifier(node, ctx);
        case CALL -> inferCall(node, ctx);
        case BINARY_OP -> inferBinary((BinaryOperationNode) node, ctx);
        case UNARY_OP -> inferUnary((UnaryOperationNode) node);
        case TERNARY_OP -> inferTernary((TernaryOperatorNode) node, ctx);
        case SKIPPED_CALL_ARG, ERROR -> TypeSet.EMPTY;
      };
    } finally {
      ctx.depth--;
    }
  }

  // ---------------------------------------------------------------------------
  // Literals & identifiers
  // ---------------------------------------------------------------------------

  private TypeSet inferLiteral(BslExpression node) {
    var ast = node.getRepresentingAst();
    if (ast instanceof BSLParser.ConstValueContext constValue) {
      return literalTypeOf(constValue);
    }
    if (ast instanceof TerminalNode terminal) {
      return literalTypeOf(terminal);
    }
    return TypeSet.EMPTY;
  }

  private TypeSet literalTypeOf(BSLParser.ConstValueContext ctx) {
    if (ctx.string() != null) return TypeSet.of(STRING);
    if (ctx.numeric() != null) return TypeSet.of(NUMBER);
    if (ctx.TRUE() != null || ctx.FALSE() != null) return TypeSet.of(BOOLEAN);
    if (ctx.DATETIME() != null) return TypeSet.of(DATE);
    if (ctx.UNDEFINED() != null) return TypeSet.of(UNDEFINED);
    if (ctx.NULL() != null) return TypeSet.of(NULL);
    return TypeSet.EMPTY;
  }

  private TypeSet literalTypeOf(TerminalNode terminal) {
    var type = terminal.getSymbol().getType();
    if (type == BSLParser.STRING || type == BSLParser.STRINGSTART
      || type == BSLParser.STRINGPART || type == BSLParser.STRINGTAIL) {
      return TypeSet.of(STRING);
    }
    if (type == BSLParser.TRUE || type == BSLParser.FALSE) return TypeSet.of(BOOLEAN);
    if (type == BSLParser.UNDEFINED) return TypeSet.of(UNDEFINED);
    if (type == BSLParser.NULL) return TypeSet.of(NULL);
    if (type == BSLParser.DATETIME) return TypeSet.of(DATE);
    if (type == BSLParser.FLOAT || type == BSLParser.DECIMAL) return TypeSet.of(NUMBER);
    return TypeSet.EMPTY;
  }

  private TypeSet inferIdentifier(BslExpression node, InferenceContext ctx) {
    var ast = node.getRepresentingAst();
    if (!(ast instanceof TerminalNode terminal)) {
      return TypeSet.EMPTY;
    }
    var token = terminal.getSymbol();
    var position = new Position(token.getLine() - 1, token.getCharPositionInLine() + 1);
    var resolved = resolveReferenceAt(ctx, position);
    if (!resolved.isEmpty()) {
      return resolved;
    }
    var text = terminal.getText();
    if (text == null || text.isBlank()) {
      return TypeSet.EMPTY;
    }
    // Глобальная область: платформенные глобалы, library-модули,
    // common-модули — все приходят через единый GlobalSymbolScope.
    var fromScope = globalScopeProvider.findGlobal(text, ctx.documentContext.getFileType())
      .map(symbol -> {
        if (symbol instanceof SyntheticSymbol s) {
          return s.getValueType();
        }
        // Для source-defined символов тип берётся отдельно (через SymbolTypeIndex);
        // вернёмся к этому, когда oscript-модули будут регистрироваться как ModuleSymbol.
        return TypeRef.UNKNOWN;
      })
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN))
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
    return fromScope;
  }

  // ---------------------------------------------------------------------------
  // Calls (constructor / method)
  // ---------------------------------------------------------------------------

  private TypeSet inferCall(BslExpression node, InferenceContext ctx) {
    if (node instanceof ConstructorCallNode constructor) {
      return inferConstructor(constructor, ctx);
    }
    if (node instanceof MethodCallNode methodCall) {
      return inferMethodCall(methodCall, ctx);
    }
    return TypeSet.EMPTY;
  }

  private TypeSet inferConstructor(ConstructorCallNode constructor, InferenceContext ctx) {
    var typeName = extractTypeName(constructor);
    if (typeName == null || typeName.isBlank()) {
      return TypeSet.EMPTY;
    }
    return typeRegistry.resolve(typeName, ctx.documentContext.getFileType())
      .map(TypeSet::of)
      .orElseGet(() -> TypeSet.of(typeRegistry.intern(TypeKind.USER, typeName)));
  }

  private static String extractTypeName(ConstructorCallNode constructor) {
    var typeNameNode = constructor.getTypeName();
    if (typeNameNode == null) {
      return null;
    }
    var ast = typeNameNode.getRepresentingAst();
    if (ast == null) {
      return null;
    }
    return stripQuotes(ast.getText());
  }

  private static String stripQuotes(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    var first = s.charAt(0);
    var last = s.charAt(s.length() - 1);
    if ((first == '"' || first == '\'') && first == last) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private TypeSet inferMethodCall(MethodCallNode call, InferenceContext ctx) {
    var name = call.getName();
    if (name == null) {
      return TypeSet.EMPTY;
    }
    var token = name.getSymbol();
    var position = new Position(token.getLine() - 1, token.getCharPositionInLine() + 1);
    return referenceResolver.findReference(ctx.documentContext.getUri(), position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .map(symbolTypeIndex::getDeclaredReturnTypes)
      .orElse(TypeSet.EMPTY);
  }

  // ---------------------------------------------------------------------------
  // Binary, unary, ternary
  // ---------------------------------------------------------------------------

  private TypeSet inferBinary(BinaryOperationNode node, InferenceContext ctx) {
    var op = node.getOperator();
    if (op == BslOperator.DEREFERENCE) {
      return inferDereference(node, ctx);
    }
    if (isLogical(op) || isComparison(op)) {
      return TypeSet.of(BOOLEAN);
    }
    if (op == BslOperator.ADD) {
      var left = inferInternal(node.getLeft(), ctx);
      var right = inferInternal(node.getRight(), ctx);
      if (left.refs().contains(STRING) || right.refs().contains(STRING)) {
        return TypeSet.of(STRING);
      }
      if (left.refs().contains(DATE) || right.refs().contains(DATE)) {
        return TypeSet.of(DATE);
      }
      return TypeSet.of(NUMBER);
    }
    if (op == BslOperator.SUBTRACT || op == BslOperator.MULTIPLY
      || op == BslOperator.DIVIDE || op == BslOperator.MODULO) {
      return TypeSet.of(NUMBER);
    }
    return TypeSet.EMPTY;
  }

  private TypeSet inferDereference(BinaryOperationNode node, InferenceContext ctx) {
    var leftTypes = inferInternal(node.getLeft(), ctx);
    if (leftTypes.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var right = node.getRight();
    String memberName;
    com.github._1c_syntax.bsl.languageserver.types.model.MemberKind expectedKind;
    if (right instanceof MethodCallNode methodCall) {
      var nameNode = methodCall.getName();
      memberName = nameNode == null ? null : nameNode.getText();
      expectedKind = com.github._1c_syntax.bsl.languageserver.types.model.MemberKind.METHOD;
    } else {
      var rightAst = right == null ? null : right.getRepresentingAst();
      memberName = memberNameOf(rightAst);
      expectedKind = com.github._1c_syntax.bsl.languageserver.types.model.MemberKind.PROPERTY;
    }
    if (memberName == null || memberName.isBlank()) {
      return TypeSet.EMPTY;
    }
    Set<TypeRef> result = new LinkedHashSet<>();
    for (var leftType : leftTypes.refs()) {
      for (var member : typeRegistry.getMembers(leftType, ctx.documentContext.getFileType())) {
        if (member.kind() != expectedKind) {
          continue;
        }
        if (!member.name().equalsIgnoreCase(memberName)) {
          continue;
        }
        if (member.returnType() != null && member.returnType().kind() != TypeKind.UNKNOWN) {
          result.add(member.returnType());
        }
      }
    }
    return result.isEmpty() ? TypeSet.EMPTY : TypeSet.of(result);
  }

  private static String memberNameOf(ParseTree ast) {
    if (ast == null) {
      return null;
    }
    if (ast instanceof TerminalNode terminal) {
      return terminal.getText();
    }
    return ast.getText();
  }

  @SuppressWarnings("unused")
  private TypeSet inferUnary(UnaryOperationNode node) {
    var op = node.getOperator();
    if (op == BslOperator.NOT) {
      return TypeSet.of(BOOLEAN);
    }
    return TypeSet.of(NUMBER);
  }

  private TypeSet inferTernary(TernaryOperatorNode node, InferenceContext ctx) {
    var truthy = inferInternal(node.getTruePart(), ctx);
    var falsy = inferInternal(node.getFalsePart(), ctx);
    return truthy.union(falsy);
  }

  // ---------------------------------------------------------------------------
  // Reference resolution
  // ---------------------------------------------------------------------------

  private TypeSet resolveReferenceAt(InferenceContext ctx, Position position) {
    return referenceResolver.findReference(ctx.documentContext.getUri(), position)
      .map(reference -> resolveReference(reference, ctx))
      .orElse(TypeSet.EMPTY);
  }

  private TypeSet resolveReference(Reference reference, InferenceContext ctx) {
    var maybeSymbol = reference.getSourceDefinedSymbol();
    if (maybeSymbol.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var symbol = maybeSymbol.get();
    if (!ctx.visited.add(symbol)) {
      return TypeSet.EMPTY;
    }
    try {
      if (symbol instanceof MethodSymbol method) {
        return symbolTypeIndex.getDeclaredReturnTypes(method);
      }
      if (symbol instanceof VariableSymbol variable) {
        return inferVariable(variable, ctx);
      }
      return TypeSet.EMPTY;
    } finally {
      ctx.visited.remove(symbol);
    }
  }

  /**
   * Тип переменной = union по позиции её декларации + всем DEFINITION-обращениям
   * из {@code ReferenceIndex}. Декларация нужна, т.к. {@code ReferenceIndexFiller}
   * фильтрует first-assignment (initialization) — она содержится в самом
   * {@link VariableSymbol#getSelectionRange()}.
   */
  private TypeSet inferVariable(VariableSymbol variable, InferenceContext ctx) {
    var owner = variable.getOwner();
    Set<TypeRef> result = new LinkedHashSet<>();
    Set<Position> visitedPositions = new HashSet<>();

    var declarationStart = variable.getSelectionRange().getStart();
    if (visitedPositions.add(declarationStart)) {
      inferFromDefinitionPosition(owner, declarationStart, ctx, result);
    }
    for (var occurrence : referenceIndex.getReferencesTo(variable)) {
      if (occurrence.occurrenceType() != OccurrenceType.DEFINITION) {
        continue;
      }
      var start = occurrence.selectionRange().getStart();
      if (visitedPositions.add(start)) {
        inferFromDefinitionPosition(owner, start, ctx, result);
      }
    }
    return result.isEmpty() ? TypeSet.EMPTY : TypeSet.of(result);
  }

  private void inferFromDefinitionPosition(
    DocumentContext owner,
    Position position,
    InferenceContext ctx,
    Collection<TypeRef> sink
  ) {
    ExpressionAtPosition.findAssignmentRhs(owner, position)
      .map(com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor::buildExpressionTree)
      .ifPresent(expr -> sink.addAll(inferInternal(expr, ctx).refs()));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static boolean isComparison(BslOperator op) {
    return op == BslOperator.EQUAL || op == BslOperator.NOT_EQUAL
      || op == BslOperator.LESS || op == BslOperator.LESS_OR_EQUAL
      || op == BslOperator.GREATER || op == BslOperator.GREATER_OR_EQUAL;
  }

  private static boolean isLogical(BslOperator op) {
    return op == BslOperator.AND || op == BslOperator.OR || op == BslOperator.NOT;
  }

  /**
   * Контекст одной операции infer: стек посещённых символов для защиты от циклов
   * и глубина рекурсии.
   */
  static final class InferenceContext {
    final DocumentContext documentContext;
    final Set<SourceDefinedSymbol> visited = new HashSet<>();
    int depth;

    InferenceContext(DocumentContext documentContext) {
      this.documentContext = documentContext;
    }
  }
}
