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
package com.github._1c_syntax.bsl.languageserver.utils.variable.values;

import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8BasicType;
import com.github._1c_syntax.bsl.languageserver.utils.variable.types.V8Type;

import java.util.Optional;

public enum V8BasicValue implements V8Value {
  TRUE(V8BasicType.BOOLEAN_TYPE), FALSE(V8BasicType.BOOLEAN_TYPE), NULL(V8BasicType.NULL_TYPE), UNDEFINED(V8BasicType.UNDEFINED_TYPE);

  private V8Type type;

  V8BasicValue(V8Type type) {
    this.type = type;
  }

  public static Optional<V8Value> fromStringLiteral(String literal) {
    return Optional.of(new StringValue(literal.substring(1, literal.length() - 1).replace("\"\"","\"")));
  }

  public static Optional<FloatValue> fromNumberLiteral(String literal) {
    return Optional.of(new FloatValue(Float.parseFloat(literal)));
  }

  @Override
  public V8Type getType() {
    return type;
  }

  @Override
  public Object getValue() {
    if(this.equals(TRUE)){
      return Boolean.TRUE;
    }else if(this.equals(FALSE)){
      return Boolean.FALSE;
    }
    return null;
  }

  @Override
  public void setValue(Object o) {

  }

  static class FloatValue implements V8Value<Float> {
    private Float value;

    public FloatValue(Float value) {
      this.value = value;
    }

    @Override
    public V8Type getType() {
      return V8BasicType.NUMBER_TYPE;
    }

    @Override
    public void setValue(Float s) {
      this.value = s;
    }

    @Override
    public Float getValue() {
        return value;
    }
  }

  static class V8TypeValue implements V8Value<V8Type> {
    private V8Type value;

    public V8TypeValue(V8Type value) {
      this.value = value;
    }

    @Override
    public V8Type getType() {
      return V8BasicType.TYPE_TYPE;
    }

    @Override
    public void setValue(V8Type s) {
      this.value = s;
    }

    @Override
    public V8Type getValue() {
      return value;
    }
  }

  public static class StringValue implements V8Value<String> {
    String value;

    StringValue(String literal) {
      value = literal;
    }

    @Override
    public V8Type getType() {
      return V8BasicType.STRING_TYPE;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public void setValue(String s) {
      this.value = s;
    }
  }
}
