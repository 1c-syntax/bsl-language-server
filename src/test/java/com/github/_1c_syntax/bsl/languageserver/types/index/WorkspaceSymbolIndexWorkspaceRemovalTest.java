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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.providers.SymbolProvider;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Индекс символов общий на все рабочие области: удаление одной из них не должно
 * стирать записи документов остальных.
 */
@SpringBootTest
class WorkspaceSymbolIndexWorkspaceRemovalTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op
  };

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Autowired
  private WorkspaceSymbolIndex index;

  @Autowired
  private SymbolProvider symbolProvider;

  @TempDir
  Path folderA;

  @TempDir
  Path folderB;

  private URI uriA;
  private URI uriB;

  @BeforeEach
  void setUp() throws IOException {
    Files.writeString(folderA.resolve("ModuleA.bsl"), """
      Процедура СимволИзПапкиА() Экспорт
      КонецПроцедуры
      """, StandardCharsets.UTF_8);
    Files.writeString(folderB.resolve("ModuleB.bsl"), """
      Процедура СимволИзПапкиБ() Экспорт
      КонецПроцедуры
      """, StandardCharsets.UTF_8);

    uriA = Absolute.uri(folderA.toUri());
    uriB = Absolute.uri(folderB.toUri());

    populate(uriA);
    populate(uriB);
  }

  @AfterEach
  void tearDown() {
    serverContextProvider.clear();
  }

  @Test
  void removingOneWorkspaceKeepsSymbolsOfTheOther() {
    // given: обе папки проиндексированы
    assertThat(index.search("СимволИзПапкиА", NO_CANCEL))
      .as("до удаления папки B символ папки A найден")
      .anyMatch(entry -> entry.name().equals("СимволИзПапкиА"));
    assertThat(index.search("СимволИзПапкиБ", NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("СимволИзПапкиБ"));

    // when: удалена только папка B
    serverContextProvider.removeWorkspace(new WorkspaceFolder(uriB.toString(), "B"));

    // then: записи папки B ушли, записи папки A остались
    assertThat(index.search("СимволИзПапкиБ", NO_CANCEL))
      .as("символы удалённой папки B сняты")
      .isEmpty();
    assertThat(index.search("СимволИзПапкиА", NO_CANCEL))
      .as("символы оставшейся папки A после удаления папки B")
      .anyMatch(entry -> entry.name().equals("СимволИзПапкиА"));
  }

  @Test
  void workspaceSymbolRequestStillFindsSymbolsOfRemainingWorkspace() {
    // given
    var params = new WorkspaceSymbolParams("СимволИзПапкиА");
    assertThat(symbolProvider.getSymbols(params, NO_CANCEL))
      .anyMatch(symbol -> symbol.getName().equals("СимволИзПапкиА"));

    // when: LSP-клиент прислал workspace/didChangeWorkspaceFolders с removed = [B]
    serverContextProvider.removeWorkspace(new WorkspaceFolder(uriB.toString(), "B"));

    // then: workspace/symbol по оставшейся папке A
    assertThat(symbolProvider.getSymbols(params, NO_CANCEL))
      .as("workspace/symbol после удаления папки B")
      .anyMatch(symbol -> symbol.getName().equals("СимволИзПапкиА"));
  }

  private void populate(URI workspaceUri) {
    var serverContext = serverContextProvider.addWorkspace(workspaceUri);
    WorkspaceContextHolder.run(workspaceUri, serverContext::populateContext);
  }
}
