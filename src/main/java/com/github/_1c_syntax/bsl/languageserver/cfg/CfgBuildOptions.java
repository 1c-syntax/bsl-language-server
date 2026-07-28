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
package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.parser.BSLParser;

/**
 * Набор настроек построения графа потока управления.
 * <p>
 * Значение неизменяемое и пригодно как часть ключа кэша: графы, построенные с разными
 * настройками, различаются по структуре и не взаимозаменяемы.
 * <p>
 * Собранный отсюда построитель получает все три настройки явно, поэтому набор задаёт
 * структуру графа целиком, независимо от умолчаний {@link CfgBuildingParseTreeVisitor}.
 *
 * @param loopIterations          строить рёбра повторной итерации циклов
 *                                ({@link CfgEdgeType#LOOP_ITERATION}).
 * @param preprocessorConditions  строить вершины условий препроцессора
 *                                ({@link PreprocessorConditionVertex}).
 * @param adjacentDeadCode        помечать рёбрами {@link CfgEdgeType#ADJACENT_CODE} код,
 *                                идущий следом за недостижимым.
 */
public record CfgBuildOptions(
  boolean loopIterations,
  boolean preprocessorConditions,
  boolean adjacentDeadCode
) {

  private static final CfgBuildOptions DEFAULTS = new CfgBuildOptions(true, true, false);

  /**
   * Настройки по умолчанию: рёбра итераций циклов и условия препроцессора строятся,
   * соседний с недостижимым код не помечается.
   *
   * @return набор настроек по умолчанию.
   */
  public static CfgBuildOptions defaults() {
    return DEFAULTS;
  }

  /**
   * Копия набора с изменённым построением рёбер повторной итерации циклов.
   *
   * @param enable строить ли рёбра повторной итерации.
   * @return новый набор настроек.
   */
  public CfgBuildOptions withLoopIterations(boolean enable) {
    return new CfgBuildOptions(enable, preprocessorConditions, adjacentDeadCode);
  }

  /**
   * Копия набора с изменённым построением вершин условий препроцессора.
   *
   * @param enable строить ли вершины условий препроцессора.
   * @return новый набор настроек.
   */
  public CfgBuildOptions withPreprocessorConditions(boolean enable) {
    return new CfgBuildOptions(loopIterations, enable, adjacentDeadCode);
  }

  /**
   * Копия набора с изменённой пометкой кода, соседнего с недостижимым.
   *
   * @param enable помечать ли соседний код.
   * @return новый набор настроек.
   */
  public CfgBuildOptions withAdjacentDeadCode(boolean enable) {
    return new CfgBuildOptions(loopIterations, preprocessorConditions, enable);
  }

  /**
   * Построить граф потока управления блока кода с этими настройками.
   *
   * @param codeBlock блок кода: тело метода или код модуля.
   * @return построенный граф.
   */
  public ControlFlowGraph buildGraph(BSLParser.CodeBlockContext codeBlock) {
    var builder = new CfgBuildingParseTreeVisitor();
    builder.produceLoopIterations(loopIterations);
    builder.producePreprocessorConditions(preprocessorConditions);
    builder.determineAdjacentDeadCode(adjacentDeadCode);
    return builder.buildGraph(codeBlock);
  }
}
