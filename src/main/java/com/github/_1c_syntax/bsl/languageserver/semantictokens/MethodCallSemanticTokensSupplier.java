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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.context.Modules;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер семантических токенов для вызовов методов.
 */
@Component
@RequiredArgsConstructor
public class MethodCallSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] NO_MODIFIERS = new String[0];
  private static final String[] ASYNC_MODIFIERS = {SemanticTokenModifiers.Async};
  private static final String[] STATIC_MODIFIERS = {SemanticTokenModifiers.Static};
  private static final String[] STATIC_ASYNC_MODIFIERS = {
    SemanticTokenModifiers.Static,
    SemanticTokenModifiers.Async
  };

  private final ReferenceIndex referenceIndex;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var uri = documentContext.getUri();

    for (var reference : referenceIndex.getReferencesFrom(uri, SymbolKind.Method)) {
      if (!reference.isSourceDefinedSymbolReference()) {
        continue;
      }

      reference.getSourceDefinedSymbol().ifPresent(symbol -> {
        if (!(symbol instanceof MethodSymbol method)) {
          helper.addRange(entries, reference.selectionRange(), SemanticTokenTypes.Method);
          return;
        }
        // Метод объявлен в «статическом» модуле (CommonModule/ManagerModule/OScript-модуль)?
        // Тогда подсвечиваем вызов как Method + Static. instance-методы
        // (ObjectModule, формы, OScript-классы) остаются без Static.
        // Вызовы async-методов получают модификатор Async — это касается и
        // statics, и instance-методов, поэтому Async может комбинироваться со Static.
        boolean isStatic = Modules.isStaticModule(method.getOwner());
        var modifiers = methodCallModifiers(isStatic, method.isAsync());
        helper.addRange(entries, reference.selectionRange(), SemanticTokenTypes.Method, modifiers);
      });
    }

    return entries;
  }

  private static String[] methodCallModifiers(boolean isStatic, boolean isAsync) {
    if (isStatic) {
      return isAsync ? STATIC_ASYNC_MODIFIERS : STATIC_MODIFIERS;
    }
    return isAsync ? ASYNC_MODIFIERS : NO_MODIFIERS;
  }
}

