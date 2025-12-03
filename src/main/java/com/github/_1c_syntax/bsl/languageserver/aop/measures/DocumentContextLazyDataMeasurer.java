/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.aop.measures;

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Вычислитель замеров производительности для ленивых данных DocumentContext.
 * <p>
 * Автоматически измеряет время вычисления ленивых методов DocumentContext
 * при изменении содержимого документа для мониторинга производительности.
 */
@Component
@ConditionalOnMeasuresEnabled
@RequiredArgsConstructor
@Slf4j
public class DocumentContextLazyDataMeasurer {

  private final MeasureCollector measureCollector;

  /**
   * Обработчик события {@link DocumentContextContentChangedEvent}. Вызывает основную логику выполнения замеров.
   *
   * @param event Событие
   */
  @EventListener
  @Order
  @SneakyThrows
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();

    LOGGER.debug("Take measurements for {}", documentContext.getUri());

    measureCollector.measureIt(documentContext::getQueries, "context: queries");
    for (SDBLTokenizer sdblTokenizer : documentContext.getQueries()) {
      measureCollector.measureIt(sdblTokenizer::getAst, "context: queryAst");
    }
    measureCollector.measureIt(documentContext.getSymbolTree()::getChildrenFlat, "context: symbolTree - childrenFlat");
    measureCollector.measureIt(documentContext.getSymbolTree()::getMethods, "context: symbolTree - methods");
    measureCollector.measureIt(documentContext.getSymbolTree()::getVariables, "context: symbolTree - variables");
    measureCollector.measureIt(documentContext.getSymbolTree()::getVariablesByName, "context: symbolTree - variablesByName");
    measureCollector.measureIt(documentContext.getSymbolTree()::getMethodsByName, "context: symbolTree - methodsByName");
    measureCollector.measureIt(documentContext::getDiagnosticIgnorance, "context: diagnosticIgnorance");
    measureCollector.measureIt(documentContext::getCognitiveComplexityData, "context: cognitiveComplexity");
    measureCollector.measureIt(documentContext::getCyclomaticComplexityData, "context: cyclomaticComplexity");
    measureCollector.measureIt(documentContext::getMetrics, "context: metrics");
  }
}
