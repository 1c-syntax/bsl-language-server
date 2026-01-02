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
package com.github._1c_syntax.bsl.languageserver.inlayhints.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * Spring-конфигурация для определения бинов
 * пакета {@link com.github._1c_syntax.bsl.languageserver.inlayhints}.
 */
@Configuration
public class InlayHintsConfiguration {

  /**
   * Получить список активированных в данный момент сапплаеров inlay hints.
   *
   * @param configuration      Конфигурация сервера.
   * @param inlayHintSuppliers Список сапплаеров inlay hints в разрезе из идентификаторов.
   * @return Список активированных в данный момент сапплаеров inlay hints.
   */
  @Bean
  @Scope(SCOPE_PROTOTYPE)
  public List<InlayHintSupplier> enabledInlayHintSuppliers(
    LanguageServerConfiguration configuration,
    Collection<InlayHintSupplier> inlayHintSuppliers
  ) {
    var parameters = configuration.getInlayHintOptions().getParameters();
    return inlayHintSuppliers.stream()
      .filter(supplier -> supplierIsEnabled(supplier.getId(), parameters))
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
