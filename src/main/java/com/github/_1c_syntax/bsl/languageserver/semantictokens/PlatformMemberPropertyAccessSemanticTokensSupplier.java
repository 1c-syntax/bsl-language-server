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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сапплаер семантических токенов для обращения к свойствам платформенных типов
 * через accessProperty (т.е. обращений вида {@code receiver.property} без скобок,
 * где {@code receiver} — типизированное выражение, чей тип резолвится через
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}).
 * <p>
 * Свойство резолвится через {@link TypeService#memberAt(DocumentContext, Position)}.
 * Если найден member с {@link MemberKind#PROPERTY} — имя свойства получает
 * {@link SemanticTokenTypes#Property} + {@link SemanticTokenModifiers#DefaultLibrary}.
 * <p>
 * Симметричен {@link PlatformMemberMethodCallSemanticTokensSupplier}, который
 * подсвечивает вызовы методов платформенных типов через accessCall. В отличие от
 * методного сапплаера здесь не нужен skip source-defined call-site'ов: source-defined
 * переменные (в т.ч. экспортные переменные модуля объекта) не выставляются как
 * члены типа, поэтому {@link TypeService#memberAt} их не находит и пересечения
 * по позиции с {@link SymbolsSemanticTokensSupplier} не возникает.
 * <p>
 * Цепочки, начинающиеся с глобального synthetic-имени ({@code Справочники.Контрагенты},
 * {@code Перечисления.Пол.Мужской}, {@code КодировкаТекста.UTF8}), целиком красит
 * {@link GlobalScopeSemanticTokensSupplier} (метаобъекты → {@code Class}, значения
 * перечислений → {@code EnumMember}). Такие цепочки пропускаются по базовому
 * идентификатору — это ровно домен GlobalScope, и только там возникал конфликт
 * {@code Property} vs {@code Class}/{@code EnumMember}. Обращения к свойствам
 * локально-типизированных переменных ({@code Объект.Ссылка}, {@code Строка.Родитель})
 * GlobalScope не трогает — их подсвечиваем мы.
 */
@Component
@RequiredArgsConstructor
public class PlatformMemberPropertyAccessSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};
  private static final Set<Integer> CHAIN_ROOTS =
    Set.of(BSLParser.RULE_complexIdentifier, BSLParser.RULE_callStatement);

  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    var entries = new ArrayList<SemanticTokenEntry>();
    var ast = documentContext.getAst();
    var fileType = documentContext.getFileType();

    for (var accessProperty
      : Trees.<BSLParser.AccessPropertyContext>findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      if (isGlobalScopeChain(accessProperty, fileType)) {
        continue;
      }
      propertyNameRange(accessProperty)
        .filter(range -> resolvesToMember(documentContext, range.getStart()))
        .ifPresent(range -> helper.addRange(
          entries, range, SemanticTokenTypes.Property, DEFAULT_LIBRARY_MODIFIERS));
    }

    return entries;
  }

  private static Optional<Range> propertyNameRange(BSLParser.AccessPropertyContext accessProperty) {
    return Optional.ofNullable(accessProperty.IDENTIFIER())
      .map(Ranges::create);
  }

  /**
   * Цепочка обращения для {@code accessProperty} начинается с глобального
   * synthetic-имени (менеджер метаданных, глобальное перечисление, library-модуль),
   * т.е. целиком относится к домену {@link GlobalScopeSemanticTokensSupplier}.
   */
  private boolean isGlobalScopeChain(BSLParser.AccessPropertyContext accessProperty, FileType fileType) {
    return chainBaseName(accessProperty)
      .flatMap(name -> globalScopeProvider.findGlobalEntry(name, fileType))
      .map(entry -> entry.role() == GlobalSymbolScope.Role.VALUE)
      .orElse(false);
  }

  /**
   * Имя базового идентификатора цепочки, в которой находится {@code accessProperty}
   * (например, {@code Справочники} для {@code Справочники.Контрагенты.Ссылка}).
   * Пусто, если цепочка начинается не с идентификатора (конструктор {@code Новый ...} и т.п.).
   */
  private static Optional<String> chainBaseName(BSLParser.AccessPropertyContext accessProperty) {
    var root = Trees.getRootParent(accessProperty, CHAIN_ROOTS);
    TerminalNode base = null;
    if (root instanceof BSLParser.ComplexIdentifierContext complexId) {
      base = complexId.IDENTIFIER();
    } else if (root instanceof BSLParser.CallStatementContext callStatement) {
      base = callStatement.IDENTIFIER();
    }
    return Optional.ofNullable(base).map(TerminalNode::getText);
  }

  /**
   * В позиции резолвится член типа. Для accessProperty это всегда свойство
   * (метод резолвится только для accessCall), поэтому отдельная проверка
   * {@link com.github._1c_syntax.bsl.languageserver.types.model.MemberKind} не нужна.
   */
  private boolean resolvesToMember(DocumentContext documentContext, Position position) {
    return typeService.memberAt(documentContext, position).isPresent();
  }
}
