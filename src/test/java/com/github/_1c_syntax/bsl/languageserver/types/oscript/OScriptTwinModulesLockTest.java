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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дерево символов модуля строится, не дожидаясь перестроения одноимённого модуля.
 * <p>
 * Исходник библиотеки и её же копия в {@code oscript_modules} регистрируются одним типом, и
 * члены этого типа собираются по деревьям обоих файлов. Строя дерево, документ сверяет голые
 * присваивания с членами своего типа — то есть читает и дерево двойника. Если двойник в это
 * же время перестраивается, а его построение так же читает дерево первого, ожидание замкнётся:
 * так зависал проход доразрешения типов возврата на CollectionOS.
 */
@CleanupContextBeforeClassAndAfterClass
class OScriptTwinModulesLockTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_ROOT = "src/test/resources/oscript-libraries/twins-lib";

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService executor;

  @Test
  void treeIsBuiltWithoutWaitingForTwinBeingRebuilt() throws Exception {
    // given: оба файла — один тип, а блокировку двойника держит другой поток, как держал бы
    // её поток, перестраивающий двойника. Памятка членов типа сброшена: иначе члены
    // двойника не перечитываются.
    initServerContext(Path.of(FIXTURE_ROOT).toAbsolutePath());
    var own = document(FIXTURE_ROOT + "/src/Близнец.os");
    var twin = document(FIXTURE_ROOT + "/oscript_modules/twins-lib/src/Близнец.os");
    typeRegistry.resolve("Близнец", FileType.OS).ifPresent(typeRegistry::invalidateMembers);
    var twinLock = (ReentrantLock) ReflectionTestUtils.getField(twin, "computeLock");
    assertThat(twinLock).isNotNull();
    twinLock.lock();
    try {
      // when: вставшее построение обрывается таймаутом ожидания, а не вешает весь прогон.
      var content = Files.readString(Path.of(FIXTURE_ROOT, "src", "Близнец.os")) + "\n";
      executor.submit(() -> context.rebuildDocument(own, content, 1)).get(30, TimeUnit.SECONDS);

      // then: дерево построено по новому тексту, голое присваивание — локальная переменная.
      assertThat(own.getContent()).isEqualTo(content);
      assertThat(own.getSymbolTree().getVariables())
        .extracting(VariableSymbol::getName)
        .contains("Результат");
    } finally {
      twinLock.unlock();
    }
  }

  private DocumentContext document(String path) {
    var documentContext = context.getDocument(Absolute.uri(new File(path)));
    assertThat(documentContext).as("документ %s в рабочей папке", path).isNotNull();
    return documentContext;
  }
}
