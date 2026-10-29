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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Контексты компиляции прикладного метода и возможность серверного перехода в его hover.
 * Данные берутся из общей модели компиляции и сохранённого условия объявления; AST не читается.
 */
@Component
@RequiredArgsConstructor
public class MethodAvailabilityRenderer {
  private final CompilationContextResolver contextResolver;
  private final Resources resources;

  /**
   * Построить отдельную секцию доступности исходного метода.
   *
   * @param method метод, на который указывает ссылка.
   * @return markdown-секция либо пустая строка, если контексты определить нельзя.
   */
  public String render(MethodSymbol method) {
    var resolved = contextResolver.declarationContexts(method);
    if (resolved.isEmpty()) {
      return "";
    }
    var contexts = resolved.orElseThrow();
    var value = contexts.isEmpty() ? tr("unreachable") : contextNames(contexts);
    var result = "**" + tr("compilationContexts") + ":** " + value;
    var serverCalls = contextResolver.serverCallContexts(method, contexts);
    if (!serverCalls.isEmpty()) {
      result += "\n\n**" + tr("serverCall") + ":** " + tr("serverCallPossible");
    }
    return result;
  }

  /** Файловый вариант показывается отдельно, только когда обычный вариант того же режима отсутствует. */
  private String contextNames(Set<ExecutionContext> contexts) {
    return ExecutionContext.copyOf(contexts).stream()
      .filter(context -> !context.isFileMode() || !contexts.contains(context.optionContext()))
      .map(context -> tr("context." + context.name()))
      .distinct()
      .collect(Collectors.joining(", "));
  }

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }
}
