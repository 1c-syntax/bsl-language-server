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
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сапплаер семантических токенов для вызовов методов платформенных типов через
 * accessCall (т.е. вызовов вида {@code receiver.method(...)}, где {@code receiver}
 * — типизированное выражение, чей тип резолвится через
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}).
 * <p>
 * Метод резолвится через {@link TypeService#findMemberAt(DocumentContext, Position)}.
 * Если найден member с {@link MemberKind#METHOD} — имя метода получает
 * {@link SemanticTokenTypes#Method} + {@link SemanticTokenModifiers#DefaultLibrary},
 * а для async-методов платформы (флаг {@link MemberDescriptor#async()}) добавляется
 * ещё и {@link SemanticTokenModifiers#Async}.
 * <p>
 * Source-defined вызовы (методы общих модулей, локальные методы, OScript-library)
 * подсвечиваются через {@link MethodCallSemanticTokensSupplier} по ReferenceIndex —
 * этот сапплаер их пропускает, чтобы не дублировать токены.
 */
@Component
@RequiredArgsConstructor
public class PlatformMemberMethodCallSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};
  private static final String[] DEFAULT_LIBRARY_ASYNC_MODIFIERS = {
    SemanticTokenModifiers.DefaultLibrary,
    SemanticTokenModifiers.Async
  };

  private final TypeService typeService;
  private final ReferenceIndex referenceIndex;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    var entries = new ArrayList<SemanticTokenEntry>();
    var ast = documentContext.getAst();

    // Позиции, уже обработанные MethodCallSemanticTokensSupplier: пропускаем,
    // чтобы не дублировать токен на одной и той же позиции.
    var sourceDefinedCallSites = collectSourceDefinedCallSites(documentContext);

    for (var accessCall : Trees.<BSLParser.AccessCallContext>findAllRuleNodes(ast, BSLParser.RULE_accessCall)) {
      methodNameRange(accessCall)
        .filter(range -> !sourceDefinedCallSites.contains(range.getStart()))
        .flatMap(range -> platformMethodAt(documentContext, range.getStart())
          .map(descriptor -> new Resolved(range, descriptor)))
        .ifPresent(resolved -> helper.addRange(
          entries, resolved.range(), SemanticTokenTypes.Method, modifiers(resolved.descriptor())));
    }

    return entries;
  }

  private static Optional<Range> methodNameRange(BSLParser.AccessCallContext accessCall) {
    return Optional.ofNullable(accessCall.methodCall())
      .map(BSLParser.MethodCallContext::methodName)
      .map(BSLParser.MethodNameContext::IDENTIFIER)
      .map(Ranges::create);
  }

  private Optional<MemberDescriptor> platformMethodAt(DocumentContext documentContext, Position position) {
    return typeService.findMemberAt(documentContext, position)
      .map(TypeService.TypedMember::descriptor)
      .filter(descriptor -> descriptor.kind() == MemberKind.METHOD);
  }

  static String[] modifiers(MemberDescriptor descriptor) {
    return descriptor.async() ? DEFAULT_LIBRARY_ASYNC_MODIFIERS : DEFAULT_LIBRARY_MODIFIERS;
  }

  private record Resolved(Range range, MemberDescriptor descriptor) {
  }

  private Set<Position> collectSourceDefinedCallSites(DocumentContext documentContext) {
    var positions = new HashSet<Position>();
    for (var reference : referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method)) {
      positions.add(reference.selectionRange().getStart());
    }
    return positions;
  }
}
