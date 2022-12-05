/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.commands;

import org.eclipse.lsp4j.Command;

import java.beans.Introspector;
import java.util.Optional;

public interface CommandSupplier<T extends CommandArguments> {

  default String getId() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith("CommandSupplier")) {
      simpleName = simpleName.substring(0, simpleName.length() - "CommandSupplier".length());
      simpleName = Introspector.decapitalize(simpleName);
    }

    return simpleName;
  }

  default Command createCommand(String title) {
    return new Command(title, getId());
  }

  Class<T> getCommandArgumentsClass();

  Optional<Object> execute(T arguments);
  
  default boolean refreshCodeLensesAfterExecuteCommand() {
    return false;
  }

}
