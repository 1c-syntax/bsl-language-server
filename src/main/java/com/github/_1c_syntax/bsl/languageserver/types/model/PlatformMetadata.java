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

import java.util.List;
import java.util.Set;

/**
 * Платформенные метаданные члена ({@link MemberDescriptor}) или конструктора.
 * <p>
 * Поля — опциональные сведения из синтакс-помощника (bsl-context),
 * нужные hover'у/completion'у/диагностикам версий: «доступно с», «устарело с»,
 * список контекстов исполнения (тонкий клиент / сервер / …), режим доступа,
 * рекомендуемая замена, описание возвращаемого значения, заметки/примеры/
 * ссылки. Все поля могут быть пустыми ({@code ""}, {@link List#of()}) —
 * {@link #EMPTY} собирает «нулевой» вариант.
 *
 * @param sinceVersion             версия платформы, начиная с которой член
 *                                 доступен (например, {@code "8.3.27"});
 *                                 пустая строка — не указано
 * @param deprecatedSinceVersion   версия платформы, начиная с которой член
 *                                 признан устаревшим; пустая строка — не указано
 * @param recommendedReplacements  имена рекомендуемых заменяющих методов/
 *                                 свойств; пустой список — не указано
 * @param availabilities           контексты исполнения, в которых член доступен;
 *                                 пустой набор — не ограничено
 * @param accessMode               режим доступа для свойств; {@code null} —
 *                                 не указано / не применимо
 * @param returnValueDescription   текстовое описание возвращаемого значения
 *                                 (для методов); пустая строка — не указано
 * @param notes                    «Замечание» из синтакс-помощника; пустая
 *                                 строка — не указано
 * @param examples                 примеры использования; пустой список — не указано
 * @param seeAlso                  «См. также» — ссылки на связанные сущности
 */
public record PlatformMetadata(
  String sinceVersion,
  String deprecatedSinceVersion,
  List<String> recommendedReplacements,
  Set<Availability> availabilities,
  AccessMode accessMode,
  String returnValueDescription,
  String notes,
  List<String> examples,
  List<String> seeAlso
) {

  public static final PlatformMetadata EMPTY = new PlatformMetadata(
    "", "", List.of(), Set.of(), null, "", "", List.of(), List.of()
  );

  public PlatformMetadata {
    sinceVersion = sinceVersion == null ? "" : sinceVersion;
    deprecatedSinceVersion = deprecatedSinceVersion == null ? "" : deprecatedSinceVersion;
    recommendedReplacements = recommendedReplacements == null ? List.of() : List.copyOf(recommendedReplacements);
    availabilities = availabilities == null ? Set.of() : Set.copyOf(availabilities);
    returnValueDescription = returnValueDescription == null ? "" : returnValueDescription;
    notes = notes == null ? "" : notes;
    examples = examples == null ? List.of() : List.copyOf(examples);
    seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
  }

  /**
   * @return {@code true}, если все поля пустые — метаданные эффективно отсутствуют.
   */
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

  /**
   * @return {@code true}, если у члена указано «доступно с …» (sinceVersion непустой)
   *         или «устарело с …» (deprecatedSinceVersion непустой).
   */
  public boolean hasVersionInfo() {
    return !sinceVersion.isEmpty() || !deprecatedSinceVersion.isEmpty();
  }
}
