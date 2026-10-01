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
package com.github._1c_syntax.bsl.languageserver.index;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Записи по документам, привязанные к источнику, из которого построены, — дереву разбора
 * или дереву символов документа.
 * <p>
 * Запись отдаётся, только пока источник тот же самый (сравнение по ссылке). После правки
 * документа источник новый, и запись строится заново, даже если ключ у нового текста
 * прежний: URI, имя или символ. Поэтому ответ не зависит от того, успел ли индекс
 * получить событие об изменении документа.
 *
 * @param <S> тип источника.
 * @param <V> тип записи.
 */
public final class SourceBoundRecords<S, V> {

  private final Map<URI, Entry<S, V>> byUri = new ConcurrentHashMap<>();

  /**
   * Запись документа, построенная из этого источника.
   * <p>
   * Строится вне блокировок отображения: построение бывает долгим, а двойная работа при
   * гонке безвредна — запись зависит только от источника. Из двух записей одного источника
   * остаётся первая.
   *
   * @param uri     URI документа.
   * @param source  источник, из которого строится запись.
   * @param builder построение записи из источника.
   * @return запись, построенная из этого источника.
   */
  public V get(URI uri, S source, Function<S, V> builder) {
    var current = byUri.get(uri);
    if (current != null && current.source() == source) {
      return current.value();
    }
    var built = new Entry<>(source, builder.apply(source));
    return byUri.merge(uri, built, (previous, fresh) -> previous.source() == source ? previous : fresh).value();
  }

  /**
   * Удалить запись документа.
   *
   * @param uri URI документа.
   */
  public void remove(URI uri) {
    byUri.remove(uri);
  }

  private record Entry<S, V>(S source, V value) {
  }
}
