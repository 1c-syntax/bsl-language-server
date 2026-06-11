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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations;

import lombok.experimental.UtilityClass;

/**
 * Имена служебных аннотаций и параметров движка пользовательских аннотаций
 * OneScript (<a href="https://github.com/autumn-library/annotations">annotations</a>).
 */
@UtilityClass
public class OScriptAnnotations {

  /** Маркер класса-определения пользовательской аннотации ({@code &Аннотация("Имя")}). */
  public static final String ANNOTATION_MARKER = "Аннотация";
  /**
   * Псевдоним параметра конструктора композитной аннотации для параметра вложенной
   * мета-аннотации (annotations 1.5+, аналог Spring {@code @AliasFor}). Помечает
   * параметр {@code ПриСозданииОбъекта}: его значение переносится в указанную
   * мета-аннотацию при разворачивании.
   */
  public static final String ALIAS_FOR = "ПсевдонимДля";
  /** Параметр {@code &ПсевдонимДля} — имя целевой мета-аннотации. */
  public static final String ALIAS_TARGET_ANNOTATION = "Аннотация";
  /** Параметр {@code &ПсевдонимДля} — имя параметра целевой мета-аннотации. */
  public static final String ALIAS_TARGET_PARAMETER = "Параметр";
  /** Параметр {@code &ПсевдонимДля} — переносить ли значение по умолчанию (по умолчанию {@code Ложь}). */
  public static final String ALIAS_TRANSFER_DEFAULT = "ПереноситьЗначениеПоУмолчанию";
  /** Булев литерал {@code Истина}. */
  public static final String TRUE_LITERAL = "Истина";
  /** Первый параметр большинства аннотаций — имя/значение. */
  public static final String VALUE_PARAMETER = "Значение";
}
