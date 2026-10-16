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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поиск тела по позиции ({@link VariableFlowAnalyzer#bodyAt}) сверяется с поиском по полным
 * диапазонам узлов в каждой позиции модуля, включая границы объявлений.
 */
@SpringBootTest
class VariableFlowAnalyzerBodyAtTest {

  @Test
  void bodyAtMatchesRangeContainmentAtEveryPosition() {
    // given — код до методов, методы в одну и несколько строк, пустые строки между ними и код
    // модуля после методов.
    var lines = new String[]{
      "Перем А;",
      "А = 1;",
      "Процедура Первая() Б = 1; КонецПроцедуры",
      "",
      "Функция Вторая()",
      "    В = 2;",
      "    Возврат В;",
      "КонецФункции",
      "",
      "&НаКлиенте",
      "Процедура Третья()",
      "КонецПроцедуры",
      "А = 3;"
    };
    var documentContext = TestUtils.getDocumentContext(String.join("\n", lines));

    // then
    var found = 0;
    for (var line = 0; line < lines.length; line++) {
      for (var character = 0; character <= lines[line].length() + 1; character++) {
        var position = new Position(line, character);
        var body = VariableFlowAnalyzer.bodyAt(documentContext, position);
        assertThat(body).as("позиция %s", position).isSameAs(byRanges(documentContext, position));
        if (body != null) {
          found++;
        }
      }
    }
    assertThat(found).as("тела находятся").isPositive();
  }

  /** Тот же поиск, что у {@link VariableFlowAnalyzer#bodyAt}, но по полным диапазонам узлов. */
  private static BSLParser.@Nullable CodeBlockContext byRanges(DocumentContext documentContext, Position position) {
    var ast = documentContext.getAst();
    var subs = ast.subs();
    if (subs != null) {
      for (var sub : subs.sub()) {
        if (!Ranges.containsPosition(Ranges.create(sub), position)) {
          continue;
        }
        var procedure = sub.procedure();
        if (procedure != null) {
          return procedure.subCodeBlock().codeBlock();
        }
        var function = sub.function();
        return function == null ? null : function.subCodeBlock().codeBlock();
      }
    }
    for (var block : new BSLParser.CodeBlockContext[]{
      ast.fileCodeBlockBeforeSub() == null ? null : ast.fileCodeBlockBeforeSub().codeBlock(),
      ast.fileCodeBlock() == null ? null : ast.fileCodeBlock().codeBlock()}) {
      if (block != null && Ranges.containsPosition(Ranges.create(block), position)) {
        return block;
      }
    }
    return null;
  }
}
