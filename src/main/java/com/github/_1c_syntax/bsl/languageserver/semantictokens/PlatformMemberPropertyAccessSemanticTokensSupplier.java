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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сапплаер семантических токенов для обращения к свойствам платформенных типов
 * через accessProperty (т.е. обращений вида {@code receiver.property} без скобок,
 * где {@code receiver} — типизированное выражение, чей тип резолвится через
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}).
 * <p>
 * Свойство резолвится через {@link TypeService#findMemberAt(DocumentContext, Position)}.
 * Если найден member с {@link MemberKind#PROPERTY} — имя свойства получает
 * {@link SemanticTokenTypes#Property} + {@link SemanticTokenModifiers#DefaultLibrary}.
 * <p>
 * Симметричен {@link PlatformMemberMethodCallSemanticTokensSupplier}, который
 * подсвечивает вызовы методов платформенных типов через accessCall.
 * <p>
 * Source-defined переменные (в т.ч. экспортные переменные модуля объекта,
 * доступные как {@code Объект.Перем}) подсвечиваются через
 * {@link SymbolsSemanticTokensSupplier} по ReferenceIndex — этот сапплаер
 * пропускает такие позиции, чтобы не дублировать токены.
 */
@Component
@RequiredArgsConstructor
public class PlatformMemberPropertyAccessSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};

  private final TypeService typeService;
  private final ReferenceIndex referenceIndex;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    var entries = new ArrayList<SemanticTokenEntry>();
    var ast = documentContext.getAst();

    // Позиции, уже обработанные SymbolsSemanticTokensSupplier (source-defined
    // переменные): пропускаем, чтобы не дублировать токен на одной позиции.
    var sourceDefinedVarSites = collectSourceDefinedVarSites(documentContext);

    for (var accessProperty
      : Trees.<BSLParser.AccessPropertyContext>findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      propertyNameRange(accessProperty)
        .filter(range -> !sourceDefinedVarSites.contains(range.getStart()))
        .filter(range -> isPlatformProperty(documentContext, range.getStart()))
        .ifPresent(range -> helper.addRange(
          entries, range, SemanticTokenTypes.Property, DEFAULT_LIBRARY_MODIFIERS));
    }

    return entries;
  }

  private static Optional<Range> propertyNameRange(BSLParser.AccessPropertyContext accessProperty) {
    return Optional.ofNullable(accessProperty.IDENTIFIER())
      .map(Ranges::create);
  }

  private boolean isPlatformProperty(DocumentContext documentContext, Position position) {
    return typeService.findMemberAt(documentContext, position)
      .map(TypeService.TypedMember::descriptor)
      .filter(descriptor -> descriptor.kind() == MemberKind.PROPERTY)
      .isPresent();
  }

  private Set<Position> collectSourceDefinedVarSites(DocumentContext documentContext) {
    var positions = new HashSet<Position>();
    for (var reference : referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Variable)) {
      positions.add(reference.selectionRange().getStart());
    }
    return positions;
  }
}
