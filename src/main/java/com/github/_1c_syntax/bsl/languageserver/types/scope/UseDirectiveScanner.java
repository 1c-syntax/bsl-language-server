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
      var usedLib = use.usedLib();
      if (usedLib == null) {
        continue;
      }
      var identifier = usedLib.PREPROC_IDENTIFIER();
      if (identifier != null) {
        // #Использовать <имяБиблиотеки>
        result.add(identifier.getText());
        continue;
      }
      // #Использовать "относительный/путь" — подключение каталога-библиотеки по
      // пути. Имя библиотеки для gating'а — последний сегмент пути (совпадает с
      // libOrigin = имя каталога, см. OScriptLibraryIndex#libOriginOf).
      var string = usedLib.PREPROC_STRING();
      if (string != null) {
        var libName = libraryNameFromPath(string.getText());
        if (!libName.isBlank()) {
          result.add(libName);
        }
      }
    }
    return result;
  }

  /**
   * Имя библиотеки из строкового пути директивы {@code #Использовать "путь"}:
   * снимает обрамляющие кавычки и берёт последний сегмент пути.
   * Пример: {@code "lib"} → {@code lib}, {@code "./libs/mylib"} → {@code mylib}.
   */
  private static String libraryNameFromPath(String raw) {
    var text = raw.strip();
    if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
      text = text.substring(1, text.length() - 1);
    }
    text = text.strip().replace('\\', '/');
    while (text.endsWith("/")) {
      text = text.substring(0, text.length() - 1);
    }
    var slash = text.lastIndexOf('/');
    return slash >= 0 ? text.substring(slash + 1) : text;
  }

  /**
   * @return список имён библиотек (списочная форма {@link #usedLibraries}).
   */
  public static List<String> usedLibrariesList(DocumentContext documentContext) {
    return List.copyOf(usedLibraries(documentContext));
  }
}
