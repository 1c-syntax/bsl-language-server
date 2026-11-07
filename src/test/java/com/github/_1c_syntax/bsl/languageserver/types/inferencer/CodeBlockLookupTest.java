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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBlockLookupTest {
  @ParameterizedTest
  @MethodSource("sources")
  void matchesTheLinearLookupAtEveryPosition(String source) {
    var ast = new BSLTokenizer(source).getAst();
    var lines = source.split("\n", -1);
    for (var line = 0; line <= lines.length; line++) {
      var length = line < lines.length ? lines[line].length() : 0;
      for (var column = 0; column <= length + 1; column++) {
        var position = new Position(line, column);
        assertThat(CodeBlockLookup.at(ast, position)).as("position %s", position)
          .isSameAs(linear(ast, position));
      }
    }
  }

  private static Stream<String> sources() {
    return Stream.of("", "Сообщить(1);", "#Если Сервер Тогда\n#КонецЕсли", """
      Процедура Первая()
      КонецПроцедуры;Процедура Вторая()
        Сообщить(1);
      КонецПроцедуры
      """, """
      Сообщить(0);
      #Область Методы
      #Если Сервер Тогда
      &НаСервере
      Функция Значение()
        Возврат 1;
      КонецФункции
      #Иначе
      Функция Значение()
        Возврат "";
      КонецФункции
      #КонецЕсли
      #КонецОбласти
      Сообщить(2);
      """, "Процедура П()\r\nКонецПроцедуры\r\nСообщить(1);");
  }

  @Test
  void repeatedLookupVisitsLogarithmicallyManyTopLevelNodes() {
    var source = new StringBuilder();
    for (var i = 0; i < 4096; i++) {
      source.append("Процедура П").append(i).append("()\nКонецПроцедуры\n");
    }
    var ast = new BSLTokenizer(source.toString()).getAst();
    var subs = ast.subs();
    var expected = subs.sub(4095).procedure().subCodeBlock().codeBlock();
    var children = new ArrayList<ParseTree>();
    for (var i = 0; i < subs.getChildCount(); i++) {
      children.add(subs.getChild(i));
    }
    var counted = new CountingChildren(children);
    ReflectionTestUtils.setField(subs, "children", counted);
    CodeBlockLookup.at(ast, new Position(0, 10));
    counted.reads = 0;

    assertThat(CodeBlockLookup.at(ast, new Position(8190, 10))).isSameAs(expected);
    assertThat(counted.reads).isLessThan(64);
  }

  private static final class CountingChildren extends AbstractList<ParseTree> {
    private final List<ParseTree> children;
    private int reads;

    private CountingChildren(List<ParseTree> children) {
      this.children = children;
    }

    @Override
    public ParseTree get(int index) {
      reads++;
      return children.get(index);
    }

    @Override
    public int size() {
      return children.size();
    }
  }

  private static BSLParser.@Nullable CodeBlockContext linear(BSLParser.FileContext ast, Position position) {
    var subs = ast.subs();
    if (subs != null) {
      for (var sub : subs.sub()) {
        if (Ranges.containsPosition(Ranges.create(sub), position)) {
          var procedure = sub.procedure();
          if (procedure != null) {
            return procedure.subCodeBlock().codeBlock();
          }
          var function = sub.function();
          return function == null ? null : function.subCodeBlock().codeBlock();
        }
      }
    }
    var before = ast.fileCodeBlockBeforeSub();
    if (before != null && Ranges.containsPosition(Ranges.create(before.codeBlock()), position)) {
      return before.codeBlock();
    }
    var after = ast.fileCodeBlock();
    return after != null && Ranges.containsPosition(Ranges.create(after.codeBlock()), position) ? after.codeBlock() : null;
  }
}
