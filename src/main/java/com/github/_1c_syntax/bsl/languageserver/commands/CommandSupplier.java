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
package com.github._1c_syntax.bsl.languageserver.commands;

import org.eclipse.lsp4j.Command;

import java.beans.Introspector;
import java.util.List;
import java.util.Optional;

/**
 * Базовый интерфейс для наполнения {@link com.github._1c_syntax.bsl.languageserver.providers.CommandProvider}
 * данными о доступных в документе командах.
 * <p>
 * Конкретный сапплаер может расширить состав данных, передаваемых в аргументах команды, доопределив дата-класс,
 * наследующий {@link CommandArguments}, и указав его тип в качестве типа-параметра класса.
 *
 * @param <T> Конкретный тип для аргументов команды.
 */

public interface CommandSupplier<T extends CommandArguments> {

  String COMMAND_SUPPLIER_SUFFIX = "CommandSupplier";

  /**
   * Идентификатор сапплаера.
   * <p>
   * Идентификатор в аргументах команды должен совпадать с данным идентификатором.
   *
   * @return Идентификатор сапплаера.
   */
  default String getId() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith(COMMAND_SUPPLIER_SUFFIX)) {
      simpleName = simpleName.substring(0, simpleName.length() - COMMAND_SUPPLIER_SUFFIX.length());
      simpleName = Introspector.decapitalize(simpleName);
    }

    return simpleName;
  }

  /**
   * Создать DTO команды.
   *
   * @param title Заголовок команды.
   * @return Команда с заполненными заголовком и идентификатором команды.
   */
  default Command createCommand(String title, T arguments) {
    return new Command(title, getId(), List.of(arguments));
  }

  /**
   * Получить класс для аргументов команды.
   *
   * @return Конкретный класс для аргументов команды.
   */
  Class<T> getCommandArgumentsClass();

  /**
   * Выполнить серверную команду.
   *
   * @param arguments Аргументы команды.
   *
   * @return Результат выполнения команды.
   */
  Optional<Object> execute(T arguments);

  /**
   * Флаг, показывающий необходимость обновить inlay hints после выполнения команды.
   *
   * @return Флаг, показывающий необходимость обновить inlay hints после выполнения команды.
   */
  default boolean needRefreshInlayHintsAfterExecuteCommand() {
    return false;
  }

  /**
   * Флаг, показывающий необходимость обновить линзы после выполнения команды.
   *
   * @return Флаг, показывающий необходимость обновить линзы после выполнения команды.
   */
  default boolean needRefreshCodeLensesAfterExecuteCommand() {
    return false;
  }

}
