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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.TypeReferenceSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

/**
 * Построитель hover-контента для имени типа (например, в описании метода —
 * BSLDoc). Выводит локализованное имя типа, метку «тип» и описание типа
 * (если оно известно). Работает одинаково для инстанцируемых и
 * неинстанцируемых типов — в отличие от
 * {@link ConstructorHoverBuilder}, не показывает синтаксис {@code Новый}.
 */
@Component
@RequiredArgsConstructor
public class TypeReferenceMarkupContentBuilder implements MarkupContentBuilder {

  private final TypeService typeService;
  private final TypeRegistry typeRegistry;
  private final CollectionHoverHints collectionHoverHints;
  private final Resources resources;
  private final LanguageServerConfiguration configuration;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (TypeReferenceSymbol) reference.symbol();
    var ref = symbol.getTypeRef();
    var lang = configuration.getLanguage();
    var fileType = reference.from().getOwner().getFileType();

    var localizedTypeName = typeRegistry.displayName(ref, lang);
    if (localizedTypeName.isBlank()) {
      localizedTypeName = symbol.getTypeName();
    }

    var sb = new StringBuilder();
    sb.append("```bsl\n").append(localizedTypeName).append("\n```\n");
    sb.append("\n_").append(resources.getResourceString(getClass(), "type")).append("_ `")
      .append(localizedTypeName).append('`');

    var description = typeService.getDescription(ref, lang, fileType);
    if (!description.isBlank()) {
      sb.append("\n\n").append(description);
    }

    collectionHoverHints.append(sb, ref, typeRegistry);

    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return TypeReferenceSymbol.class;
  }
}
