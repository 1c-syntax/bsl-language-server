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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventHandlerClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier;
import com.github._1c_syntax.utils.StringInterner;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Фабрика контекстов документов.
 * <p>
 * Передаёт создаваемым {@link DocumentContext} общие для всех документов сервисы.
 */
@Component
public class DocumentContextFactory {

  private final DocumentContext.Dependencies dependencies;

  /**
   * @param diagnosticComputer        вычислитель диагностик
   * @param oScriptModuleTypeResolver определитель типа модуля OneScript-файлов
   * @param selfMemberClassifier      классификатор обращений к собственным членам модуля
   * @param eventHandlerClassifier    классификатор обработчиков событий
   * @param stringInterner            интернер строк
   */
  public DocumentContextFactory(
    DiagnosticComputer diagnosticComputer,
    OScriptModuleTypeResolver oScriptModuleTypeResolver,
    SelfMemberClassifier selfMemberClassifier,
    EventHandlerClassifier eventHandlerClassifier,
    StringInterner stringInterner
  ) {
    this.dependencies = new DocumentContext.Dependencies(
      diagnosticComputer,
      oScriptModuleTypeResolver,
      selfMemberClassifier,
      eventHandlerClassifier,
      stringInterner
    );
  }

  /**
   * Создать контекст документа.
   *
   * @param uri           URI документа
   * @param serverContext контекст рабочей области, которой принадлежит документ
   * @return новый контекст документа
   */
  public DocumentContext create(URI uri, ServerContext serverContext) {
    return new DocumentContext(uri, serverContext, dependencies);
  }
}
