/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.utils.variable.scope;

import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8Type;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8TypeFromPresentationSupplier;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8TypeFromVariableSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProgramScope implements V8TypeFromPresentationSupplier, V8TypeFromVariableSupplier {
  private final HierarchicalScope<CodeFlowType> flowMode = new HierarchicalScope<>();
  private final HierarchicalNamedScope<VariableDefinition> variableScope = new HierarchicalNamedScope<>();
  private List<V8TypeFromPresentationSupplier> typeSuppliers = new ArrayList<>();

  public boolean codeFlowInConditionalBlock() {
    return flowMode.containsAnyOf(CodeFlowType.CYCLE, CodeFlowType.CONDITIONAL);
  }

  public boolean codeFlowInCycle() {
    return flowMode.contains(CodeFlowType.CYCLE);
  }

  public Optional<VariableDefinition> getVariableByName(String variableName) {
    return Optional.ofNullable(this.variableScope.findByName(variableName));
  }

  public VariableDefinition addVariable(VariableDefinition variableDefinition) {
    this.variableScope.addToScope(variableDefinition);
    return variableDefinition;
  }

  public void enterScope(String name) {
    variableScope.enterNamedSubScope(name);
    flowMode.enterSubScope(CodeFlowType.LINEAR);
  }

  public void leaveScope() {
    variableScope.leaveSubScope();
    flowMode.leaveSubScope();
  }

  public void enterFlowScope(CodeFlowType additionalMode) {
    flowMode.enterSubScope(additionalMode);
  }

  public void leaveFlowScope() {
    flowMode.leaveSubScope();
  }

  @Override
  public Optional<V8Type> getTypeFromPresentation(String presentation) {
    Optional<V8Type> started = Optional.empty();
    for (var typeSupplier : typeSuppliers) {
      started = typeSupplier.getTypeFromPresentation(presentation);
      if (started.isPresent()) {
        break;
      }
    }
    return started;
  }

  @Override
  public Optional<Set<V8Type>> getTypesFromVariable(String variableName) {
    return this.getVariableByName(variableName)
      .map(VariableDefinition::getTypes);
  }

  public List<V8TypeFromPresentationSupplier> getTypeSuppliers() {
    return typeSuppliers;
  }
}
