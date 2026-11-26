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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Optional;

/**
 * Символ метода или функции.
 * <p>
 * Представляет метод или функцию в модуле BSL с информацией о параметрах,
 * аннотациях, экспортности и вложенных элементах.
 */
public interface MethodSymbol extends SourceDefinedSymbol, Exportable, Describable {
  String getName();

  boolean isFunction();

  @Override
  boolean isExport();

  boolean isDeprecated();

  /**
   * Объявлен ли метод с ключевым словом {@code Асинх} ({@code Async}).
   */
  boolean isAsync();

  List<ParameterDefinition> getParameters();

  @Override
  Optional<MethodDescription> getDescription();

  Optional<CompilerDirectiveKind> getCompilerDirectiveKind();

  List<Annotation> getAnnotations();

  /**
   * Диапазон имени метода/конструктора (без ключевых слов и параметров) —
   * используется как selection-range и для матчинга позиции к символу.
   */
  Range getSubNameRange();

  /**
   * Регион/область, в которой объявлен метод/конструктор, если он находится
   * непосредственно внутри {@link RegionSymbol}.
   */
  Optional<RegionSymbol> getRegion();
}
