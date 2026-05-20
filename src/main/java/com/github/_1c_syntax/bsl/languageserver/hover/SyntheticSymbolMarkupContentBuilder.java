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

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
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
public class SyntheticSymbolMarkupContentBuilder implements MarkupContentBuilder<SyntheticSymbol> {

  private final TypeRegistry typeRegistry;

  @Override
  public MarkupContent getContent(SyntheticSymbol symbol) {
    var sb = new StringBuilder();
    sb.append("```bsl\n").append(symbol.getName());
    var valueType = symbol.getValueType();
    if (valueType != null && valueType != TypeRef.UNKNOWN && !valueType.qualifiedName().isEmpty()) {
      sb.append(": ").append(valueType.qualifiedName());
    }
    sb.append("\n```\n");

    sb.append('\n').append(roleDescription(symbol));

    var description = symbol.getDescription();
    if (description != null && !description.isBlank()) {
      sb.append("\n\n").append(description);
    }

    if (valueType != null && valueType != TypeRef.UNKNOWN) {
      CollectionHoverHints.append(sb, valueType, typeRegistry);
    }

    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Property;
  }

  @Override
  public Class<? extends Symbol> getSymbolClass() {
    return SyntheticSymbol.class;
  }

  private static String roleDescription(SyntheticSymbol symbol) {
    return switch (symbol.getSyntheticKind()) {
      case PLATFORM_GLOBAL_PROPERTY -> "_глобальное свойство_";
      case PLATFORM_GLOBAL_METHOD -> "_глобальная функция_";
      case PLATFORM_GLOBAL_VARIABLE -> "_глобальная переменная_";
      case PLATFORM_MEMBER_PROPERTY -> "_свойство_";
      case PLATFORM_MEMBER_METHOD -> "_метод_";
      case CONFIGURATION_OBJECT -> "_объект конфигурации_";
      case LIBRARY_MODULE -> "_модуль библиотеки_";
    };
  }
}
