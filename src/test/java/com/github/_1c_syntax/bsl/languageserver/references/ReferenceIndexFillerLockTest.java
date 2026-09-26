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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Заполнение индекса ссылок не ждёт блокировок чужих документов.
 * <p>
 * Индекс заполняется в слушателе изменения содержимого — под блокировкой записи своего
 * документа. Два документа, ссылающихся на классы друг друга, перечитываются на разных
 * потоках, и если заполнение встанет на блокировке класса, ожидание замкнётся.
 */
@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexFillerLockTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/constructor-inlay-test";
  private static final String CALLER_PATH = FIXTURE_DIR + "/src/Классы/Caller.os";

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private OScriptLibraryIndex oScriptLibraryIndex;

  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService executor;

  @Test
  void fillDoesNotWaitForLockOfLibraryClass() throws Exception {
    // given: блокировку класса на запись держит тестовый поток, а заполнение идёт на
    // другом: владелец записи сам получил бы и чтение, и ожидание бы не проявилось.
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath());
    oScriptLibraryIndex.reindex(context);
    var classUri = oScriptLibraryIndex.findClassUri("ClassWithCtorParams").orElseThrow();
    var constructor = context.getDocument(classUri).getSymbolTree().getConstructor().orElseThrow();
    var caller = TestUtils.getDocumentContextFromFile(CALLER_PATH, context);
    var lock = context.getDocumentLock(classUri).writeLock();
    lock.lock();
    try {
      // when: вставшее заполнение обрывается таймаутом ожидания, а не вешает весь прогон.
      executor.submit(() -> referenceIndexFiller.fill(caller)).get(30, TimeUnit.SECONDS);

      // then: вызов конструктора класса под блокировкой попал в индекс.
      assertThat(referenceIndex.getReferencesTo(constructor)).isNotEmpty();
    } finally {
      lock.unlock();
    }
  }
}
