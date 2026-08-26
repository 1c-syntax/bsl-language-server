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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Register;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCube;
import com.github._1c_syntax.bsl.mdo.children.StandardAttribute;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Что именно платформа называет плейсхолдером в имени поля таблицы запросов.
 * <p>
 * Плейсхолдеры именованы по виду детей объекта метаданных
 * ({@code <Имя измерения>}, {@code <Имя ресурса>}), поэтому по имени
 * плейсхолдера и объекту определяются реквизиты, которые за ним стоят.
 */
final class QueryTablePlaceholders {

  static final String ATTRIBUTE = "Имя реквизита";
  static final String COMMON_ATTRIBUTE = "Имя общего реквизита";
  static final String DIMENSION = "Имя измерения";
  static final String RESOURCE = "Имя ресурса";
  static final String TABULAR_SECTION = "Имя табличной части";
  static final String JOURNAL_COLUMN = "Имя графы журнала";
  static final String ADDRESSING_ATTRIBUTE = "Имя реквизита адресации";
  static final String FIELD = "Имя поля";
  static final String EXT_DIMENSION_NUMBER = "Номер субконто";

  private QueryTablePlaceholders() {
  }

  /**
   * Реквизиты объекта метаданных, которые стоят за плейсхолдером.
   *
   * @param placeholder имя плейсхолдера без угловых скобок.
   * @param md          объект метаданных таблицы.
   * @return реквизиты; пусто, если таких у объекта нет либо плейсхолдер
   *   заполняется не из реквизитов объекта.
   */
  static List<? extends Attribute> attributesFor(String placeholder, MD md) {
    return switch (placeholder) {
      case ATTRIBUTE -> ownAttributes(md);
      case DIMENSION -> md instanceof Register register ? register.getDimensions() : List.of();
      case RESOURCE -> resources(md);
      case JOURNAL_COLUMN, FIELD -> customAttributes(allAttributes(md));
      case ADDRESSING_ATTRIBUTE -> md instanceof Task task ? task.getAddressingAttributes() : List.of();
      default -> List.of();
    };
  }

  /**
   * Имена для подстановки в плейсхолдер внутри имени поля
   * ({@code <Имя ресурса>Оборот} → {@code СуммаОборот},
   * {@code Субконто<Номер субконто>} → {@code Субконто1}).
   *
   * @param placeholder имя плейсхолдера без угловых скобок.
   * @param request     запрос полей.
   * @return подставляемые имена в порядке объявления; пусто, если подставлять нечего.
   */
  static List<String> expansionValues(String placeholder, QueryTableRequest request) {
    if (EXT_DIMENSION_NUMBER.equals(placeholder)) {
      return extDimensionNumbers(request);
    }
    var md = request.mdo();
    if (md == null) {
      return List.of();
    }
    return attributesFor(placeholder, md).stream()
      .map(Attribute::getName)
      .filter(name -> !name.isBlank())
      .toList();
  }

  /**
   * Номера субконто: от единицы до максимального количества субконто у счёта.
   * Число задаёт план счетов, на котором стоит регистр бухгалтерии, — этим же
   * пределом ограничен и пользовательский режим.
   *
   * @param request запрос полей.
   * @return номера строками; пусто, если регистр не бухгалтерский, план счетов
   *   не найден либо субконто у счетов нет.
   */
  private static List<String> extDimensionNumbers(QueryTableRequest request) {
    var configuration = request.configuration();
    if (configuration == null || !(request.mdo() instanceof AccountingRegister register)) {
      return List.of();
    }
    var count = configuration.findChild(register.getChartOfAccounts())
      .filter(ChartOfAccounts.class::isInstance)
      .map(ChartOfAccounts.class::cast)
      .map(ChartOfAccounts::getMaxExtDimensionCount)
      .orElse(0);
    return IntStream.rangeClosed(1, count).mapToObj(String::valueOf).toList();
  }

  /**
   * Собственные реквизиты объекта. У регистра они лежат отдельно от измерений и
   * ресурсов, у остальных объектов — вперемешку со стандартными.
   */
  private static List<? extends Attribute> ownAttributes(MD md) {
    if (md instanceof Register register) {
      return customAttributes(register.getAttributes());
    }
    return customAttributes(allAttributes(md));
  }

  /**
   * Реквизиты без стандартных: стандартные платформа объявляет полями таблицы
   * с конкретными именами, и через плейсхолдер они попали бы вторично.
   */
  private static List<? extends Attribute> customAttributes(List<? extends Attribute> attributes) {
    return attributes.stream().filter(attribute -> !(attribute instanceof StandardAttribute)).toList();
  }

  private static List<? extends Attribute> resources(MD md) {
    if (md instanceof Register register) {
      return register.getResources();
    }
    if (md instanceof ExternalDataSourceCube cube) {
      return cube.getResources();
    }
    return List.of();
  }

  private static List<? extends Attribute> allAttributes(MD md) {
    return md instanceof AttributeOwner owner ? owner.getAllAttributes() : List.of();
  }
}
