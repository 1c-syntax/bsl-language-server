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

import com.github._1c_syntax.bsl.languageserver.context.FileType;

import org.jspecify.annotations.Nullable;

/**
 * Языковой скоуп символа/типа: к какому из двух BSL-диалектов он принадлежит.
 * <ul>
 *   <li>{@link #BSL} — только встроенный язык 1С:Предприятия (видим в .bsl-файлах).</li>
 *   <li>{@link #OS} — только OneScript (видим в .os-файлах).</li>
 *   <li>{@link #BOTH} — общий, виден в обоих типах файлов.</li>
 * </ul>
 * <p>
 * Скоуп присваивается источнику символа (PlatformTypesProvider, JSON ресурсы
 * builtin-globals.json / builtin-oscript-globals.json, OScriptLibraryIndex,
 * Configuration-провайдеры). При коллизии имён скоуп повышается до {@link #BOTH}.
 */
public enum LanguageScope {
  BSL,
  OS,
  BOTH;

  /**
   * Виден ли символ с данным скоупом в файле с данным типом.
   * {@code null} fileType трактуется как «без фильтрации» — возвращает {@code true}.
   */
  public boolean matches(FileType fileType) {
    if (fileType == null || this == BOTH) {
      return true;
    }
    return switch (fileType) {
      case BSL -> this == BSL;
      case OS -> this == OS;
    };
  }

  /**
   * Скоуп по умолчанию для встроенных ресурсов BSL.
   */
  public static LanguageScope forFileType(@Nullable FileType fileType) {
    if (fileType == null) {
      return BOTH;
    }
    return switch (fileType) {
      case BSL -> BSL;
      case OS -> OS;
    };
  }

  /**
   * Слияние двух скоупов: если они различаются — результат {@link #BOTH}.
   */
  public LanguageScope merge(LanguageScope other) {
    if (other == null || other == this) {
      return this;
    }
    return BOTH;
  }
}
