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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Optional;

/**
 * Восстанавливает символ неквалифицированного self-члена (реквизита/платформенного
 * метода self-типа модуля) по его имени и виду — для реконструкции {@code Reference}
 * из проиндексированного вхождения ({@code ReferenceIndex.buildReference}).
 * <p>
 * Реализация живёт в слое {@code types} и внедряется через Spring.
 */
public interface SelfMemberResolver {

  /**
   * Символ self-члена {@code name} вида {@code symbolKind}
   * ({@link SymbolKind#Property}/{@link SymbolKind#Method}) для self-типа модуля
   * документа {@code documentContext}, либо empty, если self-типа нет или член не
   * найден (например, конфигурационные типы ещё не зарегистрированы).
   *
   * @param documentContext документ, из которого происходит обращение.
   * @param symbolKind      вид члена: {@link SymbolKind#Method} для вызова,
   *                        {@link SymbolKind#Property} для голой ссылки.
   * @param name            имя члена (без учёта регистра, ru/en-написания).
   * @return символ найденного self-члена ({@code PlatformMemberSymbol}); empty, если не найден.
   */
  Optional<Symbol> resolveSelfMember(DocumentContext documentContext, SymbolKind symbolKind, String name);
}
