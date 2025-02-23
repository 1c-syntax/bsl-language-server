/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.net.URI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

/**
 * Интерфейс DTO для хранения промежуточных данных линз между созданием линзы и ее разрешением.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "id", visible = true)
public interface CodeLensData {
  /**
   * URI документа, с которым связана линза.
   *
   * @return URI документа, с которым связана линза.
   */
  URI getUri();

  /**
   * Идентификатор линзы.
   * <p>
   * Должен совпадать с {@link CodeLensSupplier#getId()} сапплаера,
   * создающего данные линзы.
   *
   * @return Идентификатор линзы.
   */
  String getId();
}
