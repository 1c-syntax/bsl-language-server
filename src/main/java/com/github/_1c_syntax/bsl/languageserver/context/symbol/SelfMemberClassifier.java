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
 * Классифицирует голое (без {@code Перем}) присваивание имени как обращение к
 * self-реквизиту текущего модуля, а не к отдельной локальной переменной.
 * <p>
 * BSL резолвит локальную переменную по имени только при явном объявлении
 * ({@code Перем}/параметр). Присваивание одноимённому реквизиту объекта/набора
 * записей/менеджера без {@code Перем} НЕ создаёт теневую переменную — это
 * обращение к реквизиту. Поэтому {@code context.computer.VariableSymbolComputer}
 * спрашивает этот классификатор перед тем, как завести неявную
 * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind#DYNAMIC}-переменную:
 * если имя — self-реквизит, символ не заводится вовсе, и имя резолвит self-член
 * machinery: инференсер, {@code PlatformMemberReferenceFinder}, а также индексация
 * self-членов в {@code ReferenceIndexFiller} и их подсветка
 * {@code SymbolsSemanticTokensSupplier}.
 * <p>
 * Интерфейс — контракт слоя {@code context}; реальная реализация живёт в
 * {@code types} и внедряется через Spring: та же инверсия зависимости, что и у
 * {@code context.computer.DiagnosticComputer} (реализация — {@code
 * diagnostics.DefaultDiagnosticComputer}), — чтобы {@code context} не зависел от
 * {@code types} напрямую, хотя классификация по существу требует реестра типов.
 * <p>
 * Вызывается при построении дерева символов. Классификация решается по реквизитам
 * self-типа (конфигурационные и стандартные реквизиты объекта/набора записей/
 * менеджера), которые приходят из метаданных конфигурации, а не из {@code SymbolTree}
 * самого модуля; self-тип регистрируется ({@code
 * types.registry.ConfigurationTypesProvider}) сразу по добавлению workspace'а —
 * раньше, чем строится хоть одно дерево.
 * <p>
 * Оговорка про реентрантность: union членов self-типа включает и собственные
 * экспортные члены модуля ({@code ConfigurationModuleMembersProvider.collectModuleMembers}),
 * которые на перестройках читают {@code SymbolTree} этого же документа — то есть вызов
 * МОЖЕТ оказаться реентрантным к вычисляемому в этот момент дереву (вернётся предыдущее,
 * без взаимоблокировки). На решение это не влияет: экспортные методы имеют вид
 * {@code METHOD} и не проходят фильтр {@code PROPERTY}, а экспортные {@code Перем}-
 * переменные отсекаются проверкой объявленных переменных ещё до обращения к
 * классификатору (см. {@code VariableSymbolComputer#visitLValue}).
 */
public interface SelfMemberClassifier {

  /**
   * Является ли голое имя {@code name} обращением к self-реквизиту (свойству)
   * модуля документа {@code documentContext}.
   *
   * @param documentContext документ, в котором встречено имя.
   * @param name            имя (без учёта регистра, ru/en-написания).
   * @return {@code true}, если имя — self-реквизит текущего модуля.
   */
  boolean isBareSelfProperty(DocumentContext documentContext, String name);
}
