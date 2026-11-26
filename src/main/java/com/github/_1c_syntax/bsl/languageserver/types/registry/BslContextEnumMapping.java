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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Маппинг enum'ов bsl-context на одноимённые enum'ы LS-side слоя моделей.
 * <p>
 * Изолирован в отдельный класс из-за коллизии простых имён
 * ({@code Availability}, {@code AccessMode}) — здесь приоритетно импортированы
 * LS-side enum'ы текущего проекта, а enum'ы из зависимости {@code bsl-context}
 * адресуются через полное имя. Соглашение по проекту: FQN допустим только для
 * классов из внешних зависимостей при коллизии простых имён.
 */
final class BslContextEnumMapping {

  private BslContextEnumMapping() {
  }

  static Set<Availability> mapAvailabilities(
    List<com.github._1c_syntax.bsl.context.api.Availability> raw
  ) {
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    var result = EnumSet.noneOf(Availability.class);
    for (var a : raw) {
      result.add(mapAvailability(a));
    }
    return result;
  }

  static Availability mapAvailability(com.github._1c_syntax.bsl.context.api.Availability a) {
    return switch (a) {
      case THIN_CLIENT -> Availability.THIN_CLIENT;
      case WEB_CLIENT -> Availability.WEB_CLIENT;
      case MOBILE_CLIENT -> Availability.MOBILE_CLIENT;
      case SERVER -> Availability.SERVER;
      case THICK_CLIENT -> Availability.THICK_CLIENT;
      case EXTERNAL_CONNECTION -> Availability.EXTERNAL_CONNECTION;
      case MOBILE_APPLICATION_CLIENT -> Availability.MOBILE_APPLICATION_CLIENT;
      case MOBILE_APPLICATION_SERVER -> Availability.MOBILE_APPLICATION_SERVER;
      case MOBILE_STANDALONE_SERVER -> Availability.MOBILE_STANDALONE_SERVER;
    };
  }

  @Nullable
  static AccessMode mapAccessMode(com.github._1c_syntax.bsl.context.api.@Nullable AccessMode mode) {
    if (mode == null) {
      return null;
    }
    return switch (mode) {
      case READ -> AccessMode.READ;
      case READ_WRITE -> AccessMode.READ_WRITE;
    };
  }
}
