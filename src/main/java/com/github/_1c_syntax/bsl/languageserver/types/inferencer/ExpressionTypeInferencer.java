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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
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
    // ВАЖНО: имена классов (роль TYPE_NAME — например, голое `Структура`)
    // здесь пропускаются: имя класса само по себе не является инстансом
    // класса, и `Структура.` не должен показывать методы класса.
    var fromScope = globalScopeProvider.findGlobalEntry(text, ctx.documentContext.getFileType())
      .filter(entry -> entry.role() != GlobalSymbolScope.Role.TYPE_NAME)
      .map(entry -> {
        var symbol = entry.symbol();
        if (symbol instanceof SyntheticSymbol s) {
          return s.getValueType();
        }
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
    var base = typeRegistry.resolve(typeName, ctx.documentContext.getFileType())
      .map(TypeSet::of)
      .orElseGet(() -> TypeSet.of(typeRegistry.intern(TypeKind.USER, typeName)));
    if (isStructureLike(typeName)) {
      base = applyStructureConstructorKeys(base, constructor, ctx);
    }
    return base;
  }

  /**
   * Для записи {@code Новый Структура("К1, К2", v1, v2)}: распарсить первый
   * строковый аргумент в имена ключей и подвесить к каждому ключу TypeSet
   * соответствующего value-аргумента через {@link TypeSet#withField(TypeRef, String, TypeSet)}.
   */
  private TypeSet applyStructureConstructorKeys(
    TypeSet base,
    ConstructorCallNode constructor,
    InferenceContext ctx
  ) {
    var args = constructor.arguments();
    if (args.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var keyLiteral = extractStringLiteral(args.get(0));
    if (keyLiteral == null) {
      return base;
    }
    var keys = keyLiteral.split(",");
    var headRef = base.refs().iterator().next();
    var result = base;
    for (int i = 0; i < keys.length; i++) {
      var keyName = keys[i].trim();
      if (keyName.isEmpty()) {
        continue;
      }
      int valueArgIndex = i + 1;
      TypeSet valueTypes;
      if (valueArgIndex < args.size()) {
        valueTypes = inferInternal(args.get(valueArgIndex), ctx);
      } else {
        valueTypes = TypeSet.of(UNDEFINED);
      }
      if (!valueTypes.isEmpty()) {
        result = result.withField(headRef, keyName, valueTypes);
      }
    }
    return result;
  }

  private static String extractStringLiteral(BslExpression node) {
    if (node == null) {
      return null;
    }
    var ast = node.getRepresentingAst();
    if (ast == null) {
      return null;
    }
    var text = ast.getText();
    if (text == null) {
      return null;
    }
    var trimmed = text.trim();
    if (trimmed.length() >= 2
      && (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'')
      && trimmed.charAt(0) == trimmed.charAt(trimmed.length() - 1)) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return null;
  }

  private static boolean isStructureLike(String typeName) {
    if (typeName == null) {
      return false;
    }
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("структура") || lower.equals("structure")
      || lower.equals("фиксированнаяструктура") || lower.equals("fixedstructure");
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
    MemberKind expectedKind;
    if (right instanceof MethodCallNode methodCall) {
      var nameNode = methodCall.getName();
      memberName = nameNode == null ? null : nameNode.getText();
      expectedKind = MemberKind.METHOD;
    } else {
      var rightAst = right == null ? null : right.getRepresentingAst();
      memberName = memberNameOf(rightAst);
      expectedKind = MemberKind.PROPERTY;
    }
    if (memberName == null || memberName.isBlank()) {
      return TypeSet.EMPTY;
    }
    // Сначала смотрим декларированные поля «открытого» объекта данных
    // (Структура / ТаблицаЗначений с описанными ключами).
    TypeSet fromLocalFields = TypeSet.EMPTY;
    if (expectedKind == MemberKind.PROPERTY) {
      for (var leftType : leftTypes.refs()) {
        var fields = leftTypes.getLocalFields(leftType);
        for (var entry : fields.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(memberName)) {
            fromLocalFields = fromLocalFields.union(entry.getValue());
          }
        }
      }
      if (!fromLocalFields.isEmpty()) {
        return fromLocalFields;
      }
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
   * <p>
   * Для параметра метода — добавляются типы, объявленные в JsDoc
   * (секция {@code // Параметры:}).
   */
  private TypeSet inferVariable(VariableSymbol variable, InferenceContext ctx) {
    var owner = variable.getOwner();
    TypeSet acc = TypeSet.EMPTY;
    Set<Position> visitedPositions = new HashSet<>();

    if (variable.getKind() == VariableKind.PARAMETER) {
      acc = acc.union(declaredParameterTypes(variable));
    }

    acc = acc.union(typesFromVariableTrailingComment(variable));

    var declarationStart = variable.getSelectionRange().getStart();
    if (visitedPositions.add(declarationStart)) {
      acc = acc.union(inferFromDefinitionPosition(owner, declarationStart, ctx));
    }
    for (var occurrence : referenceIndex.getReferencesTo(variable)) {
      if (occurrence.occurrenceType() != OccurrenceType.DEFINITION) {
        continue;
      }
      var start = occurrence.selectionRange().getStart();
      if (visitedPositions.add(start)) {
        acc = acc.union(inferFromDefinitionPosition(owner, start, ctx));
      }
    }
    return acc;
  }

  /**
   * Извлечь типы из висячего комментария декларации переменной:
   * {@code Перем X; // Тип -}. Источник — {@code VariableDescription.trailingDescription},
   * который парсер уже привязал к декларации.
   */
  private TypeSet typesFromVariableTrailingComment(VariableSymbol variable) {
    var description = variable.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var trailing = description.getTrailingDescription().orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }
    var names = InlineTypeCommentParser.parseTypeNames(trailing.getDescription());
    if (names.isEmpty()) {
      return TypeSet.EMPTY;
    }
    Set<TypeRef> refs = new LinkedHashSet<>();
    var fileType = variable.getOwner().getFileType();
    for (var name : names) {
      typeRegistry.resolve(name, fileType).ifPresent(refs::add);
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  /**
   * Найти {@link ParameterDefinition} в скоупе-методе по имени переменной и
   * вернуть его декларированные типы из JsDoc.
   */
  private TypeSet declaredParameterTypes(VariableSymbol variable) {
    var scope = variable.getScope();
    if (!(scope instanceof MethodSymbol method)) {
      return TypeSet.EMPTY;
    }
    var name = variable.getName();
    for (ParameterDefinition parameter : method.getParameters()) {
      if (parameter.getName().equalsIgnoreCase(name)) {
        return symbolTypeIndex.getDeclaredParameterTypes(parameter);
      }
    }
    return TypeSet.EMPTY;
  }

  private TypeSet inferFromDefinitionPosition(
    DocumentContext owner,
    Position position,
    InferenceContext ctx
  ) {
    var assignment = ExpressionAtPosition.findAssignment(owner, position);
    TypeSet result = assignment.map(BSLParser.AssignmentContext::expression)
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree)
      .map(expr -> inferInternal(expr, ctx))
      .orElse(TypeSet.EMPTY);
    if (assignment.isPresent()) {
      result = result.union(inlineCommentTypes(owner, assignment.get()));
    }
    return result;
  }

  /**
   * Подхватить типы из висячего комментария в строке присваивания:
   * {@code X = F(); // Тип -}. Соответствует «inline-typing локальной
   * переменной» из стандарта 1С:EDT.
   */
  private TypeSet inlineCommentTypes(
    DocumentContext owner,
    BSLParser.AssignmentContext assignment
  ) {
    var stop = assignment.getStop();
    if (stop == null) {
      return TypeSet.EMPTY;
    }
    var tokens = owner.getTokens();
    if (tokens == null) {
      return TypeSet.EMPTY;
    }
    return Trees.getTrailingComment(tokens, stop).map(commentToken -> {
      Set<TypeRef> refs = new LinkedHashSet<>();
      for (var name : InlineTypeCommentParser.parseTypeNames(commentToken.getText())) {
        typeRegistry.resolve(name, owner.getFileType()).ifPresent(refs::add);
      }
      return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
    }).orElse(TypeSet.EMPTY);
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
