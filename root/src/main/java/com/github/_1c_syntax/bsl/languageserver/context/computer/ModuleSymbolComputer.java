/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import org.eclipse.lsp4j.SymbolKind;

import java.util.EnumSet;
import java.util.Set;

/**
 * Компьютер символа модуля документа.
 */
public class ModuleSymbolComputer implements Computer<ModuleSymbol> {

  private static final Set<ModuleType> MODULE_TYPES_TO_APPEND_NAME = EnumSet.of(
    ModuleType.ObjectModule,
    ModuleType.ManagerModule
  );

  private final DocumentContext documentContext;

  public ModuleSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public ModuleSymbol compute() {
    var firstRange = Ranges.getFirstSignificantTokenRange(documentContext.getTokens())
      .orElseGet(Ranges::create); // используем нулевую область

    return ModuleSymbol.builder()
      .name(getName(documentContext))
      .symbolKind(SymbolKind.Module)
      .owner(documentContext)
      .range(Ranges.create(documentContext.getAst()))
      .selectionRange(firstRange)
      .build();
  }

  private static String getName(DocumentContext documentContext) {
    String name = MdoRefBuilder.getMdoRef(documentContext);
    var moduleType = documentContext.getModuleType();
    if (MODULE_TYPES_TO_APPEND_NAME.contains(moduleType)) {
      name += "." + moduleType.name();
    }
    return name;
  }
}
