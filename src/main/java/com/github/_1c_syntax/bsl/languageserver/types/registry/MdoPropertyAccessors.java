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

import com.github._1c_syntax.bsl.mdo.AdditionalIndexOwner;
import com.github._1c_syntax.bsl.mdo.BasedOnOwner;
import com.github._1c_syntax.bsl.mdo.DataLockFieldsOwner;
import com.github._1c_syntax.bsl.mdo.InputByStringOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.storage.AdditionalIndex;
import com.github._1c_syntax.bsl.types.MdoReference;

import java.util.List;

/**
 * Массовые свойства объектов метаданных: `ВводитсяНаОсновании`, `ВводПоСтроке`,
 * `ПоляБлокировкиДанных`, `ДополнительныеИндексы`.
 * <p>
 * Каждое объявлено в mdclasses своим интерфейсом-владельцем (mdclasses#677), и у каждого
 * вида объекта набор этих интерфейсов свой. Здесь они собраны вместе, чтобы читающему
 * свойство хватало одной зависимости вместо пяти.
 */
final class MdoPropertyAccessors {

  private MdoPropertyAccessors() {
    // утилитный класс
  }

  /** Типы, на основании которых вводится объект; пусто — свойства у вида нет. */
  static List<MdoReference> basedOn(MD md) {
    return md instanceof BasedOnOwner owner ? owner.getBasedOn() : List.of();
  }

  /** Поля, по которым доступен ввод по строке; пусто — свойства у вида нет. */
  static List<MdoReference> inputByString(MD md) {
    return md instanceof InputByStringOwner owner ? owner.getInputByString() : List.of();
  }

  /** Поля блокировки данных; пусто — свойства у вида нет. */
  static List<MdoReference> dataLockFields(MD md) {
    return md instanceof DataLockFieldsOwner owner ? owner.getDataLockFields() : List.of();
  }

  /** Дополнительные индексы; пусто — свойства у вида нет. */
  static List<AdditionalIndex> additionalIndexes(MD md) {
    return md instanceof AdditionalIndexOwner owner ? owner.getAdditionalIndexes() : List.of();
  }
}
