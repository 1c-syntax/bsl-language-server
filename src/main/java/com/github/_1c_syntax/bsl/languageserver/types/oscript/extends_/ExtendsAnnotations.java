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
package com.github._1c_syntax.bsl.languageserver.types.oscript.extends_;

import lombok.experimental.UtilityClass;

/**
 * Имена аннотаций библиотеки наследования OneScript
 * <a href="https://github.com/nixel2007/extends">extends</a>.
 * <p>
 * Базовый набор имён; пользовательские аннотации, разворачивающиеся в них
 * через мета-аннотации, разрешаются отдельно.
 */
@UtilityClass
public class ExtendsAnnotations {

  /**
   * Базовая роль аннотации наследования {@code &Расширяет("ИмяРодителя")}
   * (ставится на конструктор класса-наследника).
   */
  public static final String EXTENDS_ROLE = "Расширяет";

  /**
   * Базовая роль аннотации реализации интерфейса {@code &Реализует("Интерфейс")}.
   * Аннотация повторяемая — класс может реализовывать несколько интерфейсов.
   */
  public static final String IMPLEMENTS_ROLE = "Реализует";

  /**
   * Базовая роль аннотации-маркера интерфейса {@code &Интерфейс} (ставится на
   * конструктор класса-интерфейса).
   */
  public static final String INTERFACE_ROLE = "Интерфейс";

  /** Аннотация поля-держателя экземпляра родителя {@code &Родитель}. */
  public static final String PARENT_FIELD = "Родитель";

  /**
   * Имя поля, которое библиотека {@code extends} неявно создаёт в собранном
   * объекте-наследнике для хранения экземпляра родителя. Доступно даже без
   * явного объявления поля с {@code &Родитель}.
   */
  public static final String IMPLICIT_PARENT_FIELD = "_ОбъектРодитель";
}
