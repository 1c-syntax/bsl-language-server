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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.providers.CommandProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SymbolProvider;
import com.github._1c_syntax.bsl.languageserver.utils.PathExclusionUtils;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Сервис обработки запросов, связанных с рабочей областью.
 * <p>
 * Реализует интерфейс {@link WorkspaceService} из LSP4J и обрабатывает
 * запросы на уровне всей рабочей области (поиск символов, изменение конфигурации,
 * выполнение команд и мониторинг изменений файлов).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BSLWorkspaceService implements WorkspaceService {

  private final CommandProvider commandProvider;
  private final SymbolProvider symbolProvider;
  private final ServerContextProvider serverContextProvider;
  @Qualifier("workspaceServiceExecutor")
  private final ThreadPoolTaskExecutor executor;
  @Qualifier("populateContextExecutor")
  private final ExecutorService populateContextExecutor;

  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>,List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return CompletableFuture.supplyAsync(
      () -> Either.forRight(symbolProvider.getSymbols(params)),
      executor
    );
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    // no-op: configuration is managed through .bsl-language-server.json files
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    CompletableFuture.runAsync(
      () -> {
        for (var fileEvent : params.getChanges()) {
          var uri = Absolute.uri(fileEvent.getUri());

          serverContextProvider.getServerContext(uri).ifPresentOrElse(
            context -> {
              var workspaceUri = context.getWorkspaceUri();
              if (workspaceUri == null) {
                LOGGER.warn("No workspace URI for context, skipping file event: {}", uri);
                return;
              }
              WorkspaceContextHolder.run(workspaceUri, () -> {
                switch (fileEvent.getType()) {
                  case Deleted -> handleDeletedFileEvent(uri);
                  case Created -> handleCreatedFileEvent(uri);
                  case Changed -> handleChangedFileEvent(uri);
                }
              });
            },
            () -> LOGGER.debug("No workspace found for file event, skipping: {}", uri)
          );
        }
      },
      executor
    );
  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
    var arguments = commandProvider.extractArguments(params);

    return CompletableFuture.supplyAsync(
      () -> commandProvider.executeCommand(arguments),
      executor
    );
  }

  @Override
  public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    CompletableFuture.runAsync(
      () -> {
        var event = params.getEvent();

        // Remove old workspace folders
        event.getRemoved().forEach(serverContextProvider::removeWorkspace);

        // Add new workspace folders
        event.getAdded().forEach((WorkspaceFolder folder) -> {
          var uri = Absolute.uri(folder.getUri());
          var serverContext = serverContextProvider.addWorkspace(folder);
          WorkspaceContextHolder.run(uri,
            () -> CompletableFuture.runAsync(serverContext::populateContext, populateContextExecutor));
        });

        LOGGER.info("Workspace folders changed. Added: {}, Removed: {}",
          event.getAdded().size(), event.getRemoved().size());
      },
      executor
    );
  }

  /**
   * Обрабатывает событие удаления файла из файловой системы.
   * <p>
   * Если файл был открыт в редакторе, сначала закрывает его и очищает вторичные данные.
   * Затем полностью удаляет документ из контекста сервера, включая все связанные метаданные.
   * <p>
   * Если документ с таким URI в контексте отсутствует, URI трактуется как удаленный каталог:
   * клиенты (в т.ч. VS Code) при удалении каталога присылают одно событие Deleted с URI каталога
   * без событий по вложенным файлам.
   *
   * @param uri URI удаленного файла или каталога
   */
  private void handleDeletedFileEvent(URI uri) {
    var context = getContextForDocument(uri);
    var documentContext = context.getDocument(uri);
    if (documentContext == null) {
      handleDeletedFolderEvent(context, uri);
      return;
    }

    removeDocument(context, uri, documentContext);
  }

  /**
   * Обрабатывает событие удаления каталога из файловой системы.
   * <p>
   * Удаляет из контекста сервера все документы, расположенные внутри каталога.
   * Принадлежность каталогу определяется по префиксу URI с границей {@code /},
   * чтобы не зацепить каталог-«тёзку» с общим строковым префиксом.
   *
   * @param context   контекст сервера
   * @param folderUri URI удаленного каталога
   */
  private void handleDeletedFolderEvent(ServerContext context, URI folderUri) {
    var folderUriString = folderUri.toString();
    var prefix = folderUriString.endsWith("/") ? folderUriString : folderUriString + "/";

    var deletedUris = context.getDocuments().keySet().stream()
      .filter(documentUri -> documentUri.toString().startsWith(prefix))
      .toList();

    for (var documentUri : deletedUris) {
      var documentContext = context.getDocument(documentUri);
      if (documentContext != null) {
        removeDocument(context, documentUri, documentContext);
      }
    }
  }

  /**
   * Удаляет документ из контекста сервера, предварительно закрыв его, если он открыт в редакторе.
   *
   * @param context         контекст сервера
   * @param uri             URI документа
   * @param documentContext контекст документа
   */
  private static void removeDocument(ServerContext context, URI uri, DocumentContext documentContext) {
    var isDocumentOpened = context.isDocumentOpened(documentContext);
    if (isDocumentOpened) {
      context.closeDocument(documentContext);
    }
    context.removeDocument(uri);
  }

  /**
   * Обрабатывает событие создания нового файла в файловой системе.
   * <p>
   * Добавляет файл в контекст сервера. Если файл не открыт в редакторе,
   * выполняет его парсинг и анализ, после чего сразу очищает вторичные данные
   * для экономии памяти. Для открытых файлов обработка пропускается,
   * т.к. их содержимое управляется через события textDocument/didOpen.
   * Пути, попадающие под {@code excludePaths} из конфигурации, пропускаются.
   *
   * @param uri URI созданного файла
   */
  private void handleCreatedFileEvent(URI uri) {
    var context = getContextForDocument(uri);
    if (isExcludedPath(context, uri)) {
      return;
    }
    var documentContext = context.addDocument(uri);

    var isDocumentOpened = context.isDocumentOpened(documentContext);
    if (!isDocumentOpened) {
      context.rebuildDocument(documentContext);
      context.tryClearDocument(documentContext);
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
   * Если файл отсутствует в контексте, добавляет его и обрабатывает аналогично созданному;
   * при этом пути из {@code excludePaths} пропускаются.
   *
   * @param uri URI измененного файла
   */
  private void handleChangedFileEvent(URI uri) {
    var context = getContextForDocument(uri);
    var documentContext = context.getDocument(uri);
    if (documentContext == null) {
      if (isExcludedPath(context, uri)) {
        return;
      }
      documentContext = context.addDocument(uri);
    }

    var isDocumentOpened = context.isDocumentOpened(documentContext);
    if (!isDocumentOpened) {
      context.rebuildDocument(documentContext);
      context.tryClearDocument(documentContext);
    }
  }

  /**
   * Получить контекст сервера для документа.
   *
   * @param uri URI документа
   * @return контекст сервера
   */
  private ServerContext getContextForDocument(URI uri) {
    return serverContextProvider.getServerContext(uri)
      .orElseThrow(() -> new IllegalStateException("No workspace found for document: " + uri));
  }

  /** Проверяет, входит ли путь по uri в excludePaths конфигурации workspace {@code context}. */
  private boolean isExcludedPath(ServerContext context, URI uri) {
    var cfg = context.getLanguageServerConfiguration();
    if (cfg == null) {
      return false;
    }
    var patterns = cfg.getExcludePaths();
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    try {
      var path = Absolute.path(Paths.get(uri));
      return PathExclusionUtils.isExcluded(path, patterns);
    } catch (IllegalArgumentException e) {
      LOGGER.debug("Не удалось вычислить путь для проверки исключений, путь не считается исключённым: {}", uri, e);
      return false;
    }
  }
}
