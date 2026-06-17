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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
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
  private final CollectionHoverHints collectionHoverHints;
  private final Resources resources;
  private final LanguageServerConfiguration configuration;

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }

  public MarkupContent build(
    String typeName,
    TypeRef ref,
    SignatureDescriptor chosen,
    List<SignatureDescriptor> ctors,
    boolean disclaim,
    String classDescription,
    FileType fileType
  ) {
    var sb = new StringBuilder();
    var lang = configuration.getLanguage();
    var localizedTypeName = typeService.displayName(ref, lang);
    if (localizedTypeName.isBlank()) {
      localizedTypeName = typeName;
    }
    sb.append("```bsl\n").append(tr("newKeyword")).append(' ').append(localizedTypeName).append('(');
    if (chosen != null) {
      sb.append(chosen.parameters().stream().map(p -> p.displayName(lang)).collect(Collectors.joining(", ")));
    }
    sb.append(')').append("\n```\n");
    sb.append("\n_").append(tr("constructorOf")).append("_ `").append(localizedTypeName).append('`');
    // Bilingual: всегда зовём typeService.getDescription(ref, lang, fileType) — он
    // отдаст ru или en по текущей LS-локали и вариант описания по языку файла
    // (BSL/OS). {@code classDescription} от caller'а используется только если
    // registry не знает описание (legacy/source-defined символы).
    var bilingual = typeService.getDescription(ref, lang, fileType);
    var classDesc = !bilingual.isBlank() ? bilingual : (classDescription != null ? classDescription : "");
    if (!classDesc.isBlank()) {
      sb.append("\n\n").append(classDesc);
    }
    if (chosen != null) {
      var chosenDesc = chosen.displayDescription(lang);
      if (chosenDesc != null && !chosenDesc.isBlank()) {
        sb.append("\n\n").append(chosenDesc);
      }
    }
    if (chosen != null && !chosen.parameters().isEmpty()) {
      sb.append("\n\n**").append(tr("parameters")).append("**\n");
      for (var p : chosen.parameters()) {
        HoverParameters.appendNameAndType(sb, p.displayName(lang), renderTypeSet(p.types(), lang), p.optional());
        if (!p.defaultValue().isBlank()) {
          sb.append(" _= ").append(p.defaultValue()).append('_');
        }
        var pDesc = p.displayDescription(lang);
        if (pDesc != null && !pDesc.isBlank()) {
          sb.append(" — ").append(pDesc);
        }
        sb.append('\n');
      }
    }
    collectionHoverHints.append(sb, ref, fileType, typeService);
    if (disclaim) {
      sb.append("\n\n_").append(tr("noMatchingConstructor")).append('_');
    }
    if (ctors.size() > 1) {
      sb.append("\n\n**").append(tr("allConstructorVariants")).append("**\n");
      for (var sig : ctors) {
        sb.append("- `").append(tr("newKeyword")).append(' ').append(localizedTypeName).append('(')
          .append(sig.parameters().stream().map(p -> p.displayName(lang)).collect(Collectors.joining(", ")))
          .append(")`");
        var sigDesc = sig.displayDescription(lang);
        if (sigDesc != null && !sigDesc.isBlank()) {
          sb.append(" — ").append(sigDesc);
        }
        sb.append('\n');
      }
    }
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  /**
   * Форматирует {@code TypeSet} как {@code "Тип1 | Тип2"}. Пустой набор —
   * пустая строка.
   */
  private String renderTypeSet(TypeSet types, Language lang) {
    if (types == null || types.isEmpty()) {
      return "";
    }
    return types.refs().stream()
      .map(r -> typeService.displayName(r, lang))
      .filter(name -> name != null && !name.isEmpty())
      .collect(Collectors.joining(" | "));
  }
}
