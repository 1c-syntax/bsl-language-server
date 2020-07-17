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
import com.github._1c_syntax.bsl.languageserver.utils.variable.values.V8NamedObject;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.ToString;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ToString(exclude = {"firstDeclaration"})
public class VariableDefinition implements V8NamedObject {

  private final String variablePath;
  private final Set<V8Type> types = new HashSet<>();
  private ParseTree firstDeclaration;

  public VariableDefinition(String variableName) {
    this.variablePath = variableName;
  }

  public static VariableDefinition fromLValue(BSLParser.LValueContext lValue) {
    var variable = new VariableDefinition(lValue.getText());
    variable.addDeclaration(lValue);
    return variable;
  }

  public Set<V8Type> getTypes() {
    return types;
  }

  public void addDeclaration(ParseTree firstDeclaration) {
    if (this.firstDeclaration == null) {
      this.firstDeclaration = firstDeclaration;
    }
  }

  public void clearTypes() {
    this.types.clear();
  }

  public void addAll(Collection<V8Type> newTypes) {
    this.types.addAll(newTypes);
  }
  public void replaceAll(Collection<V8Type> newTypes) {
    this.types.clear();
    this.types.addAll(newTypes);
  }
  public String getName() {
    return variablePath;
  }


}
