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

import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;

/**
 * Имена типов дерева метаданных и вопросы к ним.
 * <p>
 * Синтетические типы дерева ({@code ОбъектМетаданных: Справочник.Контрагенты},
 * {@code КоллекцияОбъектовМетаданных.Реквизиты.Контрагенты}) заводит
 * {@link MetadataCollectionSpecializer} — здесь собраны их имена, чтобы
 * потребители снаружи ({@code semantictokens}) могли отличить <b>объект</b>
 * метаданных от коллекции объектов, не зная, как эти имена складываются.
 */
public final class MetadataTypeNames {

  /** Начало имени типа конкретного объекта дерева метаданных. */
  static final String METADATA_OBJECT = "ОбъектМетаданных";

  /** Начало имени типа коллекции объектов дерева метаданных. */
  static final String METADATA_COLLECTION = "КоллекцияОбъектовМетаданных";

  private MetadataTypeNames() {
    // утилитный класс
  }

  /**
   * Описывает ли тип конкретный объект дерева метаданных
   * ({@code ОбъектМетаданных: Справочник.Контрагенты}, {@code ОбъектМетаданных: Реквизит}).
   *
   * @param ref тип.
   * @return {@code true} — за типом стоит объект метаданных, а не коллекция и не значение.
   */
  public static boolean isMetadataObject(TypeRef ref) {
    return ref.qualifiedName().startsWith(METADATA_OBJECT);
  }
}
