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
package com.github._1c_syntax.bsl.languageserver.types.scope;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.experimental.UtilityClass;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Извлекает имена OneScript-библиотек, упомянутых в директивах
 * {@code #Использовать <libName>} текущего документа.
 * <p>
 * Используется downstream-провайдерами (completion, hover) чтобы ограничить
 * видимость library-модулей текущим документом: в 1С/OneScript нет "глобально
 * видимых" библиотек — их вытягивает в область видимости только директива
 * {@code #Использовать}. Однако стратегия применения фильтра — на стороне
 * вызывающего: если документ не содержит ни одной директивы, можно либо
 * откатываться к "видны все" (backward-compat), либо к "ничего не видно"
 * (строгий режим). См. {@code GlobalScopeProvider}.
 */
@UtilityClass
public class UseDirectiveScanner {

  /**
   * @return набор имён библиотек, объявленных в директивах {@code #Использовать},
   *         в порядке их появления.
   */
  public static Set<String> usedLibraries(DocumentContext documentContext) {
    var ast = documentContext.getAst();
    var nodes = Trees.<BSLParser.UseContext>findAllRuleNodes(ast, BSLParser.RULE_use);
    if (nodes.isEmpty()) {
      return Set.of();
    }
    var result = new LinkedHashSet<String>();
    for (var use : nodes) {
      Optional.ofNullable(use.usedLib())
        .map(BSLParser.UsedLibContext::PREPROC_IDENTIFIER)
        .ifPresent(id -> result.add(id.getText()));
    }
    return result;
  }

  /**
   * @return список имён библиотек (списочная форма {@link #usedLibraries}).
   */
  public static List<String> usedLibrariesList(DocumentContext documentContext) {
    return List.copyOf(usedLibraries(documentContext));
  }
}
