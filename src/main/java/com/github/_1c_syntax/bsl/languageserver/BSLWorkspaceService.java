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
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.bsl.languageserver.utils.PathExclusionUtils;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
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
    return CompletableFutures.computeAsync(
      executor,
      cancelChecker -> Either.forRight(symbolProvider.getSymbols(params, cancelChecker))
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
          runInWorkspaceContext(uri, () -> {
            switch (fileEvent.getType()) {
              case Deleted -> handleDeletedFileEvent(uri);
              case Created -> handleCreatedFileEvent(uri);
              case Changed -> handleChangedFileEvent(uri);
            }
          });
        }
      },
      executor
    );
  }

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает уведомление {@code workspace/didCreateFiles}: для каждого URI добавляет
   * документ(ы) в контекст сервера аналогично событию создания в
   * {@code workspace/didChangeWatchedFiles}. Пути из {@code excludePaths} и открытые в редакторе
   * документы учитываются так же, как в существующих обработчиках.
   *
   * @param params параметры уведомления о созданных файлах
   */
  @Override
  public void didCreateFiles(CreateFilesParams params) {
    CompletableFuture.runAsync(
      () -> {
        for (var file : params.getFiles()) {
          var uri = Absolute.uri(file.getUri());
          runInWorkspaceContext(uri, () -> handleCreatedFileEvent(uri));
        }
      },
      executor
    );
  }

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает уведомление {@code workspace/didDeleteFiles}: для каждого URI удаляет
   * документ(ы) из контекста сервера. Для каталога удаление выполняется по префиксу URI
   * с границей {@code /} (переиспользуется логика обработки удаления каталога из
   * {@code workspace/didChangeWatchedFiles}).
   *
   * @param params параметры уведомления об удалённых файлах
   */
  @Override
  public void didDeleteFiles(DeleteFilesParams params) {
    CompletableFuture.runAsync(
      () -> {
        for (var file : params.getFiles()) {
          var uri = Absolute.uri(file.getUri());
          runInWorkspaceContext(uri, () -> handleDeletedFileEvent(uri));
        }
      },
      executor
    );
  }

  /**
   * {@inheritDoc}
   * <p>
   * Обрабатывает уведомление {@code workspace/didRenameFiles}: каждое переименование
   * выполняется атомарно как удаление документа(ов) по старому URI и создание по новому,
   * включая переименование каталогов. Пути из {@code excludePaths} и открытые в редакторе
   * документы учитываются так же, как в существующих обработчиках.
   *
   * @param params параметры уведомления о переименованных файлах
   */
  @Override
  public void didRenameFiles(RenameFilesParams params) {
    CompletableFuture.runAsync(
      () -> {
        for (var file : params.getFiles()) {
          var oldUri = Absolute.uri(file.getOldUri());
          var newUri = Absolute.uri(file.getNewUri());
          runInWorkspaceContext(oldUri, () -> handleDeletedFileEvent(oldUri));
          runInWorkspaceContext(newUri, () -> handleCreatedFileEvent(newUri));
        }
      },
      executor
    );
  }

  /**
   * Выполняет действие над файлом в контексте его workspace.
   * <p>
   * Резолвит контекст сервера и устанавливает workspace-контекст на текущем треде
   * (через {@link WorkspaceContextHolder}), чтобы workspace-scoped proxy beans корректно
   * разрешались в {@code @EventListener}-методах. Если workspace для URI не найден,
   * действие пропускается.
   *
   * @param uri    URI файла или каталога
   * @param action действие, выполняемое в установленном workspace-контексте
   */
  private void runInWorkspaceContext(URI uri, Runnable action) {
    serverContextProvider.getServerContext(uri).ifPresentOrElse(
      context -> {
        var workspaceUri = context.getWorkspaceUri();
        if (workspaceUri == null) {
          LOGGER.warn("No workspace URI for context, skipping file event: {}", uri);
          return;
        }
        WorkspaceContextHolder.run(workspaceUri, action);
      },
      () -> LOGGER.debug("No workspace found for file event, skipping: {}", uri)
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

    var file = toFile(uri);
    if (file != null && file.isDirectory()) {
      handleCreatedFolderEvent(context, file);
      return;
    }

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
   * Обрабатывает событие создания каталога в файловой системе.
   * <p>
   * Клиенты (в т.ч. VS Code) при создании или переименовании каталога присылают одно событие
   * с URI каталога без событий по вложенным файлам. Каталог рекурсивно обходится, и все найденные
   * BSL/OS-файлы добавляются в контекст сервера. Поиск файлов и учёт {@code excludePaths}
   * переиспользуют логику {@link BSLFiles#listBslFiles} — ту же,
   * что применяется при первичном наполнении контекста в {@link ServerContext#populateContext()}.
   *
   * @param context контекст сервера
   * @param folder  созданный каталог
   */
  private void handleCreatedFolderEvent(ServerContext context, File folder) {
    var excludePaths = getExcludePaths(context);
    var files = BSLFiles.listBslFiles(folder.toPath(), excludePaths);

    for (var file : files) {
      var fileUri = Absolute.uri(file.toURI());
      var documentContext = context.addDocument(fileUri);

      var isDocumentOpened = context.isDocumentOpened(documentContext);
      if (!isDocumentOpened) {
        context.rebuildDocument(documentContext);
        context.tryClearDocument(documentContext);
      }
    }
  }

  /**
   * Преобразует URI в {@link File}, если это файловый URI.
   *
   * @param uri URI файла или каталога
   * @return {@link File} или {@code null}, если URI не указывает на путь в файловой системе
   */
  @Nullable
  private static File toFile(URI uri) {
    try {
      return Paths.get(uri).toFile();
    } catch (IllegalArgumentException | FileSystemNotFoundException e) {
      LOGGER.debug("Не удалось преобразовать URI в путь файловой системы: {}", uri, e);
      return null;
    }
  }

  /**
   * Возвращает список {@code excludePaths} из конфигурации workspace {@code context}.
   *
   * @param context контекст сервера
   * @return список паттернов исключения
   */
  private static List<String> getExcludePaths(ServerContext context) {
    return context.getLanguageServerConfiguration().getExcludePaths();
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
