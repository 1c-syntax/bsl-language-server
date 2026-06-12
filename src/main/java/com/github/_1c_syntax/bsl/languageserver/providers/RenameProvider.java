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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;
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
  private final Resources resources;

  /**
   * {@link WorkspaceEdit}
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return Изменения документов
   */
  public WorkspaceEdit getRename(DocumentContext documentContext, RenameParams params) {

    checkNewName(params.getNewName());

    var position = params.getPosition();
    var sourceDefinedSymbol = referenceResolver.findReference(documentContext.getUri(), position)
      .filter(RenameProvider::isRenameable)
      .flatMap(Reference::getSourceDefinedSymbol);

    Map<String, List<TextEdit>> changes = Stream.concat(
      sourceDefinedSymbol
        .stream()
        .map(referenceIndex::getReferencesTo)
        .flatMap(Collection::stream),
      sourceDefinedSymbol
        .stream().map(RenameProvider::referenceOf)
    ).collect(Collectors.groupingBy(ref -> ref.uri().toString(), getTexEdits(params)));

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
  public @Nullable Range getPrepareRename(DocumentContext documentContext, TextDocumentPositionParams params) {
    return referenceResolver.findReference(documentContext.getUri(), params.getPosition())
      .filter(Reference::isSourceDefinedSymbolReference)
      .filter(RenameProvider::isRenameable)
      .map(Reference::selectionRange)
      .orElse(null);
  }

  /**
   * Проверяет, поддерживается ли переименование символа, на который указывает ссылка.
   * <p>
   * Имя модуля задаётся метаданными и не может быть переименовано текстовой правкой,
   * поэтому ссылки на символы с {@link SymbolKind#Module} не переименовываются.
   *
   * @param reference Ссылка на символ.
   * @return {@code true}, если символ можно переименовать через текстовую правку.
   */
  private static boolean isRenameable(Reference reference) {
    return reference.symbol().getSymbolKind() != SymbolKind.Module;
  }

  private static Collector<Reference, ?, List<TextEdit>> getTexEdits(RenameParams params) {
    return Collectors.mapping(
      Reference::selectionRange,
      Collectors.mapping(range -> newTextEdit(params, range), Collectors.toList())
    );
  }

  private static TextEdit newTextEdit(RenameParams params, Range range) {
    return new TextEdit(range, params.getNewName());
  }

  private void checkNewName(@Nullable String newName) {
    if (!isValidIdentifier(newName)) {
      var message = resources.getResourceString(getClass(), "invalidNewName", newName);
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, message, null));
    }
  }

  private static boolean isValidIdentifier(@Nullable String newName) {
    if (newName == null || newName.isEmpty()) {
      return false;
    }

    var tokens = new BSLTokenizer(newName).getTokens();

    return tokens.size() == 2
      && tokens.get(0).getType() == BSLLexer.IDENTIFIER
      && newName.equals(tokens.get(0).getText());
  }

}
