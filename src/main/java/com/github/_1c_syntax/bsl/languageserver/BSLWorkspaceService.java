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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.providers.CommandProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SymbolProvider;
import com.github._1c_syntax.utils.Absolute;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.PropertyUtils;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервис обработки запросов, связанных с рабочей областью.
 * <p>
 * Реализует интерфейс {@link WorkspaceService} из LSP4J и обрабатывает
 * запросы на уровне всей рабочей области (поиск символов, изменение конфигурации,
 * выполнение команд и мониторинг изменений файлов).
 */
@Component
@RequiredArgsConstructor
public class BSLWorkspaceService implements WorkspaceService {

  private final LanguageServerConfiguration configuration;
  private final CommandProvider commandProvider;
  private final SymbolProvider symbolProvider;
  private final ServerContext serverContext;

  private final ExecutorService executorService = Executors.newCachedThreadPool(new CustomizableThreadFactory("workspace-service-"));

  @PreDestroy
  private void onDestroy() {
    executorService.shutdown();
  }

  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>,List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return CompletableFuture.supplyAsync(
      () -> Either.forRight(symbolProvider.getSymbols(params)),
      executorService
    );
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    var settings = params.getSettings();
    if (settings == null) {
      return;
    }
    try {
      PropertyUtils.copyProperties(configuration, settings);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    CompletableFuture.runAsync(
      () -> {
        for (var fileEvent : params.getChanges()) {
          var uri = Absolute.uri(fileEvent.getUri());

          switch (fileEvent.getType()) {
            case Deleted -> handleDeletedFileEvent(uri);
            case Created -> handleCreatedFileEvent(uri);
            case Changed -> handleChangedFileEvent(uri);
          }
        }
      },
      executorService
    );
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    var arguments = commandProvider.extractArguments(params);

    return CompletableFuture.supplyAsync(
      () -> commandProvider.executeCommand(arguments),
      executorService
    );
  }

  /**
   * Обрабатывает событие удаления файла из файловой системы.
   * <p>
   * Если файл был открыт в редакторе, сначала закрывает его и очищает вторичные данные.
   * Затем полностью удаляет документ из контекста сервера, включая все связанные метаданные.
   *
   * @param uri URI удаленного файла
   */
  private void handleDeletedFileEvent(URI uri) {
    var documentContext = serverContext.getDocument(uri);
    if (documentContext == null) {
      return;
    }

    var isDocumentOpened = serverContext.isDocumentOpened(documentContext);
    if (isDocumentOpened) {
      serverContext.closeDocument(documentContext);
    }
    serverContext.removeDocument(uri);
  }

  /**
   * Обрабатывает событие создания нового файла в файловой системе.
   * <p>
   * Добавляет файл в контекст сервера. Если файл не открыт в редакторе,
   * выполняет его парсинг и анализ, после чего сразу очищает вторичные данные
   * для экономии памяти. Для открытых файлов обработка пропускается,
   * т.к. их содержимое управляется через события textDocument/didOpen.
   *
   * @param uri URI созданного файла
   */
  private void handleCreatedFileEvent(URI uri) {
    var documentContext = serverContext.addDocument(uri);

    var isDocumentOpened = serverContext.isDocumentOpened(documentContext);
    if (!isDocumentOpened) {
      serverContext.rebuildDocument(documentContext);
      serverContext.tryClearDocument(documentContext);
    }
  }

  /**
   * Обрабатывает событие изменения файла в файловой системе.
   * <p>
   * Если файл уже есть в контексте сервера и не открыт в редакторе,
   * перечитывает его содержимое с диска, выполняет повторный парсинг и анализ,
   * после чего очищает вторичные данные. Для открытых файлов обработка пропускается,
   * т.к. их актуальное содержимое управляется через события textDocument/didChange.
   * <p>
   * Если файл отсутствует в контексте, добавляет его и обрабатывает аналогично созданному.
   *
   * @param uri URI измененного файла
   */
  private void handleChangedFileEvent(URI uri) {
    var documentContext = serverContext.getDocument(uri);
    if (documentContext == null) {
      documentContext = serverContext.addDocument(uri);
    }

    var isDocumentOpened = serverContext.isDocumentOpened(documentContext);
    if (!isDocumentOpened) {
      serverContext.rebuildDocument(documentContext);
      serverContext.tryClearDocument(documentContext);
    }
  }
}
