/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Range;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Optional;

@State(Scope.Benchmark)
public class VariableSymbolCreate {

  private Range range;

  @Param({"false", "true"})
  boolean shortBased;

  @Setup(Level.Trial)
  public void setup() {
    int line = shortBased ? 100 : 60_000;
    range = Ranges.create(line, 0, line, 1);
  }

  @Benchmark
  @Fork(value = 2, warmups = 2)
  @Warmup(time = 5, iterations = 3)
  public void createVariableSymbols(Blackhole bh) {
    var test = VariableSymbol.builder()
      .name("test")
      .owner(null)
      .range(range)
      .variableNameRange(range)
      .export(true)
      .kind(VariableKind.MODULE)
      .description(Optional.empty())
      .scope(null).build();

    bh.consume(test);
  }

}
