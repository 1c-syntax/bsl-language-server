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
package com.github._1c_syntax.bsl.languageserver.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.commands.CommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.CommandSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Конфигурация ObjectMapper для сериализации/десериализации.
 * <p>
 * Настраивает Jackson ObjectMapper для работы с code lenses и командами,
 * регистрируя необходимые типы для полиморфной сериализации.
 */
@Configuration
public class ObjectMapperConfiguration  {

  @Bean
  public ObjectMapper objectMapper(
    Collection<CodeLensSupplier<? extends CodeLensData>> codeLensResolvers,
    Collection<CommandSupplier<? extends CommandArguments>> commandSuppliers
  ) {

    var namedTypes = new ArrayList<NamedType>();
    codeLensResolvers.stream()
      .map(ObjectMapperConfiguration::toNamedType)
      .collect(Collectors.toCollection(() -> namedTypes));
    commandSuppliers.stream()
      .map(ObjectMapperConfiguration::toNamedType)
      .collect(Collectors.toCollection(() -> namedTypes));

    var objectMapperBuilder = JsonMapper.builder();

    namedTypes.forEach(objectMapperBuilder::registerSubtypes);

    return objectMapperBuilder.build();
  }

  private static NamedType toNamedType(CodeLensSupplier<? extends CodeLensData> codeLensSupplier) {
    return new NamedType(codeLensSupplier.getCodeLensDataClass(), codeLensSupplier.getId());
  }

  private static NamedType toNamedType(CommandSupplier<? extends CommandArguments> commandSupplier) {
    return new NamedType(commandSupplier.getCommandArgumentsClass(), commandSupplier.getId());
  }
}
