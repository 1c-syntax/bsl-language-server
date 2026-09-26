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
package com.github._1c_syntax.bsl.languageserver.types.oscript.extends_;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отношения наследования не ждут блокировок документов.
 * <p>
 * Наследников обходят и под блокировкой записи: перечитанный документ сбрасывает члены
 * своих наследников прямо в слушателе изменения содержимого. Два потока, перечитывающие
 * родителя и наследника, держат запись каждый своего документа — и если обход встанет на
 * блокировке чужого, ожидание замкнётся.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeRelationsLockTest extends AbstractServerContextAwareTest {

  private static final Path TYPE_HIERARCHY = Path.of("src/test/resources/type-hierarchy").toAbsolutePath();

  @Autowired
  private TypeRelations typeRelations;

  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService executor;

  @Test
  void subtypesDoNotWaitForLockOfSubtypeDocument() throws Exception {
    // given: блокировку наследника на запись держит тестовый поток, а обход идёт на
    // другом: владелец записи сам получил бы и чтение, и ожидание бы не проявилось.
    initServerContext(TYPE_HIERARCHY);
    var mammal = context.getDocument(Absolute.uri(TYPE_HIERARCHY.resolve("Млекопитающее.os").toUri()));
    var cat = context.getDocument(Absolute.uri(TYPE_HIERARCHY.resolve("Кошка.os").toUri()));
    var lock = context.getDocumentLock(cat.getUri()).writeLock();
    lock.lock();
    try {
      // when: вставший обход обрывается таймаутом ожидания, а не вешает весь прогон.
      var subtypes = executor.submit(() -> typeRelations.subtypes(mammal)).get(30, TimeUnit.SECONDS);

      // then: обход не только не встал, но и нашёл наследника под блокировкой.
      assertThat(subtypes).contains(cat);
    } finally {
      lock.unlock();
    }
  }
}
