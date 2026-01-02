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

/**
 * Посетитель дерева символов.
 * <p>
 * Интерфейс для обхода иерархии символов модуля с использованием паттерна Visitor.
 * Позволяет обрабатывать различные типы символов (модуль, регионы, методы, переменные).
 */
public interface SymbolTreeVisitor {
  /**
   * Посетить символ модуля.
   *
   * @param module Символ модуля
   */
  void visitModule(ModuleSymbol module);

  /**
   * Посетить символ региона/области.
   *
   * @param region Символ региона
   */
  void visitRegion(RegionSymbol region);

  /**
   * Посетить символ метода.
   *
   * @param method Символ метода
   */
  void visitMethod(MethodSymbol method);

  /**
   * Посетить символ переменной.
   *
   * @param variable Символ переменной
   */
  void visitVariable(VariableSymbol variable);
}
