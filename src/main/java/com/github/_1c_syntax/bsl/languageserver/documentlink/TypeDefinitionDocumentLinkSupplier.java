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
package com.github._1c_syntax.bsl.languageserver.documentlink;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github._1c_syntax.bsl.parser.description.support.DescriptionElement;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentLink;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Сапплаер кликабельных ссылок на тип в описаниях символов (методов и переменных).
 * <p>
 * В doc-комментарии типы упоминаются в структурных позициях (типы параметров/возвращаемого
 * значения метода, тип переменной по нотации «тип в начале»). Сапплаер разрешает каждое такое
 * имя типа через {@link TypeService} и, если у типа есть объявляющий исходный символ
 * ({@link TypeService#definingSymbol}), формирует {@link DocumentLink} на его местоположение.
 * <p>
 * Ссылки создаются только для типов с источниковым символом ({@code USER}/{@code CONFIGURATION}):
 * платформенные/примитивные типы объявляющего символа не имеют и пропускаются. Это же служит
 * гвардом для описаний переменных — произвольный текст в висячем комментарии не резолвится
 * в тип и ссылку не порождает.
 */
@Component
@RequiredArgsConstructor
public class TypeDefinitionDocumentLinkSupplier implements DocumentLinkSupplier {

  private final TypeService typeService;

  @Override
  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    var documentLinks = new ArrayList<DocumentLink>();

    var symbolTree = documentContext.getSymbolTree();
    Stream.<Describable>concat(
      symbolTree.getMethods().stream(),
      symbolTree.getVariables().stream()
    ).forEach(describable ->
      describable.getDescription().ifPresent(description ->
        addTypeLinksFromDescription(documentContext, description, documentLinks)
      )
    );

    return documentLinks;
  }

  private void addTypeLinksFromDescription(
    DocumentContext documentContext,
    SourceDefinedSymbolDescription description,
    List<DocumentLink> documentLinks
  ) {
    for (var element : description.getElements()) {
      if (element.type() != DescriptionElement.Type.TYPE_NAME) {
        continue;
      }
      addTypeLink(documentContext, description, element, documentLinks);
    }

    if (description instanceof VariableDescription variableDescription) {
      variableDescription.getTrailingDescription().ifPresent(trailingDescription ->
        addTypeLinksFromDescription(documentContext, trailingDescription, documentLinks)
      );
    }
  }

  private void addTypeLink(
    DocumentContext documentContext,
    SourceDefinedSymbolDescription description,
    DescriptionElement element,
    List<DocumentLink> documentLinks
  ) {
    elementText(element, description)
      .flatMap(typeName -> typeService.resolve(typeName, documentContext.getFileType()))
      .flatMap(typeRef -> typeService.definingSymbol(typeRef, documentContext))
      .ifPresent(symbol -> {
        var elementRange = element.range();
        var range = Ranges.create(
          elementRange.startLine(),
          elementRange.startCharacter(),
          elementRange.endLine(),
          elementRange.endCharacter()
        );
        documentLinks.add(new DocumentLink(range, symbolTarget(symbol)));
      });
  }

  /**
   * Читает текст элемента описания (имя типа) из исходного текста описания по диапазону элемента.
   * <p>
   * Диапазон элемента абсолютный; первая строка описания начинается со столбца
   * {@code range.startCharacter()}, остальные — со столбца 0.
   */
  private static Optional<String> elementText(
    DescriptionElement element,
    SourceDefinedSymbolDescription description
  ) {
    var lines = description.getDescription().split("\n", -1);
    var descriptionRange = description.getRange();
    var elementRange = element.range();

    int lineIdx = elementRange.startLine() - descriptionRange.startLine();
    if (lineIdx < 0 || lineIdx >= lines.length) {
      return Optional.empty();
    }

    var lineText = lines[lineIdx];
    int start = elementRange.startCharacter() - (lineIdx == 0 ? descriptionRange.startCharacter() : 0);
    int end = start + elementRange.length();
    if (start < 0 || end > lineText.length() || start >= end) {
      return Optional.empty();
    }

    return Optional.of(lineText.substring(start, end));
  }

  private static String symbolTarget(SourceDefinedSymbol symbol) {
    var start = symbol.getSelectionRange().getStart();
    return "%s#L%d,%d".formatted(symbol.getOwner().getUri(), start.getLine() + 1, start.getCharacter() + 1);
  }
}
