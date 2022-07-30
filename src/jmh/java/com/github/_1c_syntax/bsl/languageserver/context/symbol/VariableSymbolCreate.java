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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Optional;
import java.util.Random;

@State(Scope.Benchmark)
public class VariableSymbolCreate {

  private static final Random RANDOM = new Random();

  private Range range;
  private Range variableNameRange;

//  @Param("shortBased")
  boolean shortBased;

  @Setup(Level.Invocation)
  public void setup() {
    var line = RANDOM.nextInt(60_000);
    range = Ranges.create(line, 0, line, 1);

    line = RANDOM.nextInt(60_000);
    variableNameRange = Ranges.create(line, 0, line, 1);

    var start = range.getStart();
    var end = range.getEnd();
    var variableNameRangeStart = variableNameRange.getStart();
    var variableNameRangeEnd = variableNameRange.getEnd();

    shortBased = start.getLine() <= Short.MAX_VALUE
      && end.getLine() <= Short.MAX_VALUE
      && start.getCharacter() <= Short.MAX_VALUE
      && end.getCharacter() <= Short.MAX_VALUE
      && variableNameRangeStart.getLine() <= Short.MAX_VALUE
      && variableNameRangeStart.getCharacter() <= Short.MAX_VALUE
      && variableNameRangeEnd.getCharacter() <= Short.MAX_VALUE;
  }

  @Benchmark
  @Fork(value = 2, warmups = 2)
  @Warmup(time = 5, iterations = 3)
  public void createVariableSymbols(Blackhole bh) {
    var test = getVariableSymbolBuilder().build();

    bh.consume(test);
  }

  @Benchmark
  @Fork(value = 2, warmups = 2)
  @Warmup(time = 5, iterations = 3)
  public void createVariableSymbolsInt(Blackhole bh) {
    var test = getVariableSymbolBuilder().buildInt();

    bh.consume(test);
  }

  private VariableSymbolBuilder getVariableSymbolBuilder() {
    return VariableSymbol.builder()
      .name("test")
      .owner(null)
      .range(range)
      .variableNameRange(variableNameRange)
      .export(true)
      .kind(VariableKind.MODULE)
      .symbolKind(SymbolKind.Variable)
      .description(Optional.empty())
      .scope(null);
  }

}
