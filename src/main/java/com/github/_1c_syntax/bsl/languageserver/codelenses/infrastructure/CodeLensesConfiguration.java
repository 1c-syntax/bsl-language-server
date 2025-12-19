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
package com.github._1c_syntax.bsl.languageserver.codelenses.infrastructure;

import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * Spring-конфигурация для определения бинов
 * пакета {@link com.github._1c_syntax.bsl.languageserver.codelenses}.
 */
@Configuration
public class CodeLensesConfiguration {

  /**
   * Получить список сапплаеров линз в разрезе их идентификаторов.
   *
   * @param codeLensSuppliers Плоский список сапплаеров.
   * @return Список сапплаеров линз в разрезе их идентификаторов.
   */
  @Bean
  @SuppressWarnings("unchecked")
  public Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliersById(
    Collection<CodeLensSupplier<? extends CodeLensData>> codeLensSuppliers
  ) {
    return codeLensSuppliers.stream()
      .map(codeLensSupplier -> (CodeLensSupplier<CodeLensData>) codeLensSupplier)
      .collect(Collectors.toMap(CodeLensSupplier::getId, Function.identity()));
  }

  /**
   * Получить список активированных в данный момент сапплаеров линз.
   *
   * @param configuration         Конфигурация сервера.
   * @param codeLensSuppliersById Список сапплаеров линз в разрезе из идентификаторов.
   * @return Список активированных в данный момент сапплаеров линз.
   */
  @Bean
  @Scope(SCOPE_PROTOTYPE)
  public List<CodeLensSupplier<CodeLensData>> enabledCodeLensSuppliers(
    LanguageServerConfiguration configuration,
    @Qualifier("codeLensSuppliersById") Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliersById
  ) {
    var parameters = configuration.getCodeLensOptions().getParameters();
    return codeLensSuppliersById.values().stream()
      .filter(supplier -> supplierIsEnabled(supplier.getId(), parameters))
      .sorted(Comparator.comparing(o ->
        Objects.requireNonNullElse(OrderUtils.getOrder(o.getClass()), Ordered.LOWEST_PRECEDENCE)))
      .collect(Collectors.toList());
  }

  private static boolean supplierIsEnabled(
    String supplierId,
    Map<String, Either<Boolean, Map<String, Object>>> parameters
  ) {
    var supplierConfig = parameters.getOrDefault(supplierId, Either.forLeft(true));
    return supplierConfig.isRight() || supplierConfig.getLeft();
  }
}
