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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;

/**
 * Классифицирует объявленный в коде метод как обработчик платформенного события.
 * <p>
 * Интерфейс — контракт {@code context.computer.MethodSymbolComputer}, реальная реализация живёт
 * в {@code types.registry} ({@code EventHandlerResolver}) и внедряется через Spring: та же
 * инверсия зависимости, что и у {@code context.computer.DiagnosticComputer} (реализация —
 * {@code diagnostics.DefaultDiagnosticComputer}) — так {@code context} не зависит от {@code types}
 * напрямую (см. {@code ArchitectureTest.layer_dependencies_are_respected}), хотя классификация по
 * существу требует доступа к реестру типов платформы/конфигурации.
 * <p>
 * Безопасно вызывать в любой момент жизни воркспейса: типы платформы/конфигурации регистрируются
 * ({@code types.registry.ConfigurationTypesProvider}) сразу по добавлению workspace'а — раньше,
 * чем {@code ServerContext.populateContext()} успевает построить хоть одно дерево символов.
 */
public interface EventHandlerClassifier {

  /**
   * Является ли метод с именем {@code methodName} обработчиком платформенного события owner-типа
   * модуля {@code documentContext}.
   */
  boolean isEventHandler(DocumentContext documentContext, String methodName);
}
