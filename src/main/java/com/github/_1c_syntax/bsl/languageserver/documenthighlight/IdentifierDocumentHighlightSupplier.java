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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Поставщик подсветки вхождений идентификаторов (переменных, параметров, методов).
 * <p>
 * При установке курсора на идентификатор переменной, параметра или имени метода
 * подсвечиваются объявление символа и все его вхождения в текущем документе.
 * Объявление и присваивания значению отображаются как {@link DocumentHighlightKind#Write},
 * остальные обращения — как {@link DocumentHighlightKind#Read}.
 */
@Component
@RequiredArgsConstructor
public class IdentifierDocumentHighlightSupplier implements DocumentHighlightSupplier {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  @Override
  public List<DocumentHighlight> getDocumentHighlight(
    DocumentHighlightParams params,
    DocumentContext documentContext,
    Optional<TerminalNodeInfo> terminalNodeInfo
  ) {
    if (terminalNodeInfo.isEmpty()) {
      return Collections.emptyList();
    }

    if (terminalNodeInfo.get().tokenType() != BSLParser.IDENTIFIER) {
      return Collections.emptyList();
    }

    var uri = documentContext.getUri();

    var maybeSymbol = referenceResolver.findReference(uri, params.getPosition())
      .flatMap(Reference::getSourceDefinedSymbol);

    if (maybeSymbol.isEmpty()) {
      return Collections.emptyList();
    }

    var symbol = maybeSymbol.get();
    List<DocumentHighlight> highlights = new ArrayList<>();
    List<Range> seenRanges = new ArrayList<>();

    addDeclarationHighlight(symbol, uri, highlights, seenRanges);

    referenceIndex.getReferencesTo(symbol).stream()
      .filter(reference -> reference.uri().equals(uri))
      .forEach(reference -> addReferenceHighlight(reference, highlights, seenRanges));

    return highlights;
  }

  /**
   * Добавляет подсветку объявления символа, если оно расположено в текущем документе.
   *
   * @param symbol      символ, объявление которого подсвечивается
   * @param uri         URI текущего документа
   * @param highlights  список подсветок, в который добавляется результат
   * @param seenRanges  уже добавленные диапазоны для предотвращения дублирования
   */
  private static void addDeclarationHighlight(
    SourceDefinedSymbol symbol,
    URI uri,
    List<DocumentHighlight> highlights,
    List<Range> seenRanges
  ) {
    if (!symbol.getOwner().getUri().equals(uri)) {
      return;
    }

    var range = symbol.getSelectionRange();
    if (seenRanges.contains(range)) {
      return;
    }

    seenRanges.add(range);
    highlights.add(new DocumentHighlight(range, DocumentHighlightKind.Write));
  }

  /**
   * Добавляет подсветку для вхождения символа с учётом типа обращения.
   *
   * @param reference  вхождение символа в документе
   * @param highlights список подсветок, в который добавляется результат
   * @param seenRanges уже добавленные диапазоны для предотвращения дублирования
   */
  private static void addReferenceHighlight(
    Reference reference,
    List<DocumentHighlight> highlights,
    List<Range> seenRanges
  ) {
    var range = reference.selectionRange();
    if (seenRanges.contains(range)) {
      return;
    }

    seenRanges.add(range);

    var kind = reference.occurrenceType() == OccurrenceType.DEFINITION
      ? DocumentHighlightKind.Write
      : DocumentHighlightKind.Read;

    highlights.add(new DocumentHighlight(range, kind));
  }
}
