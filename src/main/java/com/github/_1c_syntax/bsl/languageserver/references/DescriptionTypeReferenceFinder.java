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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.symbol.TypeReferenceSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.support.DescriptionElement;
import com.github._1c_syntax.bsl.parser.description.support.SimpleRange;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Finder для имени типа внутри описания символа (BSLDoc) — секции
 * {@code // Параметры:} и {@code // Возвращаемое значение:}.
 *
 * <p>Резолвит {@link DescriptionElement.Type#TYPE_NAME}-элемент под курсором в
 * {@link com.github._1c_syntax.bsl.languageserver.types.model.TypeRef} и
 * возвращает {@link Reference} с {@link TypeReferenceSymbol} — этого достаточно
 * для рендеринга hover'а с документацией типа.
 *
 * <p>Позиции элементов описания — абсолютные координаты в файле, поэтому
 * сопоставление с курсором идёт напрямую, без сдвигов относительно начала
 * описания.
 */
@Component
@Order(300)
@RequiredArgsConstructor
public class DescriptionTypeReferenceFinder implements ReferenceFinder {

  private final ServerContextProvider serverContextProvider;
  private final TypeService typeService;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    // Горячий путь hover — без захвата per-document RWLock, как и у остальных finder'ов.
    return serverContextProvider.getDocumentUnsafeNoLock(uri)
      .flatMap(document -> findReference(document, uri, position));
  }

  private Optional<Reference> findReference(DocumentContext document, URI uri, Position position) {
    var symbolTree = document.getSymbolTree();
    var fileType = document.getFileType();
    var content = document.getContentList();
    var module = symbolTree.getModule();

    for (var method : symbolTree.getMethods()) {
      var reference = method.getDescription()
        .flatMap(description -> findInDescription(description, position, content, fileType, module, uri));
      if (reference.isPresent()) {
        return reference;
      }
    }

    for (var variable : symbolTree.getVariables()) {
      var description = variable.getDescription();
      if (description.isEmpty()) {
        continue;
      }
      var reference = findInDescription(description.get(), position, content, fileType, module, uri);
      if (reference.isPresent()) {
        return reference;
      }
      var trailingReference = description.get().getTrailingDescription()
        .flatMap(trailing -> findInDescription(trailing, position, content, fileType, module, uri));
      if (trailingReference.isPresent()) {
        return trailingReference;
      }
    }

    return Optional.empty();
  }

  private Optional<Reference> findInDescription(
    SourceDefinedSymbolDescription description,
    Position position,
    String[] content,
    FileType fileType,
    SourceDefinedSymbol module,
    URI uri
  ) {
    for (var element : description.getElements()) {
      if (element.type() != DescriptionElement.Type.TYPE_NAME) {
        continue;
      }
      var simpleRange = element.range();
      var range = toRange(simpleRange);
      if (!Ranges.containsPosition(range, position)) {
        continue;
      }
      return typeName(simpleRange, content)
        .flatMap(typeName -> typeService.resolve(typeName, fileType)
          .map(typeRef -> new Reference(
            module,
            new TypeReferenceSymbol(typeName, typeRef),
            uri,
            range,
            OccurrenceType.REFERENCE
          )));
    }
    return Optional.empty();
  }

  private static Range toRange(SimpleRange range) {
    return Ranges.create(range.startLine(), range.startCharacter(), range.endLine(), range.endCharacter());
  }

  private static Optional<String> typeName(SimpleRange range, String[] content) {
    if (range.startLine() != range.endLine() || range.startLine() >= content.length) {
      return Optional.empty();
    }
    var line = content[range.startLine()];
    if (range.startCharacter() < 0 || range.endCharacter() > line.length()
      || range.startCharacter() >= range.endCharacter()) {
      return Optional.empty();
    }
    return Optional.of(line.substring(range.startCharacter(), range.endCharacter()));
  }
}
