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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TerminalSymbolNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Единая точка для consumer'ов (hover, completion, signature help) для
 * получения информации о типах. Делегирует {@link SymbolTypeIndex}/
 * {@link ExpressionTypeInferencer}/{@link TypeRegistry}.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class TypeService {

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;
  private final ExpressionTypeInferencer inferencer;
  private final ReferenceResolver referenceResolver;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Получить набор типов на позиции (точка входа для hover/completion).
   */
  public TypeSet findTypes(URI uri, Position position) {
    return referenceResolver.findReference(uri, position)
      .map(this::findTypes)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Получить набор типов для конкретной {@link Reference}.
   */
  public TypeSet findTypes(Reference reference) {
    return reference.getSourceDefinedSymbol()
      .map(this::findTypes)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Получить набор типов для символа.
   */
  public TypeSet findTypes(SourceDefinedSymbol symbol) {
    return inferencer.inferSymbol(symbol);
  }

  /**
   * Получить типы выражения, начинающегося в указанной позиции.
   * Используется hover'ом/completion'ом для произвольного выражения,
   * не привязанного к именованному символу.
   */
  public TypeSet inferAtPosition(DocumentContext documentContext, Position position) {
    typeRegistry.resolve("");
    return ExpressionAtPosition.findExpressionContext(documentContext, position)
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree)
      .map(expr -> inferencer.infer(expr, documentContext))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Список типов параметров метода — для signature help.
   */
  public List<TypeSet> getParameterTypes(MethodSymbol method) {
    return method.getParameters().stream()
      .map(symbolTypeIndex::getDeclaredParameterTypes)
      .toList();
  }

  /**
   * Члены типа (методы + свойства) — для completion на точке.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef typeRef) {
    return typeRegistry.getMembers(typeRef);
  }

  /**
   * То же, что {@link #getMembers(TypeRef)}, но фильтрует члены по языковому скоупу
   * источника. Источники, не совместимые с {@code fileType}, пропускаются.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef typeRef, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    return typeRegistry.getMembers(typeRef, fileType);
  }

  /**
   * Описание типа (текст для hover); пустая строка, если описание отсутствует.
   */
  public String getDescription(TypeRef typeRef) {
    return typeRegistry.getDescription(typeRef);
  }

  /**
   * Сигнатуры конструкторов типа (для платформенных классов из JSON-пакета).
   * Пустой список, если конструкторов нет.
   */
  public java.util.List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> getConstructors(TypeRef typeRef) {
    return typeRegistry.getConstructors(typeRef);
  }

  /**
   * Резолв типа по имени (включая Ru/En алиасы и qualifiedName).
   */
  public Optional<TypeRef> resolve(String name) {
    return typeRegistry.resolve(name);
  }

  /**
   * Резолв типа по имени с учётом языкового скоупа.
   */
  public Optional<TypeRef> resolve(String name, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    return typeRegistry.resolve(name, fileType);
  }

  /**
   * Найти тип глобального свойства (например, system enum {@code КодировкаТекста}, {@code ФС}) по имени.
   */
  public Optional<TypeRef> findGlobalPropertyType(String name) {
    return findGlobalPropertyType(name, null);
  }

  /**
   * То же, что {@link #findGlobalPropertyType(String)}, но с фильтрацией по типу файла.
   */
  public Optional<TypeRef> findGlobalPropertyType(String name, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    // Триггерим bootstrap TypeRegistry в текущем workspace scope, чтобы system enum'ы
    // и прочие глобальные свойства из платформенных провайдеров были зарегистрированы.
    typeRegistry.resolve(name);
    return globalScopeProvider.findGlobalPropertyType(name, fileType);
  }

  /**
   * @return имена зарегистрированных глобальных свойств (для completion).
   */
  public Collection<String> getGlobalPropertyNames() {
    typeRegistry.resolve("");
    return globalScopeProvider.getGlobalPropertyNames();
  }

  /**
   * То же, что {@link #getGlobalPropertyNames()}, но с фильтрацией по типу файла.
   */
  public Collection<String> getGlobalPropertyNames(com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    typeRegistry.resolve("");
    return globalScopeProvider.getGlobalPropertyNames(fileType);
  }

  /**
   * Найти член типа в позиции курсора (для hover/go-to-member по
   * выражениям без source-defined символа: цепочки accessor'ов,
   * платформенные типы, library-модули).
   *
   * @return описание найденного члена + тип-владелец и диапазон под курсором.
   */
  public Optional<TypedMember> findMemberAt(DocumentContext documentContext, Position position) {
    BSLParser.FileContext ast;
    try {
      ast = documentContext.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }
    if (ast == null) {
      return Optional.empty();
    }
    var terminalOpt = Trees.findTerminalNodeContainsPosition(ast, position);
    if (terminalOpt.isEmpty()) {
      return Optional.empty();
    }
    var terminal = terminalOpt.get();
    if (terminal.getSymbol().getType() != BSLParser.IDENTIFIER) {
      return Optional.empty();
    }

    // Случай глобального свойства или library-модуля (например, КодировкаТекста, ФС).
    var bareName = terminal.getText();
    if (!isAccessorIdentifier(terminal)) {
      // Триггерим bootstrap TypeRegistry в текущем workspace scope, чтобы
      // exposedAsGlobal-типы (system enums и пр.) попали в GlobalSymbolScope.
      typeRegistry.resolve(bareName);
      var fromScope = globalScopeProvider.findGlobal(bareName, documentContext.getFileType())
        .filter(SyntheticSymbol.class::isInstance)
        .map(SyntheticSymbol.class::cast)
        .filter(s -> !s.getValueType().equals(TypeRef.UNKNOWN));
      if (fromScope.isPresent()) {
        var sym = fromScope.get();
        var ref = sym.getValueType();
        var desc = sym.getDescription();
        if (desc == null || desc.isBlank()) {
          desc = typeRegistry.getDescription(ref);
        }
        return Optional.of(new TypedMember(ref,
          MemberDescriptor.property(ref.qualifiedName(), ref, desc),
          Ranges.create(terminal)));
      }
    }

    var expressionCtx = ExpressionAtPosition.findExpressionContext(documentContext, position);
    if (expressionCtx.isEmpty()) {
      return Optional.empty();
    }
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(expressionCtx.get());
    var dereference = findDereferenceForTerminal(expression, terminal);
    if (dereference == null) {
      return Optional.empty();
    }

    var right = dereference.getRight();
    MemberKind expectedKind;
    if (right instanceof MethodCallNode) {
      expectedKind = MemberKind.METHOD;
    } else {
      expectedKind = MemberKind.PROPERTY;
    }
    var memberName = terminal.getText();
    var leftTypes = inferencer.infer(dereference.getLeft(), documentContext);
    if (leftTypes.isEmpty()) {
      return Optional.empty();
    }
    for (var owner : leftTypes.refs()) {
      for (var member : typeRegistry.getMembers(owner, documentContext.getFileType())) {
        if (member.kind() == expectedKind && member.name().equalsIgnoreCase(memberName)) {
          return Optional.of(new TypedMember(owner, member, Ranges.create(terminal)));
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isAccessorIdentifier(TerminalNode terminal) {
    var parent = terminal.getParent();
    if (parent instanceof BSLParser.AccessPropertyContext) {
      return true;
    }
    if (parent instanceof BSLParser.MethodNameContext
      && parent.getParent() instanceof BSLParser.MethodCallContext mc
      && mc.getParent() instanceof BSLParser.AccessCallContext) {
      return true;
    }
    return false;
  }

  private static BinaryOperationNode findDereferenceForTerminal(BslExpression root, TerminalNode terminal) {
    if (root instanceof BinaryOperationNode binary
      && binary.getOperator() == BslOperator.DEREFERENCE
      && rightMatchesTerminal(binary.getRight(), terminal)) {
      return binary;
    }
    if (root instanceof BinaryOperationNode binary) {
      var leftHit = findDereferenceForTerminal(binary.getLeft(), terminal);
      if (leftHit != null) {
        return leftHit;
      }
      return findDereferenceForTerminal(binary.getRight(), terminal);
    }
    if (root instanceof MethodCallNode call) {
      for (var arg : call.arguments()) {
        var hit = findDereferenceForTerminal(arg, terminal);
        if (hit != null) {
          return hit;
        }
      }
    }
    return null;
  }

  private static boolean rightMatchesTerminal(BslExpression right, TerminalNode terminal) {
    if (right instanceof TerminalSymbolNode terminalNode
      && terminalNode.getNodeType() == ExpressionNodeType.IDENTIFIER) {
      var ast = terminalNode.getRepresentingAst();
      return ast == terminal;
    }
    if (right instanceof MethodCallNode call) {
      return call.getName() == terminal;
    }
    return false;
  }

  /**
   * Найденный член типа в позиции курсора.
   *
   * @param owner       тип, которому принадлежит член
   * @param descriptor  описание члена
   * @param range       диапазон идентификатора-члена под курсором
   */
  public record TypedMember(TypeRef owner, MemberDescriptor descriptor, Range range) {
  }

}
