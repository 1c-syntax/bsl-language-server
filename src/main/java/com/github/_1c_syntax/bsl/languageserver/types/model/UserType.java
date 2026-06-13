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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;

import java.lang.ref.WeakReference;
import java.util.Optional;

/**
 * Тип, объявленный в пользовательских исходниках: OneScript-класс,
 * общий модуль (как namespace-тип) и т.п.
 * <p>
 * Хранит {@link WeakReference} на исходный символ, чтобы не удерживать
 * символы живыми вне жизненного цикла {@code ServerContext}.
 *
 * @param ref         ссылка на тип в реестре типов
 * @param declaration слабая ссылка на символ-источник, объявивший тип
 */
public record UserType(TypeRef ref, WeakReference<SourceDefinedSymbol> declaration) implements Type {

  public UserType(TypeRef ref, SourceDefinedSymbol symbol) {
    this(ref, new WeakReference<>(symbol));
  }

  /**
   * @return исходный символ, если ещё доступен через слабую ссылку
   */
  public Optional<SourceDefinedSymbol> getDeclaration() {
    return Optional.ofNullable(declaration.get());
  }
}
