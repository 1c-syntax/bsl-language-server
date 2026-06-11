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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptAnnotations;
import lombok.experimental.UtilityClass;

/**
 * Имена аннотаций фреймворка «ОСень» (Autumn).
 * <p>
 * Базовый набор имён; алиасы (killjoy) и пользовательские аннотации через
 * мета-аннотации разрешаются отдельно.
 */
@UtilityClass
public class AutumnAnnotations {

  /** Объявление компонента-желудя над конструктором. */
  public static final String COMPONENT = "Желудь";
  /** Класс-фабрика желудей (дуб); сам по себе тоже желудь. Размещается над конструктором. */
  public static final String OAK = "Дуб";
  /** Фабричный метод, создающий желудь; допустим только в классе-дубе. */
  public static final String FACTORY = "Завязь";
  /** Точка внедрения зависимости. */
  public static final String INJECTION = "Пластилин";
  /** Приоритетный желудь при конфликте имён/прозвищ. */
  public static final String PRIMARY = "Верховный";
  /** Дополнительное имя (алиас) желудя; повторяемая. */
  public static final String QUALIFIER = "Прозвище";
  /**
   * Аннотация прилепляемой коллекции желудей (autumn-collections). Размещается над
   * конструктором класса-реализации коллекции; в параметре
   * {@link OScriptAnnotations#VALUE_PARAMETER} задано имя коллекции
   * (например, «Массив», «Соответствие»).
   */
  public static final String ATTACHABLE_COLLECTION = "ПрилепляемаяКоллекция";

  /** Параметр {@code Тип} аннотаций {@code &Пластилин}/{@code &Завязь}. */
  public static final String TYPE_PARAMETER = "Тип";
  /** Значение {@code Тип}, означающее «желудь как таковой». */
  public static final String BEAN_TYPE = "Желудь";
  /**
   * Имя экспортируемой функции прилепляемой коллекции — точка получения значения
   * для внедрения. Возвращаемый тип берётся из её bsldoc-описания.
   */
  public static final String ATTACHABLE_COLLECTION_GETTER = "Получить";
}
