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

import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintData;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.jsontype.NamedType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-конфигурация для определения бинов
 * пакета {@link com.github._1c_syntax.bsl.languageserver.inlayhints}.
 */
@Configuration
public class InlayHintsConfiguration {

  /**
   * Получить список сапплаеров inlay hints в разрезе их идентификаторов.
   *
   * @param inlayHintSuppliers Плоский список сапплаеров.
   * @return Список сапплаеров inlay hints в разрезе их идентификаторов.
   */
  @Bean
  @SuppressWarnings("unchecked")
  public Map<String, InlayHintSupplier<InlayHintData>> inlayHintSuppliersById(
    Collection<InlayHintSupplier<? extends InlayHintData>> inlayHintSuppliers
  ) {
    return inlayHintSuppliers.stream()
      .map(inlayHintSupplier -> (InlayHintSupplier<InlayHintData>) inlayHintSupplier)
      .collect(Collectors.toMap(InlayHintSupplier::getId, Function.identity()));
  }

  /**
   * Зарегистрировать классы данных inlay hints как полиморфные подтипы JsonMapper.
   *
   * @param inlayHintSuppliers Плоский список сапплаеров.
   * @return Кастомайзер JsonMapper, регистрирующий подтипы по их идентификаторам.
   */
  @Bean
  public JsonMapperBuilderCustomizer inlayHintJsonCustomizer(
    Collection<InlayHintSupplier<? extends InlayHintData>> inlayHintSuppliers
  ) {
    List<NamedType> namedTypes = inlayHintSuppliers.stream()
      .filter(supplier -> supplier.getInlayHintDataClass() != null)
      .map(supplier -> new NamedType(supplier.getInlayHintDataClass(), supplier.getId()))
      .toList();
    return builder -> namedTypes.forEach(builder::registerSubtypes);
  }

  /**
   * Проверить, включён ли сапплаер по его id.
   *
   * @param supplierId id сапплаера
   * @param parameters параметры из конфигурации
   * @return true если сапплаер включён
   */
  public static boolean supplierIsEnabled(
    String supplierId,
    Map<String, Either<Boolean, Map<String, Object>>> parameters
  ) {
    var supplierConfig = parameters.getOrDefault(supplierId, Either.forLeft(true));
    return supplierConfig.isRight() || supplierConfig.getLeft();
  }
}
