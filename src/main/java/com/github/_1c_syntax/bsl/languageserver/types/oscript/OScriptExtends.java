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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnMetaAnnotationResolver;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
 *   // Определение аннотации &amp;ХранилищеСущностей:
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
   * Имя поля, которое библиотека {@code extends} неявно создаёт в собранном
   * объекте-наследнике для хранения экземпляра родителя. Доступно даже без
   * явного объявления поля с {@code &Родитель}.
   */
  public static final String IMPLICIT_PARENT_FIELD = "_ОбъектРодитель";

  /** Имена аннотации поля-держателя родителя (в нижнем регистре): {@code &Родитель}. */
  private static final Set<String> PARENT_FIELD_ANNOTATIONS = Set.of("родитель");

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

  /**
   * Имена интерфейсов, которые класс объявляет реализуемыми через
   * {@code &Реализует("...")} (с учётом мета-аннотаций). Имя интерфейса — то же,
   * что используется в {@code Новый Интерфейс}: qualifiedName library-класса или
   * basename файла.
   *
   * @param documentContext контекст {@code .os}-документа
   * @param metaResolver    резолвер мета-аннотаций «ОСени»
   * @return список имён реализуемых интерфейсов (возможно, пустой)
   */
  public static List<String> implementedInterfaceNames(DocumentContext documentContext,
                                                       AutumnMetaAnnotationResolver metaResolver) {
    if (documentContext.getFileType() != FileType.OS) {
      return List.of();
    }
    var result = new ArrayList<String>();
    for (var method : documentContext.getSymbolTree().getMethods()) {
      for (var value : metaResolver.valuesByRole(method.getAnnotations(), IMPLEMENTS_ROLE)) {
        if (value != null && !value.isBlank()) {
          result.add(value);
        }
      }
    }
    return result;
  }

  /**
   * Является ли {@code .os}-документ интерфейсом — несёт ли какой-либо его метод
   * (на практике — конструктор {@code ПриСозданииОбъекта}) аннотацию-маркер
   * {@code &Интерфейс} (с учётом мета-аннотаций).
   *
   * @param documentContext контекст {@code .os}-документа
   * @param metaResolver    резолвер мета-аннотаций «ОСени»
   * @return {@code true}, если документ — интерфейс
   */
  public static boolean isInterface(DocumentContext documentContext,
                                    AutumnMetaAnnotationResolver metaResolver) {
    if (documentContext.getFileType() != FileType.OS) {
      return false;
    }
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (metaResolver.hasRole(method.getAnnotations(), INTERFACE_ROLE)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Является ли переменная держателем экземпляра родителя: либо помечена
   * {@code &Родитель} (явный держатель с произвольным именем), либо это неявное
   * поле {@link #IMPLICIT_PARENT_FIELD}. Тип такого поля — родительский класс.
   *
   * @param variable переменная (как правило, поле модуля {@code .os}-класса)
   * @return {@code true}, если переменная хранит экземпляр родителя
   */
  public static boolean isParentHolder(VariableSymbol variable) {
    if (IMPLICIT_PARENT_FIELD.equalsIgnoreCase(variable.getName())) {
      return true;
    }
    for (Annotation annotation : variable.getAnnotations()) {
      if (PARENT_FIELD_ANNOTATIONS.contains(annotation.getName().toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

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
