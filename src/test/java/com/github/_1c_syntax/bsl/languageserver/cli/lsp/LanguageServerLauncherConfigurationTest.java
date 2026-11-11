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
package com.github._1c_syntax.bsl.languageserver.cli.lsp;

import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageServerLauncherConfigurationTest {
  @TempDir
  Path directory;

  @Test
  void springCreatesAnIndependentBuilderForEachLauncher() {
    // given
    try (var context = new AnnotationConfigApplicationContext(
      FileAwarePrintWriter.class, TraceAwareLauncherBuilder.class)) {
      var builders = context.getBeanProvider(TraceAwareLauncherBuilder.class);

      // when
      var first = builders.getObject();
      var second = builders.getObject();

      // then
      assertThat(second).isNotSameAs(first);
    }
  }

  @Test
  void disabledTraceDoesNotSerializeNotificationTwice() {
    var server = new TestServer();
    var input = System.in;
    var output = System.out;
    var bytes = new ByteArrayOutputStream();
    try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
         var writer = new FileAwarePrintWriter()) {
      System.setIn(new ByteArrayInputStream(new byte[0]));
      System.setOut(stream);
      var launcher = new LanguageServerLauncherConfiguration().launcher(server, new TraceAwareLauncherBuilder(writer));
      var payload = new CountingMap();
      payload.put("key", "value");
      launcher.getRemoteEndpoint().notify("probe", payload);
      assertThat(payload.serializations).as("only the JSON sent on the wire").isEqualTo(1);
      assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("\"method\":\"probe\"");
    } finally {
      System.setIn(input);
      System.setOut(output);
    }
  }

  @Test
  void traceCanBeEnabledAndDisabledAfterLauncherCreation() throws Exception {
    var input = System.in;
    var output = System.out;
    var bytes = new ByteArrayOutputStream();
    var trace = directory.resolve("trace.log");
    try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
         var writer = new FileAwarePrintWriter()) {
      System.setIn(new ByteArrayInputStream(new byte[0]));
      System.setOut(stream);
      var launcher = new LanguageServerLauncherConfiguration()
        .launcher(new TestServer(), new TraceAwareLauncherBuilder(writer));
      var first = new CountingMap();
      first.put("key", "first");
      launcher.getRemoteEndpoint().notify("probe", first);
      assertThat(first.serializations).isEqualTo(1);
      writer.setFile(trace.toFile());
      var second = new CountingMap();
      second.put("key", "second");
      launcher.getRemoteEndpoint().notify("probe", second);
      assertThat(second.serializations).isEqualTo(2);
      assertThat(Files.readString(trace)).contains("probe", "second");
      writer.setFile(null);
      var third = new CountingMap();
      third.put("key", "third");
      launcher.getRemoteEndpoint().notify("probe", third);
      assertThat(third.serializations).isEqualTo(1);
      assertThat(Files.readString(trace)).doesNotContain("third");
    } finally {
      System.setIn(input);
      System.setOut(output);
    }
  }

  private static final class TestServer implements LanguageServer {
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
      return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
    }

    public CompletableFuture<Object> shutdown() {
      return CompletableFuture.completedFuture(null);
    }

    public void exit() {
      // Тестовый сервер не завершает процесс.
    }

    public TextDocumentService getTextDocumentService() {
      return new TestDocuments();
    }

    public WorkspaceService getWorkspaceService() {
      return new TestWorkspace();
    }
  }

  private static final class TestDocuments implements TextDocumentService {
    public void didOpen(DidOpenTextDocumentParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }

    public void didChange(DidChangeTextDocumentParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }

    public void didClose(DidCloseTextDocumentParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }

    public void didSave(DidSaveTextDocumentParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }
  }

  private static final class TestWorkspace implements WorkspaceService {
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }

    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
      // Проверяется транспорт, обработка уведомления в заглушке не требуется.
    }
  }

  private static final class CountingMap extends LinkedHashMap<String, String> {
    private int serializations;

    @Override
    public Set<Map.Entry<String, String>> entrySet() {
      serializations++;
      return super.entrySet();
    }
  }
}
