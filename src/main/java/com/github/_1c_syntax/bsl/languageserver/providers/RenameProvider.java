/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public final class RenameProvider {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  public WorkspaceEdit getRename(RenameParams params, DocumentContext documentContext) {

    Position position = params.getPosition();

    var reference = referenceResolver.findReference(
      documentContext.getUri(), params.getPosition()
    );

    return new WorkspaceEdit(
      Stream.concat(
          referenceResolver.findReference(documentContext.getUri(), position)
            .flatMap(Reference::getSourceDefinedSymbol)
            .stream()
            .map(referenceIndex::getReferencesTo)
            .flatMap(Collection::stream),
          reference.stream())
        .collect(Collectors.groupingBy(
          ref -> ref.getUri().toString(),
          Collectors.mapping(Reference::getSelectionRange,
            Collectors.mapping(range -> new TextEdit(range, params.getNewName()), Collectors.toList())))));
  }

  public Range getPrepareRename(
    PrepareRenameParams params,
    DocumentContext documentContext) {

    return referenceResolver.findReference(
        documentContext.getUri(), params.getPosition())
      .map(Reference::getSelectionRange)
      .orElse(null);
  }
}
