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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import org.springframework.stereotype.Component;

/**
 * Распознавание обходимых коллекций OneScript — классов, помеченных
 * аннотацией-маркером {@code &Обходимое} ({@code &Iterable}) на конструкторе
 * {@code ПриСозданииОбъекта}.
 * <p>
 * Это механизм <b>стандартной библиотеки самого OneScript</b> (не библиотеки
 * {@code extends}): движок оборачивает такой класс в {@code UserIterableContextInstance}
 * и поддерживает обход {@code Для Каждого … Цикл}, требуя у класса функцию
 * {@code ПолучитьИтератор()}. Так устроены, например, коллекции из библиотеки
 * <a href="https://github.com/sfaqer/collectionos">collectionos</a>.
 * <p>
 * Тип элемента такой коллекции в исходниках OneScript нигде не объявлен (итератор
 * возвращает нетипизированное значение), поэтому при регистрации в системе типов
 * элемент остаётся «любым» — как у платформенного {@code Массив}.
 */
@Component
public class OScriptIterable {

  /** Имя аннотации-маркера обходимой коллекции {@code &Обходимое} (рус.). */
  private static final String ITERABLE_ANNOTATION_RU = "Обходимое";
  /** Английский псевдоним аннотации {@code &Iterable}. */
  private static final String ITERABLE_ANNOTATION_EN = "Iterable";

  /**
   * Является ли {@code .os}-документ обходимой коллекцией — несёт ли его
   * конструктор {@code ПриСозданииОбъекта} аннотацию-маркер {@code &Обходимое}
   * ({@code &Iterable}). Такой класс регистрируется в системе типов как
   * коллекция, обходимая через {@code Для Каждого … Цикл} (тип элемента при
   * этом остаётся неизвестным — как у {@code Массив}).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return {@code true}, если документ — обходимая коллекция
   */
  public boolean isIterable(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return false;
    }
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (!Methods.isOscriptClassConstructorName(method.getName())) {
        continue;
      }
      for (Annotation annotation : method.getAnnotations()) {
        var name = annotation.getName();
        if (ITERABLE_ANNOTATION_RU.equalsIgnoreCase(name) || ITERABLE_ANNOTATION_EN.equalsIgnoreCase(name)) {
          return true;
        }
      }
    }
    return false;
  }
}
