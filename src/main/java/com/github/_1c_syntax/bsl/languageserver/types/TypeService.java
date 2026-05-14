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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
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
   * Резолв типа по имени (включая Ru/En алиасы и qualifiedName).
   */
  public Optional<TypeRef> resolve(String name) {
    return typeRegistry.resolve(name);
  }

}
