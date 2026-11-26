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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.StringJoiner;

/**
 * Общий рендер hover'а в "constructor"-стиле для OneScript-классов.
 * <p>
 * Используется:
 * <ul>
 *   <li>{@link ConstructorSymbolMarkupContentBuilder} — когда у класса есть
 *   явный {@link ConstructorSymbol} ({@code ПриСозданииОбъекта} /
 *   {@code OnObjectCreate}) и ссылка ведёт прямо на него;</li>
 *   <li>{@link ModuleSymbolMarkupContentBuilder} — когда конструктора нет
 *   и ссылка ведёт в {@code ModuleSymbol} .os-файла-класса; hover всё равно
 *   должен показывать "Новый ИмяКласса()" сигнатуру.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OScriptClassConstructorRenderer {

  private final DescriptionFormatter descriptionFormatter;
  private final OScriptLibraryIndex oScriptLibraryIndex;

  /**
   * Срендерить hover для класса с явным конструктором.
   */
  public MarkupContent render(DocumentContext classDocument, ConstructorSymbol constructor) {
    return render(classDocument, Optional.of(constructor));
  }

  /**
   * Срендерить hover для класса без явного конструктора — только сигнатура
   * {@code Новый ИмяКласса()} без параметров.
   */
  public MarkupContent renderWithoutConstructor(DocumentContext classDocument) {
    return render(classDocument, Optional.empty());
  }

  private MarkupContent render(DocumentContext classDocument, Optional<ConstructorSymbol> maybeConstructor) {
    var sb = new StringJoiner("\n");

    var className = resolveClassName(classDocument);
    var parameters = maybeConstructor
      .map(descriptionFormatter::getParametersSignatureDescription)
      .orElse("");

    sb.add("```bsl");
    sb.add("Новый " + className + "(" + parameters + ")");
    sb.add("```");

    var moduleSymbol = classDocument.getSymbolTree().getModule();
    var location = descriptionFormatter.getLocation(moduleSymbol);
    descriptionFormatter.addSectionIfNotEmpty(sb, location);

    maybeConstructor.ifPresent((ConstructorSymbol constructor) -> {
      descriptionFormatter.addSectionIfNotEmpty(sb, descriptionFormatter.getPurposeSection(constructor));
      descriptionFormatter.addSectionIfNotEmpty(sb, descriptionFormatter.getParametersSection(constructor));
    });

    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  private String resolveClassName(DocumentContext classDocument) {
    var libraryEntry = oScriptLibraryIndex.findByUri(classDocument.getUri());
    if (libraryEntry.isPresent()) {
      return libraryEntry.get().qualifiedName();
    }
    // fallback — basename .os-файла без расширения.
    var path = classDocument.getUri().getPath();
    var fileName = path.substring(path.lastIndexOf('/') + 1);
    var dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
