/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.URI;

/**
 * Микробенчмарк создания {@link SymbolOccurrence} через фабрику {@link SymbolOccurrence#of}.
 * <p>
 * Параметр {@code shortBased} переключает координаты между укладывающимися в {@code short}
 * (короткая реализация, 32 байта) и выходящими за его пределы ({@code int}-реализация, 40 байт).
 * Запускать с {@code -prof gc} для сравнения аллокационной нагрузки (bytes/op) двух путей.
 */
@State(Scope.Benchmark)
public class SymbolOccurrenceCreate {

  private static final URI URI_VALUE = Absolute.uri("file:///module.bsl");
  private static final Symbol SYMBOL = Symbol.builder()
    .mdoRef("CommonModule.Модуль")
    .moduleType(ModuleType.CommonModule)
    .scopeName("")
    .symbolKind(SymbolKind.Method)
    .symbolName("метод")
    .build();

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
  public void createSymbolOccurrence(Blackhole bh) {
    var occurrence = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, URI_VALUE, range);
    bh.consume(occurrence);
  }

}
