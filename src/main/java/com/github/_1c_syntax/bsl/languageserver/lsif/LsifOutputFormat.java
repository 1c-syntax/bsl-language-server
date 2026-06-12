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
package com.github._1c_syntax.bsl.languageserver.lsif;

import lombok.Getter;

/**
 * Формат вывода LSIF-индекса.
 * <p>
 * LSIF спецификация поддерживает два формата:
 * <ul>
 *   <li>NDJSON (Newline Delimited JSON) — каждый элемент на отдельной строке</li>
 *   <li>JSON — массив элементов в одном JSON-документе</li>
 * </ul>
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsif/0.6.0/specification/">LSIF Specification</a>
 */
@Getter
public enum LsifOutputFormat {

  /**
   * NDJSON (Newline Delimited JSON).
   * <p>
   * Каждый LSIF-элемент записывается на отдельной строке.
   * Рекомендуемый формат для больших проектов, так как позволяет
   * потоковую обработку и не требует загрузки всего файла в память.
   * <p>
   * Расширение файла: .lsif
   */
  NDJSON("ndjson", ".lsif"),

  /**
   * JSON (массив элементов).
   * <p>
   * Все LSIF-элементы записываются как массив в одном JSON-документе.
   * Удобен для отладки и просмотра, но требует больше памяти
   * для обработки больших проектов.
   * <p>
   * Расширение файла: .lsif.json
   */
  JSON("json", ".lsif.json");

  /**
   * Формат по умолчанию.
   */
  public static final LsifOutputFormat DEFAULT = NDJSON;

  /**
   * Идентификатор формата для CLI.
   */
  private final String formatId;

  /**
   * Рекомендуемое расширение файла.
   */
  private final String fileExtension;

  LsifOutputFormat(String formatId, String fileExtension) {
    this.formatId = formatId;
    this.fileExtension = fileExtension;
  }
}
