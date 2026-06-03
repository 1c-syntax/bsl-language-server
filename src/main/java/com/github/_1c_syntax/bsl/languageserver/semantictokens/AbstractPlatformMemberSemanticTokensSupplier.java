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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.util.Positions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Общая основа сапплаеров, подсвечивающих обращения к членам платформенных типов
 * (свойство через accessProperty, вызов метода через accessCall). Тип члена
 * резолвится через {@link TypeService#memberAt} — дорогой инференс, поэтому в
 * range-запросе он вызывается только для узлов, пересекающихся с запрошенным
 * диапазоном.
 * <p>
 * Подклассы определяют четыре точки расширения:
 * <ul>
 *   <li>{@link #ruleIndex()} — какой rule-узел AST обходить;</li>
 *   <li>{@link #nameRange(ParserRuleContext)} — диапазон имени члена в узле;</li>
 *   <li>{@link #skipFilter(DocumentContext)} — узлы, которые красит другой
 *       сапплаер (домен GlobalScope, source-defined вызовы) и которые нужно
 *       пропустить, чтобы не дублировать токен;</li>
 *   <li>{@link #emit(List, DocumentContext, Range)} — резолв члена и выдача токена
 *       нужного типа/модификаторов.</li>
 * </ul>
 *
 * @param <T> тип AST-узла обращения к члену
 */
@RequiredArgsConstructor
public abstract class AbstractPlatformMemberSemanticTokensSupplier<T extends ParserRuleContext>
  implements SemanticTokensSupplier {

  protected static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};

  protected final TypeService typeService;
  protected final SemanticTokensHelper helper;

  /** Индекс rule-узла AST для обхода (например {@code BSLParser.RULE_accessProperty}). */
  protected abstract int ruleIndex();

  /** Диапазон имени члена в узле; empty, если имени нет. */
  protected abstract Optional<Range> nameRange(T node);

  /**
   * Предикат «пропустить узел» — позиции, которые уже красит другой сапплаер.
   * Считается один раз на документ (можно предвычислить тяжёлое состояние).
   */
  protected abstract BiPredicate<T, Range> skipFilter(DocumentContext documentContext);

  /** Резолвит член в позиции и при успехе добавляет токен в {@code entries}. */
  protected abstract void emit(List<SemanticTokenEntry> entries, DocumentContext documentContext, Range range);

  @Override
  public final List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    return collect(documentContext, range -> true);
  }

  @Override
  public final List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext, Range range) {
    return collect(documentContext, nameRange -> overlaps(range, nameRange));
  }

  /**
   * Диапазон имени пересекается с запрошенным (частичное пересечение, как в
   * {@code SemanticTokensProvider.filterTokensByRange}): имя, начинающееся до
   * границы диапазона, но заходящее внутрь, не отбрасывается.
   */
  private static boolean overlaps(Range range, Range nameRange) {
    return Positions.isBefore(nameRange.getStart(), range.getEnd())
      && Positions.isBefore(range.getStart(), nameRange.getEnd());
  }

  private List<SemanticTokenEntry> collect(DocumentContext documentContext, Predicate<Range> inScope) {
    var entries = new ArrayList<SemanticTokenEntry>();
    var ast = documentContext.getAst();
    var skip = skipFilter(documentContext);

    for (T node : Trees.<T>findAllRuleNodes(ast, ruleIndex())) {
      nameRange(node)
        .filter(inScope)
        .filter(range -> !skip.test(node, range))
        .ifPresent(range -> emit(entries, documentContext, range));
    }

    return entries;
  }
}
