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
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github._1c_syntax.bsl.parser.description.support.SimpleRange;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentLink;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сапплаер кликабельных ссылок на тип в описаниях символов (методов и переменных).
 * <p>
 * В doc-комментарии типы упоминаются как типы параметров/возвращаемого значения метода и как
 * тип переменной (нотация «тип в начале», в т.ч. в висячем комментарии). Идентичность типа берётся
 * из семантических аксессоров парсера ({@link DescriptionTypes#typesOf}), а не из координат текста.
 * Каждое имя типа разрешается через {@link TypeService}; если у типа есть объявляющий исходный символ
 * ({@link TypeService#definingSymbol}), формируется {@link DocumentLink} на его местоположение.
 * <p>
 * Ссылки создаются только для типов с источниковым символом ({@code USER}/{@code CONFIGURATION}):
 * платформенные/примитивные типы объявляющего символа не имеют и пропускаются. Это же служит гвардом
 * для описаний переменных — произвольный текст в висячем комментарии не резолвится в тип.
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
    DescriptionTypes.typesOf(description)
      .forEach(type -> addTypeLink(documentContext, type, documentLinks));

    if (description instanceof VariableDescription variableDescription) {
      variableDescription.getTrailingDescription().ifPresent(trailingDescription ->
        addTypeLinksFromDescription(documentContext, trailingDescription, documentLinks)
      );
    }
  }

  private void addTypeLink(
    DocumentContext documentContext,
    TypeDescription type,
    List<DocumentLink> documentLinks
  ) {
    var name = DescriptionTypes.resolveName(type);
    if (name.isBlank()) {
      return;
    }

    typeService.resolve(name, documentContext.getFileType())
      .flatMap(typeRef -> typeService.definingSymbol(typeRef, documentContext))
      .ifPresent(symbol -> {
        SimpleRange elementRange = type.element().range();
        var range = Ranges.create(
          elementRange.startLine(),
          elementRange.startCharacter(),
          elementRange.endLine(),
          elementRange.endCharacter()
        );
        documentLinks.add(new DocumentLink(range, symbolTarget(symbol)));
      });
  }

  private static String symbolTarget(SourceDefinedSymbol symbol) {
    var start = symbol.getSelectionRange().getStart();
    return "%s#L%d,%d".formatted(symbol.getOwner().getUri(), start.getLine() + 1, start.getCharacter() + 1);
  }
}
