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
package com.github._1c_syntax.bsl.languageserver.util;

import org.springframework.test.context.TestExecutionListeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Маркерная аннотация для теста, заставляющая сбросить состояние workspace'а
 * (ServerContextProvider, и через него — все workspace-scoped beans + index'ы,
 * подписанные на {@code ServerContextDocumentRemovedEvent}) при инстанцировании
 * тест-класса и после окончания работы каждого тест-метода.
 *
 * @param fullRefresh если {@code true}, дополнительно помечается ApplicationContext
 *                    как dirty и Spring пересоздаёт ВСЕ singleton-bean'ы. Дорого
 *                    (3–7 секунд на цикл, умножается на число тест-методов класса).
 *                    Включать только когда тест-методы класса модифицируют
 *                    singleton-state (например, {@link com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration}
 *                    через {@code .update(...)} / {@code .setLanguage(...)}) и
 *                    нуждаются в полной перезагрузке singleton'ов.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestExecutionListeners(
  mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
  listeners = {DirtyContextBeforeClassAndAfterTestMethodTestExecutionListener.class}
)
public @interface CleanupContextBeforeClassAndAfterEachTestMethod {
  boolean fullRefresh() default false;
}
