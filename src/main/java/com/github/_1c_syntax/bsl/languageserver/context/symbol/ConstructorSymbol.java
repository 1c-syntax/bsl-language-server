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

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Символ конструктора OneScript-класса ({@code ПриСозданииОбъекта} /
 * {@code OnObjectCreate}).
 * <p>
 * Отдельный тип нужен, чтобы:
 * <ul>
 *   <li>отличать конструктор от обычных методов в symbol tree;</li>
 *   <li>иметь свой {@link SymbolKind#Constructor} и свой markup-builder
 *   без специальных условий в общей инфраструктуре hover'а;</li>
 *   <li>освободить ссылку на конструктор от {@code Exportable}-фильтра в
 *   {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex}:
 *   у OScript-класса конструктор по соглашению объявляется без {@code Экспорт},
 *   но фактически доступен снаружи через {@code Новый}.</li>
 * </ul>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class ConstructorSymbol extends AbstractMethodSymbol {

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Constructor;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitConstructor(this);
  }
}
