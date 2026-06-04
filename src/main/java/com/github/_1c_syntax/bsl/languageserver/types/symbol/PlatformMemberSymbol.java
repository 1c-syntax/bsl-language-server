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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Symbol для члена платформенного / конфигурационного типа или для
 * глобальной функции/свойства, разрешённого через {@code TypeService.memberAt}.
 *
 * <p>Несёт в себе всё, что нужно построителю hover'а:
 * {@link MemberDescriptor} с сигнатурами/описаниями и арность вызова
 * на конкретной позиции (для выбора подходящей signature).
 */
@Getter
@EqualsAndHashCode(of = {"name", "owner"})
@RequiredArgsConstructor
public final class PlatformMemberSymbol implements Symbol {

  private final String name;
  /** {@code null} для глобальных функций/свойств без owner-типа. */
  private final @Nullable TypeRef owner;
  private final MemberDescriptor descriptor;
  /** Число фактических аргументов в текущем вызове; {@code -1} если не вызов. */
  private final int callArgCount;
  /**
   * Типы фактических аргументов в порядке вызова, для type-aware подбора
   * перегруженной сигнатуры в hover'е. Пустой список — для не-вызова или
   * если типы не удалось проинферить.
   */
  private final List<TypeSet> argTypes;

  @Override
  public SymbolKind getSymbolKind() {
    return descriptor.kind() == MemberKind.METHOD ? SymbolKind.Method : SymbolKind.Property;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    // synthetic — не участвует в обходе symbol-tree.
  }
}
