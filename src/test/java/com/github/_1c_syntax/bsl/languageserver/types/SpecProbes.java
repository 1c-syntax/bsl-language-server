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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Position;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Запросы к фикстурам сверки с методической рекомендацией «Типизация кода».
 * <p>
 * В фикстурах каждый пункт рекомендации записан парой строк: значение кладут в переменную
 * с именем пункта («Проба_3_22» — пункт 3.22) и передают её в вызов «Использование(…)».
 * Тип спрашивают в месте этой передачи — так же, как его спросил бы редактор при наведении.
 */
final class SpecProbes {

  /** Фикстура раздела «Как это работает». */
  static final String SECTION_2 = "./src/test/resources/types/spec/SpecSection2.bsl";

  /** Фикстура раздела «Возможности типизирующих документирующих комментариев». */
  static final String SECTION_3 = "./src/test/resources/types/spec/SpecSection3.bsl";

  /** Фикстура раздела «Лучшие практики написания типизированного кода». */
  static final String SECTION_4 = "./src/test/resources/types/spec/SpecSection4.bsl";

  private SpecProbes() {
  }

  /**
   * Имя переменной-пробы для пункта рекомендации.
   *
   * @param item номер пункта, например {@code "3.22"}.
   * @return имя переменной в фикстуре.
   */
  static String probe(String item) {
    return "Проба_" + item.replace('.', '_');
  }

  /**
   * Тип пробы в месте её использования.
   *
   * @param typeService     система типов.
   * @param documentContext документ фикстуры.
   * @param item            номер пункта рекомендации, например {@code "3.22"}.
   * @return набор типов переменной в этой точке.
   */
  static TypeSet typeOf(TypeService typeService, DocumentContext documentContext, String item) {
    return typeOfVariable(typeService, documentContext, probe(item));
  }

  /**
   * Тип произвольной переменной в месте её передачи в «Использование».
   *
   * @param typeService     система типов.
   * @param documentContext документ фикстуры.
   * @param variable        имя переменной.
   * @return набор типов переменной в этой точке.
   */
  static TypeSet typeOfVariable(TypeService typeService, DocumentContext documentContext, String variable) {
    var content = documentContext.getContent();
    var call = "Использование(" + variable + ")";
    var callStart = content.indexOf(call);
    assertThat(callStart).as("проба «%s» есть в фикстуре", variable).isNotNegative();

    var closingParen = callStart + call.length() - 1;
    var lineStart = content.lastIndexOf('\n', closingParen) + 1;
    var line = (int) content.substring(0, closingParen).chars().filter(c -> c == '\n').count();

    return typeService.expressionTypesAt(documentContext, new Position(line, closingParen - lineStart - 1));
  }

  /**
   * Имена типов набора.
   *
   * @param types набор типов.
   * @return имена типов в порядке набора.
   */
  static List<String> names(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }

  /**
   * Имена полей единственного типа набора — полей «открытого» объекта данных
   * (структуры, таблицы значений и т.п.).
   *
   * @param types набор ровно из одного типа.
   * @return имена полей этого типа.
   */
  static List<String> fieldNames(TypeSet types) {
    assertThat(types.refs()).hasSize(1);
    return List.copyOf(types.getLocalFields(types.refs().iterator().next()).keySet());
  }

  /**
   * Типы элементов единственного типа-коллекции набора.
   *
   * @param types набор ровно из одного типа.
   * @return имена типов элементов коллекции.
   */
  static List<String> elementNames(TypeSet types) {
    assertThat(types.refs()).hasSize(1);
    return names(types.getElementTypes(types.refs().iterator().next()));
  }
}
