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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * Конфигурация читается до параллельного заполнения рабочей области, а не лениво из него.
 * <p>
 * Её спрашивает каждый создаваемый документ, и спрашивает под блокировкой своих
 * вычислений. Прочитанная из задачи заполнения, она ждётся в чужом ForkJoinPool, а ждущий
 * воркер подхватывает другие задачи заполнения — те встают на блокировках документов,
 * держатели которых ждут эту же конфигурацию.
 */
@CleanupContextBeforeClassAndAfterClass
class ServerContextPopulateConfigurationTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @MockitoSpyBean(name = "computeConfigurationExecutor")
  private ExecutorService computeConfigurationExecutor;

  @Test
  void configurationIsNotReadFromPopulateTasks() {
    // given: конфигурация ещё не прочитана — обвязка теста могла прочитать её при
    // создании рабочей области, поэтому контекст сброшен. Запоминаем, из какого потока
    // уходит её чтение.
    initServerContext(PATH_TO_METADATA, false);
    context.clear();
    var readFrom = new CopyOnWriteArrayList<String>();
    doAnswer(invocation -> {
      readFrom.add(Thread.currentThread().getName());
      return invocation.callRealMethod();
    }).when(computeConfigurationExecutor).submit(ArgumentMatchers.<Callable<Object>>any());

    // when
    context.populateContext();

    // then
    assertThat(readFrom)
      .isNotEmpty()
      .noneMatch(threadName -> threadName.startsWith("populate-context-"));
  }
}
