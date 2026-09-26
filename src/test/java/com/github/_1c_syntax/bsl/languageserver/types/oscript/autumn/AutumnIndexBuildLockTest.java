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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Ленивая сборка индексов «ОСени» не ждёт блокировок документов.
 * <p>
 * Сборку ждёт тот, кому понадобился индекс, — а он может держать блокировку документа на
 * запись: так делает проход доразрешения типов возврата, пока считает методы догруженного
 * документа. Если сборка сама встанет на блокировку этого документа, ожидание замкнётся.
 */
@CleanupContextBeforeClassAndAfterClass
class AutumnIndexBuildLockTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_ROOT = "src/test/resources/oscript-libraries/autumn-di";

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private AutumnBeanIndex beanIndex;

  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService executor;

  @Test
  void buildDoesNotWaitForLockOfDocumentHeldByWaiter() {
    // given: индекс ещё не собран, а блокировку класса библиотеки на запись держит
    // поток, который ждёт сборку. Сборка идёт на другом потоке: владелец записи сам
    // получил бы и чтение, и ожидание бы не проявилось.
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
    var logger = TestUtils.getDocumentContextFromFile(FIXTURE_ROOT + "/src/Логгер.os", context);
    var lock = context.getDocumentLock(logger.getUri()).writeLock();
    lock.lock();
    try {
      // when: задача ставится из этого потока — он несёт рабочую область, а ожидание с
      // таймаутом идёт в отдельном потоке, у которого её нет.
      var build = executor.submit(() -> beanIndex.resolve("Логгер"));
      var types = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> build.get());

      // then: сборка не только не встала, но и дошла до класса под блокировкой.
      assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Логгер");
    } finally {
      lock.unlock();
    }
  }
}
