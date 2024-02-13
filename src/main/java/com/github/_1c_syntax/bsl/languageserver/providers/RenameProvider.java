/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Провайдер, обрабатывающий запросы {@code textDocument/rename}
 * и {@code textDocument/prepareRename}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename">Rename Request specification</a>.
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename">Prepare Document Request specification</a>.
 */
@Component
@RequiredArgsConstructor
public final class RenameProvider {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  /**
   * {@link WorkspaceEdit}
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return Изменения документов
   */
  public WorkspaceEdit getRename(DocumentContext documentContext, RenameParams params) {

    var position = params.getPosition();
    var sourceDefinedSymbol = referenceResolver.findReference(documentContext.getUri(), position)
      .flatMap(Reference::getSourceDefinedSymbol);

    Map<String, List<TextEdit>> changes = Stream.concat(
      sourceDefinedSymbol
        .stream()
        .map(referenceIndex::getReferencesTo)
        .flatMap(Collection::stream),
      sourceDefinedSymbol
        .stream().map(RenameProvider::referenceOf)
    ).collect(Collectors.groupingBy(ref -> ref.getUri().toString(), getTexEdits(params)));

    return new WorkspaceEdit(changes);
  }

  private static Reference referenceOf(SourceDefinedSymbol symbol) {
    return Reference.of(
      symbol,
      symbol,
      new Location(symbol.getOwner().getUri().toString(), symbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }

  /**
   * {@link Range}
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return Range
   */
  public Range getPrepareRename(DocumentContext documentContext, TextDocumentPositionParams params) {

    return referenceResolver.findReference(
        documentContext.getUri(), params.getPosition())
      .filter(Reference::isSourceDefinedSymbolReference)
      .map(Reference::getSelectionRange)
      .orElse(null);
  }

  private static Collector<Reference, ?, List<TextEdit>> getTexEdits(RenameParams params) {
    return Collectors.mapping(
      Reference::getSelectionRange,
      Collectors.mapping(range -> newTextEdit(params, range), Collectors.toList())
    );
  }

  private static TextEdit newTextEdit(RenameParams params, Range range) {
    return new TextEdit(range, params.getNewName());
  }

}
