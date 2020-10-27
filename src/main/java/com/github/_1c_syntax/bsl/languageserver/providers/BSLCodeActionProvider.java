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

import com.github._1c_syntax.bsl.languageserver.codeactions.CodeActionSupplier;
import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.ls_core.context.DocumentContext;
import com.github._1c_syntax.ls_core.providers.CodeActionProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Primary
public final class BSLCodeActionProvider implements CodeActionProvider {

  private final List<CodeActionSupplier> codeActionSuppliers;

  @Override
  public List<Either<Command, CodeAction>> getCodeActions(
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    return codeActionSuppliers.stream()
      .flatMap(codeActionSupplier -> codeActionSupplier
        .getCodeActions(params, (BSLDocumentContext) documentContext).stream())
      .map(Either::<Command, CodeAction>forRight)
      .collect(Collectors.toList());
  }

}
