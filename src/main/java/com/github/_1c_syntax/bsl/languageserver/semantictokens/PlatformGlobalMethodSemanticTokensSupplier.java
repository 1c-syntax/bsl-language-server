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
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер семантических токенов для вызовов глобальных функций платформы
 * (например, {@code Сообщить}, {@code СтрНайти}). Метод считается платформенным,
 * если резолвится как глобальная функция через {@link GlobalScopeProvider#globalFunction}
 * и не перекрыт локальным методом в текущем модуле — в таком случае
 * приоритет у локального символа, отрисуется через
 * {@link MethodCallSemanticTokensSupplier} как обычный {@code Method}.
 * <p>
 * Платформенные глобалы помечаются модификатором {@link SemanticTokenModifiers#DefaultLibrary} —
 * это позволяет UI-теме отличать их от пользовательских вызовов
 * (типично — приглушённый цвет «stdlib»).
 */
@Component
@RequiredArgsConstructor
public class PlatformGlobalMethodSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};
  private static final String[] DEFAULT_LIBRARY_ASYNC_MODIFIERS =
    {SemanticTokenModifiers.DefaultLibrary, SemanticTokenModifiers.Async};

  private final GlobalScopeProvider globalScopeProvider;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var ast = documentContext.getAst();
    var fileType = documentContext.getFileType();
    var symbolTree = documentContext.getSymbolTree();

    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_globalMethodCall)) {
      if (!(node instanceof BSLParser.GlobalMethodCallContext call)) {
        continue;
      }
      var methodNameCtx = call.methodName();
      if (methodNameCtx == null) {
        continue;
      }
      var name = methodNameCtx.getStart().getText();
      if (name.isBlank()) {
        continue;
      }
      // Локальное определение в модуле имеет приоритет: подсветка как обычного Method
      // выполнится через MethodCallSemanticTokensSupplier (по ReferenceIndex).
      if (symbolTree != null && symbolTree.getMethodSymbol(name).isPresent()) {
        continue;
      }
      globalScopeProvider.globalFunction(name, fileType)
        .ifPresent(function ->
          helper.addRange(
            entries,
            Ranges.create(methodNameCtx),
            SemanticTokenTypes.Function,
            modifiers(function.async())
          )
        );
    }

    return entries;
  }

  private static String[] modifiers(boolean async) {
    return async ? DEFAULT_LIBRARY_ASYNC_MODIFIERS : DEFAULT_LIBRARY_MODIFIERS;
  }
}
