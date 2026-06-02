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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * подсвечивает вызовы методов платформенных типов через accessCall. В отличие от
 * методного сапплаера здесь не нужен skip source-defined call-site'ов: source-defined
 * переменные (в т.ч. экспортные переменные модуля объекта) не выставляются как
 * члены типа, поэтому {@link TypeService#findMemberAt} их не находит и пересечения
 * по позиции с {@link SymbolsSemanticTokensSupplier} не возникает.
 */
@Component
@RequiredArgsConstructor
public class PlatformMemberPropertyAccessSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};

  private final TypeService typeService;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    var entries = new ArrayList<SemanticTokenEntry>();
    var ast = documentContext.getAst();

    for (var accessProperty
      : Trees.<BSLParser.AccessPropertyContext>findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      propertyNameRange(accessProperty)
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
      .filter(PlatformMemberPropertyAccessSemanticTokensSupplier::isProperty)
      .isPresent();
  }

  static boolean isProperty(MemberDescriptor descriptor) {
    return descriptor.kind() == MemberKind.PROPERTY;
  }
}
