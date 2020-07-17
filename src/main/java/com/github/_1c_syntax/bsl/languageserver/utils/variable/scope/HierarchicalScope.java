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

import java.util.ArrayDeque;
import java.util.Deque;

public class HierarchicalScope<T> extends Scope<T> {
  private final Deque<Scope<T>> scope = new ArrayDeque<>();

  public HierarchicalScope(String name) {
    super(name);
    scope.push(this);
  }

  public HierarchicalScope() {
    super();
    scope.push(this);
  }

  public Scope<T> current() {
    return scope.peek();
  }

  public void enterSubScope(T object) {
    Scope<T> newScope = new Scope<>();
    newScope.fillFromScope(current());
    newScope.addToScope(object);
    scope.push(newScope);
  }

  public void enterNamedScope(String nameOfScope) {
    Scope<T> newScope = new Scope<>(nameOfScope);
    newScope.fillFromScope(current());
    scope.push(newScope);
  }

  public void leaveSubScope() {
    scope.pop();
  }

  @Override
  public boolean contains(T object) {
    if (current().equals(this)) {
      return super.contains(object);
    } else {
      return current().contains(object);
    }

  }

  @SafeVarargs
  @Override
  public final boolean containsAnyOf(T... objects) {
    if (current().equals(this)) {
      return super.containsAnyOf(objects);
    } else {
      return current().containsAnyOf(objects);
    }
  }
}
