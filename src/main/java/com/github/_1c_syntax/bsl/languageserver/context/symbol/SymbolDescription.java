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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

/**
 * Текстовое описание сущности (символа, члена типа, типа в целом, параметра)
 * для отображения в hover/completion/signature-help.
 * <p>
 * Сознательно не привязан к {@code bsl-parser} — реализации могут
 * поставлять описание из самых разных источников:
 * <ul>
 *   <li>BSL-doc-comment над {@code SourceDefinedSymbol}'ом
 *       (через {@link ParserSymbolDescriptionAdapter});</li>
 *   <li>JSON-метаданные платформенных типов;</li>
 *   <li>{@code lib.config.json} OneScript-библиотек;</li>
 *   <li>аналитика {@code Configuration.xml} (например, синонимы).</li>
 * </ul>
 * <p>
 * Параметры/возвращаемые значения методов остаются на стороне
 * {@code SignatureDescriptor}/{@code ParameterDescriptor} — здесь только
 * общая описательная часть, релевантная любому символу.
 */
public interface SymbolDescription {

  /** Пустое описание — neutral element для интерфейса. */
  SymbolDescription EMPTY = new SymbolDescription() {
    @Override
    public String getPurposeDescription() {
      return "";
    }

    @Override
    public boolean isDeprecated() {
      return false;
    }

    @Override
    public String getDeprecationInfo() {
      return "";
    }
  };

  /**
   * @return основной текст описания (без блока deprecation, без параметров).
   *         Никогда не {@code null}; пустая строка означает «описания нет».
   */
  String getPurposeDescription();

  /**
   * @return {@code true}, если сущность помечена как устаревшая.
   */
  boolean isDeprecated();

  /**
   * @return сопровождающий текст об устаревании (например, на что заменено).
   *         Никогда не {@code null}; пустая строка допустима.
   */
  String getDeprecationInfo();

  /**
   * @return {@code true} если описание считается «пустым» — нет ни описания,
   *         ни пометки об устаревании. Используется для условного формирования
   *         блоков hover.
   */
  default boolean isEmpty() {
    return getPurposeDescription().isBlank() && !isDeprecated() && getDeprecationInfo().isBlank();
  }

  /**
   * Простой фабричный метод для описания «только текст».
   */
  static SymbolDescription of(String description) {
    if (description == null || description.isBlank()) {
      return EMPTY;
    }
    return new SymbolDescription() {
      @Override
      public String getPurposeDescription() {
        return description;
      }

      @Override
      public boolean isDeprecated() {
        return false;
      }

      @Override
      public String getDeprecationInfo() {
        return "";
      }
    };
  }

  /**
   * Фабричный метод для описания с пометкой об устаревании.
   */
  static SymbolDescription of(String description, boolean deprecated, String deprecationInfo) {
    var desc = description == null ? "" : description;
    var depInfo = deprecationInfo == null ? "" : deprecationInfo;
    if (desc.isBlank() && !deprecated && depInfo.isBlank()) {
      return EMPTY;
    }
    return new SymbolDescription() {
      @Override
      public String getPurposeDescription() {
        return desc;
      }

      @Override
      public boolean isDeprecated() {
        return deprecated;
      }

      @Override
      public String getDeprecationInfo() {
        return depInfo;
      }
    };
  }
}
