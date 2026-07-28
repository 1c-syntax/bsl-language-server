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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш графов потока управления, разрезанный по URI документа.
 * <p>
 * Заполняется <b>лениво</b>: граф строится на первый запрос по блоку кода и остаётся
 * в кэше до события жизненного цикла документа. Один блок кода анализируют несколько
 * потребителей, и каждый повторный разбор — это полный обход поддерева операторов,
 * поэтому построение делится между ними.
 * <p>
 * Ключ — пара «блок кода + настройки построения». Блок сравнивается по
 * тождественности (у узлов разбора нет своего {@code equals}), то есть записи
 * действительны ровно для того дерева разбора, по которому построены. Настройки
 * входят в ключ, потому что графы с разными настройками различаются по структуре.
 * <p>
 * Выдаваемый граф <b>общий и предназначен только для чтения</b>: изменение его
 * структуры испортит данные остальным потребителям.
 * <p>
 * Инвалидация — per-URI через {@link AbstractDocumentLifecycleClearableIndex}:
 * изменение содержимого, освобождение вторичных данных, закрытие и удаление документа
 * удаляют весь бакет этого URI вместе со ссылками на узлы его дерева разбора.
 */
@Component
@WorkspaceScope
public class ControlFlowGraphIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<GraphKey, ControlFlowGraph>> graphsByUri = new ConcurrentHashMap<>();

  /**
   * Граф потока управления блока кода — из кэша либо построенный на месте.
   *
   * @param documentContext контекст документа, которому принадлежит блок кода.
   * @param codeBlock       блок кода: тело метода или код модуля.
   * @param options         настройки построения.
   * @return граф потока управления; общий экземпляр, изменять его нельзя.
   */
  public ControlFlowGraph graphOf(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext codeBlock,
    CfgBuildOptions options
  ) {
    return graphsByUri
      .computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
      .computeIfAbsent(
        new GraphKey(codeBlock, options),
        key -> key.options().buildGraph(key.codeBlock())
      );
  }

  /**
   * Удалить кэш по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    graphsByUri.remove(uri);
  }

  /**
   * Ключ кэша. Блок кода участвует в сравнении по тождественности — своего
   * {@code equals} у узлов дерева разбора нет.
   */
  private record GraphKey(BSLParser.CodeBlockContext codeBlock, CfgBuildOptions options) {
  }
}
