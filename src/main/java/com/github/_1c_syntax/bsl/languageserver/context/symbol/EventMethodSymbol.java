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
 * Символ метода-обработчика платформенного события ({@code ПриЗаписи}, {@code ПередУдалением}
 * и т.п.).
 * <p>
 * Такой же полноценный {@link AbstractMethodSymbol}, как {@link RegularMethodSymbol} и {@link
 * ConstructorSymbol} — строится {@link com.github._1c_syntax.bsl.languageserver.context.computer.MethodSymbolComputer}
 * ПРИ ОБХОДЕ AST, а не пересобирается впоследствии: метод, чьё имя совпадает с контрактом события
 * owner-типа модуля, сразу регистрируется в дереве символов как {@code EventMethodSymbol}, никогда
 * не проходя через промежуточное состояние {@link RegularMethodSymbol}.
 * <p>
 * Классификацию (нужен доступ к {@code types.registry.EventHandlerResolver}/{@code TypeRegistry},
 * которых {@code context} видеть не вправе — см. {@code ArchitectureTest.layer_dependencies_are_respected})
 * выполняет {@link EventHandlerClassifier}, внедряемый в {@code MethodSymbolComputer} через
 * инверсию зависимости (тот же приём, что и у {@code context.computer.DiagnosticComputer}).
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class EventMethodSymbol extends AbstractMethodSymbol {

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Event;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitEventMethod(this);
  }
}
