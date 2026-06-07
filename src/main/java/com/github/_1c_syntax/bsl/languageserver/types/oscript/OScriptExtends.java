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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import lombok.experimental.UtilityClass;

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
 */
@UtilityClass
public class OScriptExtends {

  /**
   * Имена аннотации наследования (в нижнем регистре): русское {@code &Расширяет}
   * и английский псевдоним {@code &Extends}.
   */
  private static final Set<String> ANNOTATION_NAMES = Set.of("расширяет", "extends");

  /**
   * Имя родительского класса из аннотации {@code &Расширяет}/{@code &Extends}
   * над любым методом документа (на практике — над конструктором
   * {@code ПриСозданииОбъекта}).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return имя родителя или {@link Optional#empty()}, если файл не {@code .os}
   *         либо наследование не объявлено
   */
  public static Optional<String> parentClassName(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return Optional.empty();
    }
    for (MethodSymbol method : documentContext.getSymbolTree().getMethods()) {
      for (Annotation annotation : method.getAnnotations()) {
        if (!ANNOTATION_NAMES.contains(annotation.getName().toLowerCase(Locale.ROOT))) {
          continue;
        }
        var parent = firstStringParameter(annotation);
        if (parent.isPresent()) {
          return parent;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Первый строковый литерал-параметр аннотации. Имя родителя в
   * {@code &Расширяет("Родитель")} задаётся позиционно (параметр {@code Значение}).
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
