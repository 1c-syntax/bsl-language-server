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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для проверки корректности применения инкрементальных изменений текста.
 */
class IncrementalTextChangeTest {

  @Test
  void testInsertAtBeginning() throws Exception {
    // given
    String content = "Процедура Тест()\nКонецПроцедуры";
    Range range = Ranges.create(0, 0, 0, 0);
    var change = new TextDocumentContentChangeEvent(range, "// Комментарий\n");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("// Комментарий\nПроцедура Тест()\nКонецПроцедуры");
  }

  @Test
  void testInsertInMiddle() throws Exception {
    // given
    String content = "Процедура Тест()\nКонецПроцедуры";
    Range range = Ranges.create(1, 0, 1, 0);
    var change = new TextDocumentContentChangeEvent(range, "  // Тело процедуры\n");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Процедура Тест()\n  // Тело процедуры\nКонецПроцедуры");
  }

  @Test
  void testDeleteRange() throws Exception {
    // given
    String content = "Процедура Тест()\nКонецПроцедуры";
    Range range = Ranges.create(0, 0, 0, 10);
    var change = new TextDocumentContentChangeEvent(range, "");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Тест()\nКонецПроцедуры");
  }

  @Test
  void testReplaceText() throws Exception {
    // given
    String content = "Процедура Тест()\nКонецПроцедуры";
    Range range = Ranges.create(0, 10, 0, 14);
    var change = new TextDocumentContentChangeEvent(range, "Проверка");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Процедура Проверка()\nКонецПроцедуры");
  }

  @Test
  void testDeleteMultipleLines() throws Exception {
    // given
    String content = "Строка 1\nСтрока 2\nСтрока 3";
    Range range = Ranges.create(0, 8, 2, 0);
    var change = new TextDocumentContentChangeEvent(range, "");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Строка 1Строка 3");
  }

  @Test
  void testFullDocumentUpdate() throws Exception {
    // given
    String content = "Старое содержимое";
    var change = new TextDocumentContentChangeEvent("Новое содержимое");

    // when
    List<TextDocumentContentChangeEvent> changes = new ArrayList<>();
    changes.add(change);
    String result = applyTextDocumentChanges(content, changes);

    // then
    assertThat(result).isEqualTo("Новое содержимое");
  }

  @Test
  void testMultipleIncrementalChanges() throws Exception {
    // given
    String content = "Процедура Тест()\nКонецПроцедуры";
    
    List<TextDocumentContentChangeEvent> changes = new ArrayList<>();
    // First change: insert at beginning
    changes.add(new TextDocumentContentChangeEvent(Ranges.create(0, 0, 0, 0), "// Комментарий\n"));
    // Second change: insert in the body (note: positions are relative to the current state)
    changes.add(new TextDocumentContentChangeEvent(Ranges.create(2, 0, 2, 0), "  Возврат;\n"));

    // when
    String result = applyTextDocumentChanges(content, changes);

    // then
    assertThat(result).isEqualTo("// Комментарий\nПроцедура Тест()\n  Возврат;\nКонецПроцедуры");
  }

  @Test
  void testInsertAtEndOfLine() throws Exception {
    // given
    String content = "Процедура\nТест";
    Range range = Ranges.create(0, 9, 0, 9);
    var change = new TextDocumentContentChangeEvent(range, " Тест()");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Процедура Тест()\nТест");
  }

  @Test
  void testInsertNewline() throws Exception {
    // given
    String content = "Строка1Строка2";
    Range range = Ranges.create(0, 7, 0, 7);
    var change = new TextDocumentContentChangeEvent(range, "\n");

    // when
    String result = applyIncrementalChange(content, change);

    // then
    assertThat(result).isEqualTo("Строка1\nСтрока2");
  }

  @Test
  void testPreserveWindowsLineEndings() throws Exception {
    // given - document with Windows line endings
    String content = "Строка1\r\nСтрока2\r\nСтрока3";
    Range range = Ranges.create(1, 0, 1, 7);
    var change = new TextDocumentContentChangeEvent(range, "Изменено");

    // when
    String result = applyIncrementalChange(content, change);

    // then - Windows line endings should be preserved
    assertThat(result).isEqualTo("Строка1\r\nИзменено\r\nСтрока3");
  }

  @Test
  void testPreserveOldMacLineEndings() throws Exception {
    // given - document with old Mac line endings
    String content = "Строка1\rСтрока2\rСтрока3";
    Range range = Ranges.create(1, 0, 1, 7);
    var change = new TextDocumentContentChangeEvent(range, "Изменено");

    // when
    String result = applyIncrementalChange(content, change);

    // then - old Mac line endings should be preserved
    assertThat(result).isEqualTo("Строка1\rИзменено\rСтрока3");
  }

  @Test
  void testPreserveMixedLineEndings() throws Exception {
    // given - document with mixed line endings
    String content = "Строка1\r\nСтрока2\nСтрока3\rСтрока4";
    Range range = Ranges.create(2, 0, 2, 7);
    var change = new TextDocumentContentChangeEvent(range, "Изменено");

    // when
    String result = applyIncrementalChange(content, change);

    // then - all line endings should be preserved
    assertThat(result).isEqualTo("Строка1\r\nСтрока2\nИзменено\rСтрока4");
  }

  // Helper methods to call private methods via reflection
  private String applyIncrementalChange(String content, TextDocumentContentChangeEvent change) 
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = BSLTextDocumentService.class.getDeclaredMethod(
        "applyIncrementalChange", String.class, TextDocumentContentChangeEvent.class);
    method.setAccessible(true);
    return (String) method.invoke(null, content, change);
  }

  private String applyTextDocumentChanges(String content, List<TextDocumentContentChangeEvent> changes) 
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = BSLTextDocumentService.class.getDeclaredMethod(
        "applyTextDocumentChanges", String.class, List.class);
    method.setAccessible(true);
    return (String) method.invoke(null, content, changes);
  }
}
