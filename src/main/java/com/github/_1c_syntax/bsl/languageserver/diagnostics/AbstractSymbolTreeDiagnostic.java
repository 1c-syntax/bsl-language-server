/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;

import java.util.List;

public abstract class AbstractSymbolTreeDiagnostic extends AbstractDiagnostic implements SymbolTreeVisitor {

  @Override
  protected void check() {
    visit(documentContext.getSymbolTree().getModule());
  }

  void visitChildren(List<SourceDefinedSymbol> children) {
    children.forEach(this::visit);
  }

  void visit(SourceDefinedSymbol symbol) {
    symbol.accept(this);
  }

  @Override
  public void visitModule(ModuleSymbol module) {
    visitChildren(module.getChildren());
  }

  @Override
  public void visitRegion(RegionSymbol region) {
    visitChildren(region.getChildren());
  }

  @Override
  public void visitMethod(MethodSymbol method) {
    visitChildren(method.getChildren());
  }

  @Override
  public void visitVariable(VariableSymbol variable) {
    visitChildren(variable.getChildren());
  }
}
