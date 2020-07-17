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

import com.github._1c_syntax.bsl.languageserver.utils.variable.values.V8NamedObject;

import java.util.HashMap;

@SuppressWarnings("uncheked")
public class NamedScope<T extends V8NamedObject> extends Scope<T> {

  private final HashMap<String, T> namedObject = new HashMap<>();

  public NamedScope(String name) {
    super(name);
  }

  public NamedScope() {
    super();
  }

  @Override
  public void addToScope(T variableDefinition) {
    super.addToScope(variableDefinition);
    this.namedObject.put(variableDefinition.getName(), variableDefinition);
  }

  @Override
  @SuppressWarnings("uncheked")
  public void fillFromScope(Scope<? extends T> anotherScope) {
    super.fillFromScope(anotherScope);
    if (anotherScope instanceof NamedScope) {
      this.namedObject.putAll(((NamedScope) anotherScope).namedObject);
    } else {
      anotherScope.objects.forEach(e ->
        this.namedObject.putIfAbsent(e.getName(), e));
    }
  }

  public T findByName(String variableName) {
    return namedObject.get(variableName);
  }

}
