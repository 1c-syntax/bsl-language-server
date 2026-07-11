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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Стресс-тест на гонку «перестроение документа против конкурентного чтения индекса ссылок»
 * (имитация старта VS Code: didChange открытого файла параллельно с расчётом диагностик
 * без документного лока — {@code AnalyzeProjectOnStart}, push-ветка
 * {@code DiagnosticProvider.refreshDiagnostics}).
 * <p>
 * Поток-писатель перестраивает документ (каждый rebuild триггерит переиндексацию),
 * потоки-читатели проверяют предикат {@code UnusedLocalVariable} — наличие
 * REFERENCE-вхождений для переменной, используемой в каждом варианте содержимого.
 * Переиндексация обязана быть атомарной: ни один читатель ни в какой момент не должен
 * увидеть «переменная не используется».
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ReferenceIndexRaceStressTest {

  private static final int READERS = 4;
  private static final int REBUILDS = 400;

  @Autowired
  private ReferenceIndex referenceIndex;

  @Test
  void concurrentRebuildAndUnusedVariableCheck() throws Exception {
    // Документ побольше — переиндексация дольше, вероятность поймать гонку выше
    var content = new StringBuilder("""
      Процедура Целевая()
          А = 1;
          Б = А;
      КонецПроцедуры
      """);
    for (var i = 0; i < 150; i++) {
      content
        .append("Процедура М").append(i).append("()\n")
        .append("    П").append(i).append(" = 1;\n")
        .append("    Р").append(i).append(" = П").append(i).append(";\n")
        .append("КонецПроцедуры\n");
    }

    var documentContext = TestUtils.getDocumentContext(content.toString());
    var serverContext = documentContext.getServerContext();

    // Читатели должны резолвить те же workspace-scoped хранилища, что и
    // event-listener'ы заполнения индекса, — то есть работать под тем же
    // workspace-контекстом, что и поток текущего теста.
    var workspaceUri = WorkspaceContextHolder.get();
    var workspaceName = WorkspaceContextHolder.getName();
    assertThat(workspaceUri).isNotNull();

    // Прекондиция: индекс заполнен, использование переменной видно
    assertThat(referenceIndex.getReferencesTo(targetVariable(documentContext)))
      .anyMatch(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE);

    var falsePositives = new AtomicInteger();
    var readerErrors = new AtomicInteger();
    var stop = new AtomicBoolean();

    var pool = Executors.newFixedThreadPool(READERS);
    for (var r = 0; r < READERS; r++) {
      pool.execute(() -> {
        try (var ignored = WorkspaceContextHolder.forUri(workspaceUri, workspaceName)) {
          while (!stop.get()) {
            var used = referenceIndex.getReferencesTo(targetVariable(documentContext)).stream()
              .anyMatch(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE);
            if (!used) {
              // Ровно это условие даёт диагностику UnusedLocalVariable
              falsePositives.incrementAndGet();
              return;
            }
          }
        } catch (RuntimeException e) {
          // Исключение читателя — тоже дефект конкурентного доступа:
          // не глотаем, а проваливаем тест через счётчик.
          readerErrors.incrementAndGet();
          throw e;
        }
      });
    }

    try {
      for (var version = 2; version <= REBUILDS && falsePositives.get() == 0; version++) {
        serverContext.rebuildDocument(
          documentContext,
          content + "// v" + version + "\n",
          version
        );
      }
    } finally {
      stop.set(true);
      pool.shutdown();
      var terminated = pool.awaitTermination(60, TimeUnit.SECONDS);
      assertThat(terminated).isTrue();
    }

    assertThat(readerErrors.get())
      .withFailMessage("%d поток(ов) завершились исключением при чтении индекса", readerErrors.get())
      .isZero();
    assertThat(falsePositives.get())
      .withFailMessage(
        "%d поток(ов) увидели «переменная А не используется», хотя `Б = А` есть в каждом варианте содержимого",
        falsePositives.get())
      .isZero();
  }

  private static com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol targetVariable(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext) {
    return documentContext.getSymbolTree().getVariables().stream()
      .filter(v -> v.getName().equals("А"))
      .findFirst().orElseThrow();
  }
}
