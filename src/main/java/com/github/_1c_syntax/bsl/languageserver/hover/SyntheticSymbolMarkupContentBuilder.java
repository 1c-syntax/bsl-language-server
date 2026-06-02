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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

/**
 * Построитель hover-контента для {@link SyntheticSymbol} (свойства глобальной
 * области, элементы коллекций конфигурации, имена библиотечных модулей,
 * платформенные глобальные переменные).
 *
 * <p>Synthetic-методы (PLATFORM_GLOBAL_METHOD / PLATFORM_MEMBER_METHOD)
 * обрабатываются отдельным {@link PlatformMemberSymbolMarkupContentBuilder}'ом
 * после обогащения {@link MemberDescriptor}'ом в reference finder'е, поэтому
 * сюда они не попадают.
 */
@Component
@RequiredArgsConstructor
public class SyntheticSymbolMarkupContentBuilder implements MarkupContentBuilder {

  private final TypeRegistry typeRegistry;
  private final CollectionHoverHints collectionHoverHints;
  private final Resources resources;
  private final LanguageServerConfiguration configuration;

  @Override
  public MarkupContent getContent(Reference reference) {
    var symbol = (SyntheticSymbol) reference.symbol();
    // Hover — элемент интерфейса: язык отображения из настроек LS, а не из
    // ScriptVariant (язык исходников).
    var lang = configuration.getLanguage();
    var sb = new StringBuilder();
    sb.append("```bsl\n").append(symbol.getName());
    var valueType = symbol.getValueType();
    if (valueType != null && valueType != TypeRef.UNKNOWN && !valueType.qualifiedName().isEmpty()) {
      sb.append(": ").append(typeRegistry.displayName(valueType, lang));
    }
    sb.append("\n```\n");

    sb.append('\n').append(roleDescription(symbol));

    var description = symbol.getDescription();
    if (description != null && !description.isBlank()) {
      sb.append("\n\n").append(description);
    }

    if (valueType != null && valueType != TypeRef.UNKNOWN) {
      collectionHoverHints.append(sb, valueType, typeRegistry);
    }

    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }


  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return SyntheticSymbol.class;
  }

  private String roleDescription(SyntheticSymbol symbol) {
    return "_" + resources.getResourceString(getClass(),
      "role." + symbol.getSyntheticKind().name()) + "_";
  }
}
