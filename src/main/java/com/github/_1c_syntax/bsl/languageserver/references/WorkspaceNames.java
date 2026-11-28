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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.FileType;

import java.net.URI;
import java.util.Optional;

/**
 * Имена рабочей области, известные вне документа, которые нужны индексатору ссылок: глобальный
 * контекст платформы, self-типы модулей и библиотеки OneScript.
 * <p>
 * Реализация живёт в слое {@code types} и внедряется через Spring — так индекс ссылок не
 * зависит от системы типов.
 */
public interface WorkspaceNames {

  /**
   * Есть ли у модуля документа self-тип (реквизиты и методы объекта, доступные без квалификатора).
   *
   * @param documentUri URI документа
   * @return {@code true}, если self-тип модуля зарегистрирован
   */
  boolean hasModuleType(URI documentUri);

  /**
   * Является ли имя глобальной функцией платформы.
   *
   * @param name     имя (без учёта регистра, ru/en-написания)
   * @param fileType тип файла, из которого идёт обращение
   * @return {@code true}, если такая глобальная функция есть
   */
  boolean isGlobalFunction(String name, FileType fileType);

  /**
   * Является ли имя глобальным свойством платформы.
   *
   * @param name     имя (без учёта регистра, ru/en-написания)
   * @param fileType тип файла, из которого идёт обращение
   * @return {@code true}, если такое глобальное свойство есть
   */
  boolean isGlobalProperty(String name, FileType fileType);

  /**
   * Файл класса подключённой библиотеки OneScript.
   *
   * @param className имя класса
   * @return URI файла класса; пусто, если такого класса нет
   */
  Optional<URI> libraryClassUri(String className);

  /**
   * Файл модуля подключённой библиотеки OneScript.
   *
   * @param moduleName имя модуля
   * @return URI файла модуля; пусто, если такого модуля нет
   */
  Optional<URI> libraryModuleUri(String moduleName);
}
