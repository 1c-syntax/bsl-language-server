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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParserBaseVisitor;
import lombok.Getter;
import lombok.Setter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Diagnostic;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractSDBLVisitorDiagnostic extends SDBLParserBaseVisitor<ParseTree> implements BSLDiagnostic {
  /**
   * Виртуальные таблицы, которые есть у объектов каждого вида.
   */
  private static final Map<Integer, Set<Integer>> VIRTUAL_TABLES_BY_MDO_TYPE = Map.of(
    SDBLParser.INFORMATION_REGISTER_TYPE, Set.of(
      SDBLParser.SLICELAST_VT,
      SDBLParser.SLICEFIRST_VT
    ),
    SDBLParser.ACCUMULATION_REGISTER_TYPE, Set.of(
      SDBLParser.BALANCE_VT,
      SDBLParser.TURNOVERS_VT,
      SDBLParser.BALANCE_AND_TURNOVERS_VT
    ),
    SDBLParser.ACCOUNTING_REGISTER_TYPE, Set.of(
      SDBLParser.BALANCE_VT,
      SDBLParser.TURNOVERS_VT,
      SDBLParser.BALANCE_AND_TURNOVERS_VT,
      SDBLParser.EXT_DIMENSIONS_VT,
      SDBLParser.RECORDS_WITH_EXT_DIMENSIONS_VT,
      SDBLParser.DR_CR_TURNOVERS_VT
    ),
    SDBLParser.CALCULATION_REGISTER_TYPE, Set.of(
      SDBLParser.ACTUAL_ACTION_PERIOD_VT,
      SDBLParser.SCHEDULE_DATA_VT
    ),
    SDBLParser.SEQUENCE_TYPE, Set.of(SDBLParser.BOUNDARIES_VT),
    SDBLParser.TASK_TYPE, Set.of(SDBLParser.TASK_BY_PERFORMER_VT)
  );

  @Getter
  @Setter
  protected DiagnosticInfo info;
  protected final DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);
  protected DocumentContext documentContext;

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    this.documentContext = documentContext;
    diagnosticStorage.clearDiagnostics();
    var queries = documentContext.getQueries();
    if (!queries.isEmpty()) {
      queries.forEach(sdblTokenizer -> this.visitQueryPackage(sdblTokenizer.getAst()));
    }

    return diagnosticStorage.getDiagnostics();
  }

  @Override
  public ParseTree visitQueryPackage(SDBLParser.QueryPackageContext ctx) {
    if (Trees.treeContainsErrors(ctx)) {
      return ctx;
    }
    return super.visitQueryPackage(ctx);
  }

  /**
   * Проверяет, что источник, разобранный грамматикой как виртуальная таблица, действительно ею является.
   * <p>
   * Грамматика принимает за виртуальную таблицу любое обращение вида {@code <Вид>.<Имя>.<ИмяВиртуальнойТаблицы>},
   * поэтому табличная часть, названная как виртуальная таблица регистра ({@code Справочник.Партии.Остатки}),
   * тоже попадает в это правило. Виртуальной таблицей считается только сочетание вида объекта и таблицы,
   * которое есть у платформы. Критерий отбора виртуальной таблицей считается всегда.
   *
   * @param ctx узел виртуальной таблицы
   * @return {@code true}, если у объекта такого вида есть виртуальная таблица с таким именем
   */
  protected static boolean isVirtualTable(SDBLParser.VirtualTableContext ctx) {
    if (ctx.type != null) {
      return true;
    }
    if (ctx.mdo() == null || ctx.mdo().type == null || ctx.virtualTableName == null) {
      return false;
    }
    return VIRTUAL_TABLES_BY_MDO_TYPE
      .getOrDefault(ctx.mdo().type.getType(), Set.of())
      .contains(ctx.virtualTableName.getType());
  }

}
