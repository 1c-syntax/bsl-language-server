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
package com.github._1c_syntax.bsl.languageserver.commands.infrastructure;

import com.github._1c_syntax.bsl.languageserver.commands.CommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.CommandSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-конфигурация для определения бинов
 * пакета {@link com.github._1c_syntax.bsl.languageserver.commands}.
 */
@Configuration
public class CommandsConfiguration {

  /**
   * Получить список сапплаеров команд в разрезе их идентификаторов.
   *
   * @param commandSuppliers Плоский список сапплаеров.
   * @return Список сапплаеров линз в разрезе их идентификаторов.
   */
  @Bean
  @SuppressWarnings("unchecked")
  public Map<String, CommandSupplier<CommandArguments>> commandSuppliersById(
    Collection<CommandSupplier<? extends CommandArguments>> commandSuppliers
  ) {
    return commandSuppliers.stream()
      .map(commandSupplier -> (CommandSupplier<CommandArguments>) commandSupplier)
      .collect(Collectors.toMap(CommandSupplier::getId, Function.identity()));
  }

}
