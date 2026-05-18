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

import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сборщик markdown-контента для hover'а на имени класса в выражении
 * {@code Новый <Класс>(...)}. Выводит подобранную (по арности) сигнатуру
 * конструктора, описание класса и список всех вариантов.
 */
@Component
@RequiredArgsConstructor
public class ConstructorHoverBuilder {

  private final TypeService typeService;

  public MarkupContent build(
    String typeName,
    TypeRef ref,
    SignatureDescriptor chosen,
    List<SignatureDescriptor> ctors,
    boolean disclaim,
    String classDescription
  ) {
    var sb = new StringBuilder();
    sb.append("```bsl\nНовый ").append(typeName).append('(');
    if (chosen != null) {
      sb.append(chosen.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")));
    }
    sb.append(')').append("\n```\n");
    sb.append("\n_конструктор типа_ `").append(ref.qualifiedName()).append('`');
    var classDesc = classDescription != null ? classDescription : typeService.getDescription(ref);
    if (!classDesc.isBlank()) {
      sb.append("\n\n").append(classDesc);
    }
    if (chosen != null && chosen.description() != null && !chosen.description().isBlank()) {
      sb.append("\n\n").append(chosen.description());
    }
    if (chosen != null && !chosen.parameters().isEmpty()) {
      sb.append("\n\n**Параметры:**\n");
      for (var p : chosen.parameters()) {
        sb.append("- `").append(p.name()).append('`');
        if (p.optional()) {
          sb.append(" _(необязательный)_");
        }
        if (p.description() != null && !p.description().isBlank()) {
          sb.append(" — ").append(p.description());
        }
        sb.append('\n');
      }
    }
    if (disclaim) {
      sb.append("\n\n_Не найдено описание, подходящее под текущий вызов конструктора._");
    }
    if (ctors.size() > 1) {
      sb.append("\n\n**Все варианты конструктора:**\n");
      for (var sig : ctors) {
        sb.append("- `Новый ").append(typeName).append('(')
          .append(sig.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")))
          .append(")`");
        if (sig.description() != null && !sig.description().isBlank()) {
          sb.append(" — ").append(sig.description());
        }
        sb.append('\n');
      }
    }
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }
}
