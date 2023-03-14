/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;

import java.beans.Introspector;
import java.util.List;

/**
 * Базовый интерфейс для наполнения {@link com.github._1c_syntax.bsl.languageserver.providers.CodeLensProvider}
 * данными о доступных в документе линзах.
 * <p>
 * Для целей улучшения производительности шаги получения линз документа и их "разрешение"
 * (создание объекта {@link CodeLens#setCommand(Command)}) должно проводиться в два этапа.
 * <p>
 * Для хранения промежуточных данных между созданием и разрешением линзы необходимо использовать
 * поле {@link CodeLens#setData(Object)}, заполняя его объектом класса
 * {@link CodeLensSupplier#getCodeLensDataClass()}.
 * <p>
 * Конкретный сапплаер может расширить состав данных, хранимые в линзе, доопределив дата-класс,
 * наследующий {@link CodeLensData}, и указав его тип в качестве типа-параметра класса.
 *
 * @param <T> Конкретный тип для данных линзы.
 */
public interface CodeLensSupplier<T extends CodeLensData> {

  /**
   * Идентификатор сапплаера. Если линза содержит поле {@link CodeLens#getData()},
   * идентификатор в данных линзы должен совпадать с данным идентификатором.
   *
   * @return Идентификатор сапплаера.
   */
  default String getId() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith("CodeLensSupplier")) {
      simpleName = simpleName.substring(0, simpleName.length() - "CodeLensSupplier".length());
      simpleName = Introspector.decapitalize(simpleName);
    }

    return simpleName;
  }

  /**
   * Возвращает необходимость применения сапплаера на конкретном документе.
   *
   * @param documentContext Документ.
   * @return Необходимость применения.
   */
  default boolean isApplicable(DocumentContext documentContext) {
    return true;
  }

  /**
   * Получить список линз, доступных в документе.
   * <p>
   * Предпочтительно, чтобы линзы, возвращаемые этим методом были "не-разрешенными"
   *
   * @param documentContext Документ, для которого надо рассчитать линзы.
   * @return Список линз.
   */
  List<CodeLens> getCodeLenses(DocumentContext documentContext);

  /**
   * Получить класс для хранения данных линзы.
   * <p>
   * При создании не-разрешенной линзы поле {@link CodeLens#setData(Object)}
   * должно заполняться объектом данного класса.
   *
   * @return Конкретный класс для хранения данных линзы.
   */
  Class<T> getCodeLensDataClass();

  /**
   * Выполнить операцию "разрешения" линзы.
   * <p>
   * По умолчанию линза возвращается не-разрешенной.
   *
   * @param documentContext Документ, которому принадлежит линза.
   * @param unresolved      Линза, которую надо разрешить.
   * @param data            Десериализованные данные линзы.
   * @return Разрешенная линза (с заполненным полем {@link CodeLens#getCommand()})
   */
  default CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, T data) {
    return unresolved;
  }
}
