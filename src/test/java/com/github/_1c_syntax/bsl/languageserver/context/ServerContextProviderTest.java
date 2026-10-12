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

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;


import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ServerContextProviderTest {

  @Autowired
  private ServerContextProvider serverContextProvider;

  @BeforeEach
  void setUp() {
    // Изоляция: serverContextProvider — общий синглтон, состояние (в т.ч. дефолтный контекст)
    // может протечь из предыдущего теста, если его cleanup не отработал из-за упавшего ассерта.
    // Начинаем каждый тест с чистого листа.
    serverContextProvider.clear();
  }

  @Test
  void testAddWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");

    // when
    var serverContext = serverContextProvider.addWorkspace(workspaceFolder);

    // then
    assertThat(serverContext).isNotNull();
    assertThat(serverContext.getConfigurationRoot()).isNotNull();
    assertThat(serverContextProvider.getAllContexts()).isNotEmpty();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testRemoveWorkspace() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    // when
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then - workspace removed, should not find contexts
    assertThat(serverContextProvider.getAllContexts()).isEmpty();
  }

  @Test
  void testGetServerContextForDocument() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    var documentUri = Absolute.path(PATH_TO_METADATA).resolve("CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl").toUri();

    // when
    var foundContext = serverContextProvider.getServerContext(documentUri);

    // then
    assertThat(foundContext).isPresent();
    assertThat(foundContext.get()).isNotNull();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testGetAllContexts(@TempDir Path firstRoot, @TempDir Path secondRoot) {
    // given — проверяется учёт workspace'ов в провайдере, содержимое папок роли не играет:
    // на фикстуре метаданных каждая регистрация тянет разбор конфигурации.
    var workspaceUri1 = Absolute.uri(firstRoot.toUri()).toString();
    var workspaceFolder1 = new WorkspaceFolder(workspaceUri1, "workspace-1");

    var workspaceUri2 = Absolute.uri(secondRoot.toUri()).toString();
    var workspaceFolder2 = new WorkspaceFolder(workspaceUri2, "workspace-2");

    // when
    serverContextProvider.addWorkspace(workspaceFolder1);
    serverContextProvider.addWorkspace(workspaceFolder2);

    // then
    var allContexts = serverContextProvider.getAllContexts();
    assertThat(allContexts).hasSize(2);

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder1);
    serverContextProvider.removeWorkspace(workspaceFolder2);
  }

  @Test
  void testClearAllWorkspaces() {
    // given
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);

    // when
    serverContextProvider.clear();

    // then
    assertThat(serverContextProvider.getAllContexts()).isEmpty();
  }

  @Test
  void testRegisterDefaultWorkspace() {
    // when
    var defaultContext = serverContextProvider.registerDefaultWorkspace();

    // then — контекст создан без корня: populateContext ничего не сканирует
    assertThat(defaultContext).isNotNull();
    assertThat(defaultContext.getConfigurationRoot()).isNull();
    assertThat(serverContextProvider.getAllContexts())
      .containsKey(ServerContextProvider.DEFAULT_WORKSPACE_URI);
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(defaultContext);

    // идемпотентность: повторный вызов возвращает тот же контекст
    assertThat(serverContextProvider.registerDefaultWorkspace()).isSameAs(defaultContext);

    // cleanup
    serverContextProvider.clear();
  }

  @Test
  void testResolveUntitledDocumentRoutesToDefaultWorkspace() {
    // given — режим одиночного файла: зарегистрирован только дефолтный контекст
    var defaultContext = serverContextProvider.registerDefaultWorkspace();

    // when
    var resolved = serverContextProvider.resolveContextForDocument(URI.create("untitled:Untitled-1"));

    // then — untitled-документ маршрутизируется в дефолтный контекст
    assertThat(resolved).isSameAs(defaultContext);
    // строгий поиск владельца документа фолбэка не делает
    assertThat(serverContextProvider.getServerContext(Absolute.uri("untitled:Untitled-1"))).isEmpty();

    // cleanup
    serverContextProvider.clear();
  }

  @Test
  void testResolveUntitledDocumentRoutesToFirstWorkspace() {
    // given — есть реальный workspace, дефолтный контекст не создаётся
    var workspaceUri = Absolute.path(PATH_TO_METADATA).toUri().toString();
    var workspaceFolder = new WorkspaceFolder(workspaceUri, "test-workspace");
    var workspaceContext = serverContextProvider.addWorkspace(workspaceFolder);

    // when — untitled-буфер не относится ни к одному корню
    var resolved = serverContextProvider.resolveContextForDocument(URI.create("untitled:Untitled-1"));

    // then — маршрутизируется в главный (первый) workspace, а не теряется
    assertThat(resolved).isSameAs(workspaceContext);

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testAddingWorkspaceAtRuntimePromotesPrimaryFromDefault() {
    // given — initialize(null): открыт одиночный файл, есть только дефолтный контекст, он же primary
    var defaultContext = serverContextProvider.registerDefaultWorkspace();
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(defaultContext);

    // when — в рантайме добавили реальную папку (didChangeWorkspaceFolders → addWorkspace)
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "runtime");
    var workspaceContext = serverContextProvider.addWorkspace(workspaceFolder);

    // then — primary переезжает с синтетического дефолта на реальную папку, и untitled-буферы
    // теперь маршрутизируются в неё (с конфигурацией), а не в пустой дефолт
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(workspaceContext);
    assertThat(serverContextProvider.resolveContextForDocument(URI.create("untitled:Untitled-1")))
      .isSameAs(workspaceContext);
    // дефолтный контекст остаётся — в нём мог остаться ранее открытый одиночный файл
    assertThat(serverContextProvider.getAllContexts())
      .containsKey(ServerContextProvider.DEFAULT_WORKSPACE_URI);

    // cleanup
    serverContextProvider.clear();
  }

  @Test
  void testRemovingRuntimeWorkspaceRepointsPrimaryBackToDefault() {
    // given — дефолт + реальная папка; primary промоутнут на реальную
    var defaultContext = serverContextProvider.registerDefaultWorkspace();
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "runtime");
    var workspaceContext = serverContextProvider.addWorkspace(workspaceFolder);
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(workspaceContext);

    // when — папку убрали из рабочего пространства в рантайме
    serverContextProvider.removeWorkspace(workspaceFolder);

    // then — primary откатывается на оставшийся дефолтный контекст; untitled снова уходит в него
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(defaultContext);
    assertThat(serverContextProvider.resolveContextForDocument(URI.create("untitled:Untitled-1")))
      .isSameAs(defaultContext);

    // cleanup
    serverContextProvider.clear();
  }

  @Test
  void testFirstRuntimeWorkspaceStaysPrimaryWhenSecondAdded(@TempDir Path rootX, @TempDir Path rootY) {
    // given — дефолт, затем в рантайме добавили папку X (primary → X).
    // Папки берём пустые: тест про выбор primary, а не про их содержимое.
    serverContextProvider.registerDefaultWorkspace();
    var folderX = new WorkspaceFolder(Absolute.uri(rootX.toUri()).toString(), "X");
    var contextX = serverContextProvider.addWorkspace(folderX);
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(contextX);

    // when — добавили вторую папку Y
    var folderY = new WorkspaceFolder(Absolute.uri(rootY.toUri()).toString(), "Y");
    serverContextProvider.addWorkspace(folderY);

    // then — primary остаётся на первой реальной папке, не «прыгает» на вторую
    assertThat(serverContextProvider.getPrimaryContext()).isSameAs(contextX);

    // cleanup
    serverContextProvider.clear();
  }

  @Test
  void testResolveContextForDocumentWithoutWorkspacesFailsLoudly() {
    // given — ни одного контекста (обращение до initialize() или после shutdown())
    serverContextProvider.clear();

    // then — маршрутизация без главного контекста падает громко, а не молча возвращает пустоту
    var documentUri = URI.create("untitled:Untitled-1");
    assertThatThrownBy(() -> serverContextProvider.resolveContextForDocument(documentUri))
      .isInstanceOf(IllegalStateException.class);
  }
}
