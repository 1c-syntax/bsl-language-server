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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Проверяет, что {@link OScriptLibraryFileSystemWatcher} реагирует на
 * модификацию {@code lib.config} в watched-директории и триггерит
 * {@link OScriptLibraryIndex#reindex} без рестарта LS.
 * <p>
 * Шедулер отключён (см. свойство {@code app.scheduling.enabled=false}) —
 * {@link OScriptLibraryFileSystemWatcher#poll()} вызывается из теста явно
 * на каждой итерации Awaitility, поэтому тест не зависит от расписания.
 */
@SpringBootTest(properties = {"app.scheduling.enabled=false"})
@DirtiesContext
class OScriptLibraryFileSystemWatcherTest {

  @Autowired
  private OScriptLibraryFileSystemWatcher watcher;

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private OScriptModuleMembersProvider moduleMembersProvider;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Autowired
  private ServerContextProvider serverContextProvider;

  @TempDir
  Path workspaceDir;

  private URI workspaceUri;

  @BeforeEach
  void setUp() {
    serverContextProvider.clear();
    var serverContext = serverContextProvider.addWorkspace(Absolute.uri(workspaceDir.toUri()));
    workspaceUri = serverContext.getWorkspaceUri();
    WorkspaceContextHolder.set(workspaceUri);
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
    serverContextProvider.clear();
  }

  @Test
  void moduleTypeByUriIndexedOnRegisterAndClearedOnUnregister() throws IOException {
    // given — workspace c lib.config, объявляющим module ModOne
    Files.writeString(workspaceDir.resolve("ModOne.os"), """
      Процедура Привет() Экспорт
      КонецПроцедуры
      """);
    Files.writeString(workspaceDir.resolve("lib.config"), """
      <package-def>
        <module name="ModOne" file="ModOne.os"/>
      </package-def>
      """);
    var modUri = Absolute.uri(workspaceDir.resolve("ModOne.os").toUri());

    // when — reindex регистрирует module-as-type
    index.reindex(serverContextProvider.getServerContext(workspaceUri).orElseThrow());

    // then — обратный индекс URI→тип заполнен
    WorkspaceContextHolder.run(workspaceUri, () ->
      assertThat(globalScopeProvider.moduleTypeByUri(modUri))
        .as("после регистрации тип модуля по URI должен быть в индексе")
        .isPresent());

    // when — документ удалён из контекста (тот же путь, что у handleDocumentRemoved)
    WorkspaceContextHolder.run(workspaceUri, () -> moduleMembersProvider.unregister(modUri));

    // then — обратный индекс по URI очищен, без протечки
    WorkspaceContextHolder.run(workspaceUri, () ->
      assertThat(globalScopeProvider.moduleTypeByUri(modUri))
        .as("после unregister URI→тип должен исчезнуть")
        .isEmpty());
  }

  @Test
  void reindexTriggeredWhenLibConfigIsModified() throws IOException {
    // given — workspace c lib.config v1 (объявляет ClassOne)
    var libConfig = workspaceDir.resolve("lib.config");
    Files.writeString(workspaceDir.resolve("ClassOne.os"), """
      Процедура ПриСозданииОбъекта() Экспорт
      КонецПроцедуры
      """);
    Files.writeString(workspaceDir.resolve("ClassTwo.os"), """
      Процедура ПриСозданииОбъекта() Экспорт
      КонецПроцедуры
      """);
    Files.writeString(libConfig, """
      <package-def>
        <class name="ClassOne" file="ClassOne.os"/>
      </package-def>
      """);

    // when — первичный reindex через addWorkspace уже сработал; убеждаемся в текущем состоянии
    // и форсируем reindex руками, чтобы быть уверенными в стартовом снапшоте.
    index.reindex(serverContextProvider.getServerContext(workspaceUri).orElseThrow());
    assertThat(index.findByName("ClassOne"))
      .as("после первичного reindex ClassOne должен быть в индексе")
      .isPresent();
    assertThat(index.findByName("ClassTwo"))
      .as("ClassTwo пока не в манифесте, его не должно быть")
      .isEmpty();

    // when — перезаписываем lib.config на v2 (объявляет ClassTwo вместо ClassOne)
    Files.writeString(libConfig, """
      <package-def>
        <class name="ClassTwo" file="ClassTwo.os"/>
      </package-def>
      """);

    // then — на каждой итерации Awaitility синхронно вызываем watcher.poll(),
    // он обрабатывает накопленные WatchService-события и триггерит reindex
    // в правильном workspace-scope.
    await().atMost(15, SECONDS).untilAsserted(() ->
      WorkspaceContextHolder.run(workspaceUri, () -> {
        watcher.poll();
        assertThat(index.findByName("ClassTwo"))
          .as("после изменения lib.config ClassTwo должен появиться в индексе")
          .isPresent();
        assertThat(index.findByName("ClassOne"))
          .as("после изменения lib.config ClassOne должен исчезнуть из индекса")
          .isEmpty();
      })
    );
  }
}
