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
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Сапплаер семантических токенов для обращения к свойствам платформенных типов
 * через accessProperty (т.е. обращений вида {@code receiver.property} без скобок,
 * где {@code receiver} — типизированное выражение, чей тип резолвится через
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}).
 * <p>
 * Свойство резолвится через {@link TypeService#memberAt(DocumentContext, Position)}.
 * Имя свойства получает {@link SemanticTokenTypes#Property} +
 * {@link org.eclipse.lsp4j.SemanticTokenModifiers#DefaultLibrary}.
 * <p>
 * Симметричен {@link PlatformMemberMethodCallSemanticTokensSupplier} (вызовы методов
 * через accessCall) — общий каркас в {@link AbstractPlatformMemberSemanticTokensSupplier}.
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
public class PlatformMemberPropertyAccessSemanticTokensSupplier
  extends AbstractPlatformMemberSemanticTokensSupplier<BSLParser.AccessPropertyContext> {

  private static final Set<Integer> CHAIN_ROOTS =
    Set.of(BSLParser.RULE_complexIdentifier, BSLParser.RULE_callStatement);

  private final GlobalScopeProvider globalScopeProvider;

  public PlatformMemberPropertyAccessSemanticTokensSupplier(TypeService typeService,
                                                            GlobalScopeProvider globalScopeProvider,
                                                            SemanticTokensHelper helper) {
    super(typeService, helper);
    this.globalScopeProvider = globalScopeProvider;
  }

  @Override
  protected int ruleIndex() {
    return BSLParser.RULE_accessProperty;
  }

  @Override
  protected Optional<Range> nameRange(BSLParser.AccessPropertyContext node) {
    return Optional.ofNullable(node.IDENTIFIER()).map(Ranges::create);
  }

  @Override
  protected BiPredicate<BSLParser.AccessPropertyContext, Range> skipFilter(DocumentContext documentContext) {
    var fileType = documentContext.getFileType();
    return (node, range) -> isGlobalScopeChain(node, fileType);
  }

  @Override
  protected void emit(List<SemanticTokenEntry> entries, DocumentContext documentContext, Range range) {
    // Для accessProperty memberAt возвращает только свойство (метод — только для
    // accessCall). Модификатор DefaultLibrary вешаем лишь на члены стандартной
    // библиотеки/платформы (свойства платформенных типов, стандартные реквизиты);
    // собственные и общие реквизиты конфигурации — просто Property.
    typeService.memberAt(documentContext, range.getStart())
      .map(TypeService.TypedMember::descriptor)
      .ifPresent(descriptor -> {
        if (descriptor.standardLibrary()) {
          helper.addRange(entries, range, SemanticTokenTypes.Property, DEFAULT_LIBRARY_MODIFIERS);
        } else {
          helper.addRange(entries, range, SemanticTokenTypes.Property);
        }
      });
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
}
