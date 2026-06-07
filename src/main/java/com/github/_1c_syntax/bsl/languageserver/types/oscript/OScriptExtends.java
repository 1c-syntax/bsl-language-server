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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnMetaAnnotationResolver;
import lombok.experimental.UtilityClass;

import java.util.Optional;

/**
 * Поддержка библиотеки наследования OneScript
 * <a href="https://github.com/oscript-library/extends">extends</a> (автор — nixel2007).
 * <p>
 * Наследование объявляется аннотацией {@code &Расширяет("ИмяРодителя")}
 * (или её английским псевдонимом {@code &Extends}) над конструктором класса
 * {@code ПриСозданииОбъекта}:
 * <pre>
 *   &amp;Расширяет("Родитель")
 *   Процедура ПриСозданииОбъекта()
 *   КонецПроцедуры
 * </pre>
 * Имя родителя — то же, что используется в {@code Новый Родитель}: для
 * библиотечного класса это его {@code qualifiedName} из {@code lib.config},
 * для обычного {@code .os}-файла — basename.
 * <p>
 * Наследование распознаётся не только при прямом использовании {@code &Расширяет},
 * но и через <b>мета-аннотации</b> фреймворка «ОСень»: пользовательская аннотация,
 * определённая классом с {@code &Аннотация("Имя")} и {@code &Расширяет("Родитель")},
 * означает, что любой класс, помеченный {@code &Имя(...)}, наследует
 * {@code Родитель}. Так устроена, например, библиотека
 * <a href="https://github.com/autumn-library/autumn-data">autumn-data</a>:
 * <pre>
 *   // Определение аннотации &ХранилищеСущностей:
 *   &amp;Аннотация("ХранилищеСущностей")
 *   &amp;Расширяет("ХранилищеСущностей")
 *   &amp;Желудь
 *   Процедура ПриСозданииОбъекта(Значение, ИсточникДанных = "")
 *
 *   // Использование — класс получает члены ХранилищеСущностей (ПолучитьОдно и т.п.):
 *   &amp;ХранилищеСущностей("Справочник")
 *   Процедура ПриСозданииОбъекта()
 * </pre>
 * Разворачивание мета-аннотаций делегируется {@link AutumnMetaAnnotationResolver}
 * (роль {@link #EXTENDS_ROLE}).
 */
@UtilityClass
public class OScriptExtends {

  /**
   * Базовая роль аннотации наследования (имя русской аннотации extends).
   * Через неё {@link AutumnMetaAnnotationResolver} распознаёт и прямое
   * {@code &Расширяет("X")}, и мета-аннотации, разворачивающиеся в неё.
   */
  public static final String EXTENDS_ROLE = "Расширяет";

  /** Английский псевдоним аннотации наследования (в нижнем регистре). */
  private static final String ENGLISH_ANNOTATION = "extends";

  /**
   * Имя родительского класса для документа {@code .os}, объявленное аннотацией
   * наследования над любым методом (на практике — над конструктором
   * {@code ПриСозданииОбъекта}). Учитывает мета-аннотации (см. класс-уровневую
   * документацию).
   *
   * @param documentContext контекст {@code .os}-документа
   * @param metaResolver    резолвер мета-аннотаций «ОСени»
   * @return имя родителя или {@link Optional#empty()}, если файл не {@code .os}
   *         либо наследование не объявлено
   */
  public static Optional<String> parentClassName(DocumentContext documentContext,
                                                 AutumnMetaAnnotationResolver metaResolver) {
    if (documentContext.getFileType() != FileType.OS) {
      return Optional.empty();
    }
    for (var method : documentContext.getSymbolTree().getMethods()) {
      var annotations = method.getAnnotations();
      // Роль "Расширяет" покрывает прямую &Расширяет("X") и мета-аннотации
      // («ОСень»), разворачивающиеся в неё (например, &ХранилищеСущностей из autumn-data).
      for (var value : metaResolver.valuesByRole(annotations, EXTENDS_ROLE)) {
        if (value != null && !value.isBlank()) {
          return Optional.of(value);
        }
      }
      // Английский псевдоним &Extends("X") (если у роли нет класса-определения).
      for (Annotation annotation : annotations) {
        if (ENGLISH_ANNOTATION.equalsIgnoreCase(annotation.getName())) {
          var parent = firstStringParameter(annotation);
          if (parent.isPresent()) {
            return parent;
          }
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Первый строковый литерал-параметр аннотации. Имя родителя в
   * {@code &Extends("Родитель")} задаётся позиционно.
   */
  private static Optional<String> firstStringParameter(Annotation annotation) {
    for (var parameter : annotation.getParameters()) {
      if (parameter.value().isLeft()) {
        var value = parameter.value().getLeft();
        if (value != null && !value.isBlank()) {
          return Optional.of(value);
        }
      }
    }
    return Optional.empty();
  }
}
