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
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Отрисовка блока платформенных метаданных синтакс-помощника в markdown
 * hover'а: «доступно с …», «устарело с …», рекомендуемые замены, режим
 * доступа, контексты исполнения, описание возвращаемого значения,
 * «Замечание», примеры, «См. также».
 * <p>
 * Метаданные приходят как от членов типа ({@code MemberDescriptor.metadata()}),
 * так и от самих типов и конструкторов — разметка у всех одинаковая, отличается
 * лишь набор заполненных полей.
 */
@Component
@RequiredArgsConstructor
public class PlatformMetadataRenderer {

  /** Начало markdown-секции блока: пустая строка + жирный заголовок. */
  private static final String SECTION_PREFIX = "\n\n**";

  private final Resources resources;
  private final LanguageServerConfiguration configuration;

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }

  /**
   * Дописывает блок метаданных в {@code sb}. Если метаданные пусты — ничего
   * не пишет.
   */
  public void append(StringBuilder sb, PlatformMetadata md) {
    if (md.isEmpty()) {
      return;
    }
    if (!md.deprecatedSinceVersion().isBlank()) {
      sb.append(SECTION_PREFIX).append(tr("deprecatedSince")).append("** ")
        .append(md.deprecatedSinceVersion());
    }
    if (!md.sinceVersion().isBlank()) {
      sb.append(SECTION_PREFIX).append(tr("sinceVersion")).append("** ").append(md.sinceVersion());
    }
    if (!md.recommendedReplacements().isEmpty()) {
      sb.append(SECTION_PREFIX).append(tr("recommendedReplacements")).append("** ")
        .append(md.recommendedReplacements().stream()
          .map(r -> "`" + r + "`")
          .collect(Collectors.joining(", ")));
    }
    appendAccessMode(sb, md.accessMode());
    appendAvailabilities(sb, md.availabilities());
    var lang = configuration.getLanguage();
    var rv = md.returnValueDescription().forLanguage(lang);
    if (!rv.isBlank()) {
      sb.append(SECTION_PREFIX).append(tr("returnValueDescription")).append("** ").append(rv);
    }
    var nt = md.notes().forLanguage(lang);
    if (!nt.isBlank()) {
      sb.append(SECTION_PREFIX).append(tr("notes")).append("** ").append(nt);
    }
    appendBilingualList(sb, tr("example"), md.examples(), true, lang);
    appendBilingualList(sb, tr("seeAlso"), md.seeAlso(), false, lang);
  }

  /** Режим доступа есть только у свойств; у методов, типов и конструкторов он {@code null}. */
  private void appendAccessMode(StringBuilder sb, @Nullable AccessMode accessMode) {
    if (accessMode == null) {
      return;
    }
    var mode = accessMode == AccessMode.READ ? tr("accessReadOnly") : tr("accessReadWrite");
    sb.append(SECTION_PREFIX).append(tr("accessMode")).append("** ").append(mode);
  }

  private static void appendBilingualList(StringBuilder sb, String title,
                                          List<BilingualString> items, boolean asCodeBlock,
                                          Language lang) {
    if (items.isEmpty()) {
      return;
    }
    var resolved = new ArrayList<String>(items.size());
    for (var bi : items) {
      var s = bi.forLanguage(lang);
      if (!s.isBlank()) {
        resolved.add(s);
      }
    }
    appendList(sb, title, resolved, asCodeBlock);
  }

  private void appendAvailabilities(StringBuilder sb, Set<Availability> availabilities) {
    if (availabilities.isEmpty()) {
      return;
    }
    sb.append(SECTION_PREFIX).append(tr("availabilities")).append("** ");
    sb.append(availabilities.stream()
      .map(this::displayName)
      .collect(Collectors.joining(", ")));
  }

  private String displayName(Availability availability) {
    return tr("availability." + availability.name());
  }

  private static void appendList(StringBuilder sb, String title, List<String> items, boolean asCodeBlock) {
    if (items.isEmpty()) {
      return;
    }
    sb.append(SECTION_PREFIX).append(title).append(":**");
    for (var item : items) {
      if (item.isBlank()) {
        continue;
      }
      if (asCodeBlock) {
        sb.append("\n\n```bsl\n").append(item).append("\n```");
      } else {
        sb.append("\n- ").append(item);
      }
    }
  }
}
