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

import lombok.ToString;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ToString
public class Scope<T>  {
  private final String name;
  protected Set<T> objects = new HashSet<>();

  public Scope(String name) {
    this.name = name;
  }
  public Scope() {
    this.name = "UnnamedScope";
  }
  public void addToScope(T scopedObject) {
    objects.add(scopedObject);
  }

  public void fillFromScope(Scope<? extends T> anotherScope) {
    this.objects.addAll(anotherScope.objects);
  }

  public String getName() {
    return name;
  }

  public boolean contains(T object){
    return objects.contains(object);
  }

  public boolean containsAnyOf(T ...objects){
    return !Collections.disjoint(this.objects, Arrays.asList(objects));
  }
  public boolean containsAnyOf(Collection<T> objects){
    return !Collections.disjoint(this.objects, objects);
  }
}
