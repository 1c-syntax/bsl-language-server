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
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark для тестирования производительности инкрементальных изменений текста.
 * Тестирует обработку файлов разного размера (100, 1000, 10000 строк).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
public class IncrementalTextChangeBenchmark {

  @Param({"100", "1000", "10000"})
  private int lineCount;

  private String documentContent;
  private TextDocumentContentChangeEvent changeAtStart;
  private TextDocumentContentChangeEvent changeInMiddle;
  private TextDocumentContentChangeEvent changeAtEnd;

  @Setup(Level.Trial)
  public void setup() {
    // Создаем документ с заданным количеством строк
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lineCount; i++) {
      sb.append("Процедура Тест").append(i).append("()\n");
      sb.append("  // Комментарий в строке ").append(i).append("\n");
      sb.append("  Возврат Истина;\n");
      sb.append("КонецПроцедуры\n");
      sb.append("\n");
    }
    documentContent = sb.toString();

    // Изменение в начале документа
    changeAtStart = new TextDocumentContentChangeEvent(
      Ranges.create(0, 0, 0, 9),
      "Функция"
    );

    // Изменение в середине документа
    int middleLine = lineCount * 2;
    changeInMiddle = new TextDocumentContentChangeEvent(
      Ranges.create(middleLine, 2, middleLine, 15),
      "Новый комментарий"
    );

    // Изменение в конце документа
    int lastLine = lineCount * 5 - 2;
    changeAtEnd = new TextDocumentContentChangeEvent(
      Ranges.create(lastLine, 0, lastLine, 14),
      "КонецФункции"
    );
  }

  @Benchmark
  public String benchmarkChangeAtStart() throws Exception {
    return applyIncrementalChange(documentContent, changeAtStart);
  }

  @Benchmark
  public String benchmarkChangeInMiddle() throws Exception {
    return applyIncrementalChange(documentContent, changeInMiddle);
  }

  @Benchmark
  public String benchmarkChangeAtEnd() throws Exception {
    return applyIncrementalChange(documentContent, changeAtEnd);
  }

  @Benchmark
  public String benchmarkMultipleChanges() throws Exception {
    String result = documentContent;
    result = applyIncrementalChange(result, changeAtStart);
    result = applyIncrementalChange(result, changeInMiddle);
    result = applyIncrementalChange(result, changeAtEnd);
    return result;
  }

  /**
   * Вызывает приватный метод applyIncrementalChange через рефлексию.
   */
  private String applyIncrementalChange(String content, TextDocumentContentChangeEvent change) throws Exception {
    Method method = BSLTextDocumentService.class.getDeclaredMethod(
      "applyIncrementalChange",
      String.class,
      TextDocumentContentChangeEvent.class
    );
    method.setAccessible(true);
    return (String) method.invoke(null, content, change);
  }
}
