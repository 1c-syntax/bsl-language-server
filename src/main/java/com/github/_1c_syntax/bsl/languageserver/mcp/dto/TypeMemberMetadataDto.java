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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Платформенная метаинформация члена типа: контексты исполнения, версионная
 * совместимость, тексты разделов «Возвращаемое значение», «Замечание», «Примеры»,
 * «См. также» из HBK.
 *
 * @param sinceVersion Версия платформы, начиная с которой член доступен; {@code null}, если не задано.
 * @param deprecatedSinceVersion Версия, начиная с которой устарел; {@code null}, если не задано.
 * @param recommendedReplacements Рекомендуемые замены устаревшего члена.
 * @param availabilities Контексты исполнения, в которых член доступен.
 * @param accessMode Режим доступа для свойства ({@code READ} / {@code READ_WRITE}); {@code null} для методов.
 * @param returnValueDescription Текст раздела «Возвращаемое значение»; {@code null}, если отсутствует.
 * @param notes Текст раздела «Замечание»; {@code null}, если отсутствует.
 * @param examples Примеры использования.
 * @param seeAlso «См. также» — связанные сущности.
 */
public record TypeMemberMetadataDto(
  @Nullable String sinceVersion,
  @Nullable String deprecatedSinceVersion,
  List<String> recommendedReplacements,
  List<String> availabilities,
  @Nullable String accessMode,
  @Nullable String returnValueDescription,
  @Nullable String notes,
  List<String> examples,
  List<String> seeAlso
) {

  /** Пустая метаинформация — все поля {@code null} или пустые списки. */
  public static final TypeMemberMetadataDto EMPTY = new TypeMemberMetadataDto(
    null, null, List.of(), List.of(), null, null, null, List.of(), List.of()
  );

  public static TypeMemberMetadataDto from(PlatformMetadata metadata, Language language) {
    return new TypeMemberMetadataDto(
      nullIfBlank(metadata.sinceVersion()),
      nullIfBlank(metadata.deprecatedSinceVersion()),
      List.copyOf(metadata.recommendedReplacements()),
      metadata.availabilities().stream()
        .map(Availability::name)
        .sorted()
        .toList(),
      metadata.accessMode() == null ? null : metadata.accessMode().name(),
      nullIfBlank(metadata.returnValueDescription().forLanguage(language)),
      nullIfBlank(metadata.notes().forLanguage(language)),
      bilingualList(metadata.examples(), language),
      bilingualList(metadata.seeAlso(), language)
    );
  }

  public boolean isEmpty() {
    return sinceVersion == null
      && deprecatedSinceVersion == null
      && recommendedReplacements.isEmpty()
      && availabilities.isEmpty()
      && accessMode == null
      && returnValueDescription == null
      && notes == null
      && examples.isEmpty()
      && seeAlso.isEmpty();
  }

  private static @Nullable String nullIfBlank(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static List<String> bilingualList(List<BilingualString> source, Language language) {
    return source.stream()
      .map(item -> item.forLanguage(language))
      .filter(text -> !text.isBlank())
      .sorted(Comparator.naturalOrder())
      .toList();
  }
}
