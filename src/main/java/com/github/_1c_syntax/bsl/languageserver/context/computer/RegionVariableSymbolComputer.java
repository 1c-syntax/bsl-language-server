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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Range;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Вычислитель символов переменных и регионов за <b>один</b> обход AST.
 * <p>
 * И сбор переменных ({@link VariableSymbolComputer}), и сбор регионов обходили всё дерево вглубь
 * (методы — вместе с телами), и при перестроении дерева символов на каждый keystroke это были два
 * отдельных полных обхода 48k-строчного модуля (по профилю набора текста на больших модулях обход
 * визиторами — заметная доля стоимости перестроения). Наборы перекрытых узлов у них не пересекаются
 * (директивы {@code #Область}/{@code #КонецОбласти} против объявлений переменных/параметров/lvalue/
 * циклов), поэтому сбор регионов добавляется поверх обхода переменных, и два полных обхода
 * схлопываются в один.
 * <p>
 * {@link VariableSymbolComputer} остаётся самостоятельным (используется отдельно), а этот класс лишь
 * расширяет его сбором регионов в том же {@code visitFile}.
 */
public final class RegionVariableSymbolComputer extends VariableSymbolComputer {

  private final DocumentContext documentContext;
  private final Deque<Pair<RegionSymbol.RegionSymbolBuilder, BSLParser.RegionStartContext>> regionStack =
    new ArrayDeque<>();
  private final Set<RegionSymbol> regions = new HashSet<>();

  public RegionVariableSymbolComputer(DocumentContext documentContext,
                                      ModuleSymbol module,
                                      List<? extends MethodSymbol> methods) {
    super(documentContext, module, methods);
    this.documentContext = documentContext;
  }

  @Override
  public List<VariableSymbol> compute() {
    regionStack.clear();
    regions.clear();

    // Единственный обход дерева: попутно с переменными собираем регионы (см. visitRegion*).
    var variables = super.compute();

    regionStack.clear();
    return variables;
  }

  /**
   * Собранные за тот же обход регионы. Вызывать после {@link #compute()}.
   *
   * @return список символов регионов модуля.
   */
  public List<RegionSymbol> getRegions() {
    return new ArrayList<>(regions);
  }

  @Override
  public ParseTree visitRegionStart(BSLParser.RegionStartContext ctx) {
    RegionSymbol.RegionSymbolBuilder builder = RegionSymbol.builder()
      .owner(documentContext)
      .name(ctx.regionName().getText().intern())
      .regionNameRange(Ranges.create(ctx.regionName()))
      .startRange(Ranges.create(ctx));

    regionStack.push(Pair.of(builder, ctx));
    return super.visitRegionStart(ctx);
  }

  @Override
  public ParseTree visitRegionEnd(BSLParser.RegionEndContext ctx) {
    if (regionStack.isEmpty()) {
      return super.visitRegionEnd(ctx);
    }

    var pair = regionStack.pop();

    RegionSymbol.RegionSymbolBuilder builder = pair.getLeft();
    BSLParser.RegionStartContext regionStartContext = pair.getRight();

    Range range = Ranges.create(regionStartContext, ctx);
    builder
      .range(range)
      .endRange(Ranges.create(ctx));

    regions.add(builder.build());

    return super.visitRegionEnd(ctx);
  }
}
