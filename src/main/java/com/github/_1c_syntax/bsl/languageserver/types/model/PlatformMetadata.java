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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Платформенные метаданные члена ({@link MemberDescriptor}) или конструктора.
 * Все текстовые поля и текстовые списки — {@link BilingualString}; при
 * смене LS-локали hover'у не нужно пересоздавать дескриптор, локаль
 * выбирается на лету через {@link BilingualString#forLanguage(Language)}.
 *
 * @param sinceVersion            версия платформы, начиная с которой член доступен
 * @param deprecatedSinceVersion  версия, начиная с которой устарел
 * @param recommendedReplacements рекомендуемые замены
 * @param availabilities          контексты исполнения
 * @param accessMode              режим доступа (для свойств)
 * @param returnValueDescription  «Возвращаемое значение:» (ru + en)
 * @param notes                   «Замечание:» (ru + en)
 * @param examples                примеры использования (каждый элемент — ru + en)
 * @param seeAlso                 «См. также» — связанные сущности (каждый — ru + en)
 */
public record PlatformMetadata(
  String sinceVersion,
  String deprecatedSinceVersion,
  List<String> recommendedReplacements,
  Set<Availability> availabilities,
  AccessMode accessMode,
  BilingualString returnValueDescription,
  BilingualString notes,
  List<BilingualString> examples,
  List<BilingualString> seeAlso
) {

  public static final PlatformMetadata EMPTY = new PlatformMetadata(
    "", "", List.of(), Set.of(), null,
    BilingualString.EMPTY, BilingualString.EMPTY,
    List.of(), List.of()
  );

  public PlatformMetadata {
    sinceVersion = sinceVersion == null ? "" : sinceVersion;
    deprecatedSinceVersion = deprecatedSinceVersion == null ? "" : deprecatedSinceVersion;
    recommendedReplacements = recommendedReplacements == null ? List.of() : List.copyOf(recommendedReplacements);
    availabilities = availabilities == null ? Set.of() : Set.copyOf(availabilities);
    if (returnValueDescription == null) {
      returnValueDescription = BilingualString.EMPTY;
    }
    if (notes == null) {
      notes = BilingualString.EMPTY;
    }
    examples = examples == null ? List.of() : List.copyOf(examples);
    seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
  }

  /**
   * Compat-конструктор: одноязычные строки {@code returnValueDescription}/
   * {@code notes} + одни лишь ru-{@code examples}/{@code seeAlso} (без en).
   */
  public PlatformMetadata(
    String sinceVersion,
    String deprecatedSinceVersion,
    List<String> recommendedReplacements,
    Set<Availability> availabilities,
    @Nullable AccessMode accessMode,
    String returnValueDescription,
    String notes,
    List<String> ruExamples,
    List<String> ruSeeAlso
  ) {
    this(sinceVersion, deprecatedSinceVersion, recommendedReplacements, availabilities, accessMode,
      BilingualString.of(returnValueDescription), BilingualString.of(notes),
      wrapRu(ruExamples), wrapRu(ruSeeAlso));
  }

  private static List<BilingualString> wrapRu(List<String> ruOnly) {
    if (ruOnly == null || ruOnly.isEmpty()) {
      return List.of();
    }
    var out = new ArrayList<BilingualString>(ruOnly.size());
    for (var s : ruOnly) {
      out.add(BilingualString.of(s));
    }
    return List.copyOf(out);
  }

  public boolean isEmpty() {
    return sinceVersion.isEmpty()
      && deprecatedSinceVersion.isEmpty()
      && recommendedReplacements.isEmpty()
      && availabilities.isEmpty()
      && accessMode == null
      && returnValueDescription.isEmpty()
      && notes.isEmpty()
      && examples.isEmpty()
      && seeAlso.isEmpty();
  }

  public boolean hasVersionInfo() {
    return !sinceVersion.isEmpty() || !deprecatedSinceVersion.isEmpty();
  }
}
