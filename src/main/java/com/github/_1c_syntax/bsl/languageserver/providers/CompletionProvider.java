/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompletionProvider {
  public static Either<List<CompletionItem>, CompletionList> getCompletion(
    DocumentContext documentContext,
    CompletionParams params) {

    var items = new ArrayList<CompletionItem>();

    documentContext.getMethods().stream()
      .map((MethodSymbol methodSymbol) -> {
        var item =  new CompletionItem();
        item.setLabel(methodSymbol.getName());
        item.setKind(CompletionItemKind.Method);

        methodSymbol.getDescription()
          .map(methodDescription -> new MarkupContent(MarkupKind.MARKDOWN, methodDescription.getDescription()))
          .ifPresent(item::setDocumentation);

        return item;
      })
      .collect(Collectors.toCollection(() -> items));

    return Either.forRight(new CompletionList(items));

  }
}
