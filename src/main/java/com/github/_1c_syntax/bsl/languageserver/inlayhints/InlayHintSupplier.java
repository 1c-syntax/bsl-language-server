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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;

import java.beans.Introspector;
import java.util.List;

/**
 * Базовый интерфейс для наполнения {@link com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider}
 * данными о доступных в документе inlay hints.
 * <p>
 * Для хранения промежуточных данных между созданием и разрешением хинта необходимо использовать
 * поле {@link InlayHint#setData(Object)}, заполняя его объектом класса
 * {@link InlayHintSupplier#getInlayHintDataClass()}.
 * <p>
 * Конкретный сапплаер может расширить состав данных, хранимых в хинте, доопределив дата-класс,
 * наследующий {@link InlayHintData}, и указав его тип в качестве типа-параметра класса.
 *
 * @param <T> Конкретный тип для данных хинта.
 */
public interface InlayHintSupplier<T extends InlayHintData> {

  String INLAY_HINT_SUPPLIER = "InlayHintSupplier";

  /**
   * Идентификатор сапплаера. Если хинт содержит поле {@link InlayHint#getData()},
   * идентификатор в данных хинта должен совпадать с данным идентификатором.
   *
   * @return Идентификатор сапплаера.
   */
  default String getId() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith(INLAY_HINT_SUPPLIER)) {
      simpleName = simpleName.substring(0, simpleName.length() - INLAY_HINT_SUPPLIER.length());
      simpleName = Introspector.decapitalize(simpleName);
    }

    return simpleName;
  }

  /**
   * Получить inlay hints, доступные в документе.
   *
   * @param documentContext Контекст документа, для которого надо рассчитать inlay hints.
   * @param params          Параметры запроса.
   * @return Список inlay hints в документе.
   */
  List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params);

  /**
   * Получить класс для хранения данных хинта.
   * <p>
   * При создании не-разрешённого хинта поле {@link InlayHint#setData(Object)}
   * должно заполняться объектом данного класса.
   *
   * @return Конкретный класс для хранения данных хинта.
   */
  Class<T> getInlayHintDataClass();

  /**
   * Дорассчитать «тяжёлые» поля хинта (tooltip и т.п.) при обработке
   * {@code inlayHint/resolve}.
   * <p>
   * Базовая реализация возвращает хинт без изменений: сапплаеры, не
   * откладывающие построение полей, ничего не делают на резолве. Сапплаеры,
   * кладущие данные в {@link InlayHint#getData()} при жадном расчёте, должны
   * переопределить метод и восстановить отложенные поля по этим данным.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param unresolved      Неразрешённый хинт (с заполненным {@link InlayHint#getData()}).
   * @param data            Десериализованные данные хинта.
   * @return Разрешённый хинт.
   */
  default InlayHint resolve(DocumentContext documentContext, InlayHint unresolved, T data) {
    return unresolved;
  }
}
