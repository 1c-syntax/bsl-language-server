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
package com.github._1c_syntax.bsl.languageserver.configuration;

import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Map;
import java.util.Optional;

/**
 * Чтение параметров поставщиков (code lens, inlay hints) из раздела {@code parameters}
 * конфигурации.
 * <p>
 * Значение по идентификатору поставщика — {@code false} (поставщик выключен), {@code true} или
 * объект с настройками (поставщик включён). Без записи поставщик включён.
 */
@UtilityClass
public class SupplierParameters {

  /**
   * Включён ли поставщик.
   *
   * @param parameters Раздел {@code parameters} опций фичи
   * @param supplierId Идентификатор поставщика
   * @return {@code false}, только если поставщик явно выключен
   */
  public static boolean isEnabled(Map<String, Either<Boolean, Map<String, Object>>> parameters, String supplierId) {
    var supplierParameters = parameters.get(supplierId);
    return supplierParameters == null || supplierParameters.isRight() || supplierParameters.getLeft();
  }

  /**
   * Значение настройки поставщика.
   *
   * @param parameters Раздел {@code parameters} опций фичи
   * @param supplierId Идентификатор поставщика
   * @param name       Имя настройки
   * @return Значение; пусто, если у поставщика нет объекта настроек или в нём нет этой настройки
   */
  public static Optional<Object> setting(
    Map<String, Either<Boolean, Map<String, Object>>> parameters,
    String supplierId,
    String name
  ) {
    return Optional.ofNullable(parameters.get(supplierId))
      .filter(Either::isRight)
      .map(supplierParameters -> supplierParameters.getRight().get(name));
  }
}
