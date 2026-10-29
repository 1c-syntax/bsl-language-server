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
package com.github._1c_syntax.bsl.languageserver.context.compilation;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Контексты компиляции, различаемые инструкциями препроцессора 1С.
 * <p>
 * Контекст различает режим запуска и сторону компиляции. В файловом толстом клиенте
 * символы {@code Клиент} и {@code Сервер} истинны одновременно, в том числе в модуле
 * управляемой формы. При этом директивы методов продолжают различать клиентскую и серверную части.
 * <p>
 * Файловые варианты сведены к одному пользовательскому переключателю через
 * {@link #optionContext()}. Группы {@link #CLIENT_PREPROCESSOR}, {@link #SERVER_PREPROCESSOR}
 * и группы конкретных клиентов используются при оценке символов препроцессора.
 */
public enum ExecutionContext {
  /** Сервер клиент-серверного варианта. */
  SERVER,
  /** Тонкий клиент. */
  THIN_CLIENT,
  /** Веб-клиент. */
  WEB_CLIENT,
  /** Мобильный клиент приложения для платформы. */
  MOBILE_CLIENT,
  /** Толстый клиент, управляемое приложение, клиент-серверный вариант. */
  THICK_CLIENT_MANAGED,
  /** Толстый клиент, обычное приложение, клиент-серверный вариант. */
  THICK_CLIENT_ORDINARY,
  /** Внешнее соединение в клиент-серверном варианте. */
  EXTERNAL_CONNECTION,
  /** Клиентская часть приложения мобильной платформы. */
  MOBILE_APPLICATION_CLIENT,
  /** Серверная часть приложения мобильной платформы. */
  MOBILE_APPLICATION_SERVER,
  /** Мобильный автономный сервер; выбор проверяемых режимов задаётся отдельно. */
  MOBILE_STANDALONE_SERVER,

  /** Общий файловый контекст управляемого толстого клиента: доступны Клиент и Сервер. */
  THICK_CLIENT_MANAGED_FILE,
  /** Общий файловый контекст обычного толстого клиента: доступны Клиент и Сервер. */
  THICK_CLIENT_ORDINARY_FILE,
  /** Файловое внешнее соединение: доступны ВнешнееСоединение и Сервер. */
  EXTERNAL_CONNECTION_FILE,
  /** Клиентская часть управляемой формы или команды в файловом управляемом приложении. */
  THICK_CLIENT_MANAGED_FILE_CLIENT,
  /** Серверная часть управляемой формы или команды в файловом управляемом приложении. */
  THICK_CLIENT_MANAGED_FILE_SERVER,
  /** Клиентская часть управляемой формы или команды в файловом обычном приложении. */
  THICK_CLIENT_ORDINARY_FILE_CLIENT,
  /** Серверная часть управляемой формы или команды в файловом обычном приложении. */
  THICK_CLIENT_ORDINARY_FILE_SERVER;

  /** Все контексты модели; неизменяемое множество. */
  public static final Set<ExecutionContext> ALL = Collections.unmodifiableSet(EnumSet.allOf(ExecutionContext.class));
  /** Контексты проекта без поддержки файлового толстого клиента; внешнее соединение сохраняется. */
  public static final Set<ExecutionContext> WITHOUT_THICK_CLIENT_FILE =
    Collections.unmodifiableSet(EnumSet.copyOf(withoutThickClientFile()));
  /** Самостоятельные серверные контексты без совмещённых файловых режимов. */
  public static final Set<ExecutionContext> SERVERS =
    Collections.unmodifiableSet(EnumSet.of(SERVER, MOBILE_APPLICATION_SERVER, MOBILE_STANDALONE_SERVER));
  /** Управляемые клиенты до добавления файловых вариантов. */
  public static final Set<ExecutionContext> MANAGED_CLIENTS = Collections.unmodifiableSet(EnumSet.of(
    THIN_CLIENT, WEB_CLIENT, MOBILE_CLIENT, THICK_CLIENT_MANAGED, MOBILE_APPLICATION_CLIENT));
  /** Клиентские контексты исполнения, включая совмещённые файловые контексты общих модулей. */
  public static final Set<ExecutionContext> CLIENTS = Collections.unmodifiableSet(EnumSet.of(
    THIN_CLIENT, WEB_CLIENT, MOBILE_CLIENT, THICK_CLIENT_MANAGED, THICK_CLIENT_ORDINARY, MOBILE_APPLICATION_CLIENT,
    THICK_CLIENT_MANAGED_FILE, THICK_CLIENT_ORDINARY_FILE,
    THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_CLIENT));
  /** Серверные контексты исполнения, включая файловые серверные и общие части. */
  public static final Set<ExecutionContext> SERVER_CONTEXTS = Collections.unmodifiableSet(EnumSet.of(
    SERVER, MOBILE_APPLICATION_SERVER, MOBILE_STANDALONE_SERVER,
    THICK_CLIENT_MANAGED_FILE, THICK_CLIENT_ORDINARY_FILE, EXTERNAL_CONNECTION_FILE,
    THICK_CLIENT_MANAGED_FILE_SERVER, THICK_CLIENT_ORDINARY_FILE_SERVER));
  /** Символ Клиент в файловом толстом клиенте истинен и в серверной части управляемой формы. */
  public static final Set<ExecutionContext> CLIENT_PREPROCESSOR = Collections.unmodifiableSet(EnumSet.copyOf(withFileFormParts(CLIENTS)));
  /** Символ Сервер в файловом толстом клиенте истинен и в клиентской части управляемой формы. */
  public static final Set<ExecutionContext> SERVER_PREPROCESSOR =
    Collections.unmodifiableSet(EnumSet.copyOf(withFileFormParts(SERVER_CONTEXTS)));
  /** Все варианты символа ТолстыйКлиентУправляемоеПриложение. */
  public static final Set<ExecutionContext> THICK_MANAGED_PREPROCESSOR = Collections.unmodifiableSet(EnumSet.of(
    THICK_CLIENT_MANAGED, THICK_CLIENT_MANAGED_FILE,
    THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_MANAGED_FILE_SERVER));
  /** Все варианты символа ТолстыйКлиентОбычноеПриложение. */
  public static final Set<ExecutionContext> THICK_ORDINARY_PREPROCESSOR = Collections.unmodifiableSet(EnumSet.of(
    THICK_CLIENT_ORDINARY, THICK_CLIENT_ORDINARY_FILE,
    THICK_CLIENT_ORDINARY_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_SERVER));
  /** Оба варианта символа ВнешнееСоединение. */
  public static final Set<ExecutionContext> EXTERNAL_PREPROCESSOR = Collections.unmodifiableSet(EnumSet.of(
    EXTERNAL_CONNECTION, EXTERNAL_CONNECTION_FILE));

  /**
   * Контекст, чьим пользовательским переключателем управляется этот вариант.
   * <p>
   * Разделение файловой формы на части влияет на расчёт, но не создаёт новых параметров диагностики.
   *
   * @return базовый контекст для файлового варианта; {@code this} для остальных контекстов.
   */
  public ExecutionContext optionContext() {
    return switch (this) {
      case THICK_CLIENT_MANAGED_FILE, THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_MANAGED_FILE_SERVER
        -> THICK_CLIENT_MANAGED;
      case THICK_CLIENT_ORDINARY_FILE, THICK_CLIENT_ORDINARY_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_SERVER
        -> THICK_CLIENT_ORDINARY;
      case EXTERNAL_CONNECTION_FILE -> EXTERNAL_CONNECTION;
      default -> this;
    };
  }

  /**
   * Принадлежит ли контекст файловому варианту работы.
   *
   * @return {@code true} для общих файловых контекстов и обеих частей файловой формы/команды.
   */
  public boolean isFileMode() {
    return optionContext() != this;
  }

  /**
   * Принадлежит ли режим файловому толстому клиенту, включая обе части управляемой формы.
   *
   * @return {@code true} для файловых вариантов обычного и управляемого толстого клиента.
   */
  public boolean isThickClientFileMode() {
    return isFileMode() && (optionContext() == THICK_CLIENT_MANAGED || optionContext() == THICK_CLIENT_ORDINARY);
  }

  /**
   * Перейти от части файловой формы/команды к общему файловому контексту вызываемого модуля.
   *
   * @return соответствующий общий файловый контекст; {@code this}, если разделения на части нет.
   */
  public ExecutionContext sharedFileContext() {
    return switch (this) {
      case THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_MANAGED_FILE_SERVER -> THICK_CLIENT_MANAGED_FILE;
      case THICK_CLIENT_ORDINARY_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_SERVER -> THICK_CLIENT_ORDINARY_FILE;
      default -> this;
    };
  }

  /**
   * Создать изменяемую копию набора контекстов, в том числе пустого.
   *
   * @param contexts исходные контексты; повторяющиеся элементы объединяются.
   * @return новое {@link EnumSet}, независимое от изменений исходной коллекции.
   */
  public static Set<ExecutionContext> copyOf(Collection<ExecutionContext> contexts) {
    var result = EnumSet.noneOf(ExecutionContext.class);
    result.addAll(contexts);
    return result;
  }

  private static Set<ExecutionContext> withFileFormParts(Set<ExecutionContext> contexts) {
    var result = copyOf(contexts);
    result.addAll(EnumSet.of(THICK_CLIENT_MANAGED_FILE_CLIENT, THICK_CLIENT_MANAGED_FILE_SERVER,
      THICK_CLIENT_ORDINARY_FILE_CLIENT, THICK_CLIENT_ORDINARY_FILE_SERVER));
    return result;
  }

  /**
   * Найти контексты, отсутствующие в переданном наборе.
   *
   * @param contexts исключаемые контексты
   * @return новое изменяемое множество дополнения
   */
  public static Set<ExecutionContext> complementOf(Collection<ExecutionContext> contexts) {
    var result = EnumSet.allOf(ExecutionContext.class);
    result.removeAll(contexts);
    return result;
  }

  private static Set<ExecutionContext> withoutThickClientFile() {
    var result = EnumSet.allOf(ExecutionContext.class);
    result.removeIf(ExecutionContext::isThickClientFileMode);
    return result;
  }

}
