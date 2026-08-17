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
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Регистрация и удаление рабочих папок MCP в общем {@link ServerContextProvider}.
 * Индексация выполняется так же, как в {@code analyze}.
 * <p>
 * У папки есть <b>владельцы</b>: явная регистрация и MCP roots. Папка остаётся зарегистрированной,
 * пока её держит хотя бы один владелец, поэтому исчезновение корня из roots не отбирает папку,
 * зарегистрированную явно, и наоборот. Единственный источник правды о владении — этот бин;
 * отдельных копий состояния заводить нельзя, иначе они разойдутся с реальным набором папок.
 * Владение при этом вторично: папка, убранная из контекста мимо этого бина, теряет владельцев.
 * <p>
 * Удаляются <b>только папки, созданные через MCP</b>. Папки из других источников — прежде всего
 * workspace folders LSP-клиента в режимах {@code lsp --mcp}/{@code websocket --mcp} — MCP забирать
 * не вправе: их удаление снесло бы рабочий контекст редактора, а вернуть его клиент не может.
 * <p>
 * Идентичность папки — URI, под которым она лежит в {@link ServerContextProvider}, а не путь:
 * {@code Path.toUri()} добавляет завершающий слэш только пока каталог существует, поэтому у
 * удалённого с диска каталога тот же {@link Path} даёт уже другой URI.
 * <p>
 * Все изменения набора сериализованы на мониторе бина: источников несколько, а регистрация —
 * «проверить и добавить» с долгой индексацией между шагами, так что параллельные вызовы иначе
 * индексировали бы один каталог дважды.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpWorkspaceBootstrap {

  private final LanguageServerConfiguration configuration;
  private final ServerContextProvider serverContextProvider;

  /**
   * Папки, удерживаемые явной регистрацией ({@code register_workspace_folder}).
   */
  private final Set<URI> ownedByTool = new HashSet<>();

  /**
   * Папки, удерживаемые корнями, объявленными клиентом через MCP roots.
   */
  private final Set<URI> ownedByRoots = new HashSet<>();

  /**
   * Папки, добавленные в контекст сервера этим бином, — единственные, которые он вправе удалять.
   */
  private final Set<URI> createdHere = new HashSet<>();

  /**
   * Взять каталог во владение явной регистрацией, проиндексировав его, если он ещё не
   * зарегистрирован.
   * <p>
   * Атомарно относительно других изменений набора рабочих папок.
   *
   * @param srcDir Каталог исходных файлов.
   * @param workspaceName Имя рабочей папки; {@code null} — взять из последнего сегмента URI.
   * @return {@code true}, если каталог был зарегистрирован ранее и индексация не выполнялась.
   * @throws RuntimeException Если индексация не удалась; папка при этом не остаётся
   *   зарегистрированной наполовину.
   */
  public synchronized boolean register(Path srcDir, @Nullable String workspaceName) {
    forgetGoneFolders();
    var workspaceUri = srcDir.toUri();
    var alreadyRegistered = serverContextProvider.getAllContexts().containsKey(workspaceUri);

    if (!alreadyRegistered) {
      index(srcDir, workspaceName);
    }

    ownedByTool.add(workspaceUri);
    return alreadyRegistered;
  }

  /**
   * Отказаться от явного владения папкой и удалить её, если её больше никто не удерживает.
   * <p>
   * Атомарно относительно других изменений набора рабочих папок.
   *
   * @param workspaceUri URI ранее зарегистрированной рабочей папки — в том виде, в котором она
   *   лежит в {@link ServerContextProvider}.
   * @return {@code true}, если папка удалена; {@code false}, если она осталась —
   *   её продолжает удерживать корень, объявленный клиентом через MCP roots.
   * @throws IllegalArgumentException Если папку добавили не через MCP: забирать чужую папку
   *   (например, workspace folder LSP-клиента) этот бин не вправе.
   */
  public synchronized boolean unregister(URI workspaceUri) {
    forgetGoneFolders();

    if (!createdHere.contains(workspaceUri)) {
      throw new IllegalArgumentException("Workspace folder `" + workspaceUri
        + "` was not registered through MCP: it comes from the editor (an LSP workspace folder), "
        + "and unregistering it here would tear down the editor's context. Only folders registered "
        + "with `" + McpWorkspaceFolders.REGISTER_TOOL + "` can be removed.");
    }

    ownedByTool.remove(workspaceUri);

    if (ownedByRoots.contains(workspaceUri)) {
      LOGGER.info("Workspace folder `{}` stays registered: still declared through MCP roots", workspaceUri);
      return false;
    }

    remove(workspaceUri);
    return true;
  }

  /**
   * Привести набор папок, удерживаемых MCP roots, к объявленному клиентом.
   * <p>
   * Новые корни индексируются, исчезнувшие — освобождаются; папка удаляется, только если её не
   * удерживает явная регистрация. Атомарно относительно других изменений набора рабочих папок.
   *
   * @param declaredRoots Каталоги корней, объявленных клиентом.
   */
  public synchronized void syncRoots(Collection<Path> declaredRoots) {
    forgetGoneFolders();
    var declaredUris = declaredRoots.stream().map(Path::toUri).collect(Collectors.toSet());

    declaredRoots.stream()
      .filter(srcDir -> !ownedByRoots.contains(srcDir.toUri()))
      .forEach(this::addRoot);

    Set.copyOf(ownedByRoots).stream()
      .filter(workspaceUri -> !declaredUris.contains(workspaceUri))
      .forEach(this::dropRoot);
  }

  /**
   * Забыть владение папками, которых в контексте сервера уже нет: их могли убрать мимо этого бина —
   * например, LSP-клиент через {@code workspace/didChangeWorkspaceFolders}. Иначе просроченная
   * запись позже помешала бы удалить одноимённую папку, зарегистрированную заново.
   */
  private void forgetGoneFolders() {
    var registered = serverContextProvider.getAllContexts().keySet();
    ownedByTool.retainAll(registered);
    ownedByRoots.retainAll(registered);
    createdHere.retainAll(registered);
  }

  private void addRoot(Path srcDir) {
    var workspaceUri = srcDir.toUri();
    try {
      if (!serverContextProvider.getAllContexts().containsKey(workspaceUri)) {
        var indexed = index(srcDir, null);
        LOGGER.info("Workspace folder `{}` added from MCP root ({} files)", srcDir, indexed);
      }
      ownedByRoots.add(workspaceUri);
    } catch (RuntimeException e) {
      // Не берём во владение то, что не удалось проиндексировать: следующая синхронизация повторит.
      LOGGER.warn("Failed to add workspace folder from MCP root `{}`", srcDir, e);
    }
  }

  private void dropRoot(URI workspaceUri) {
    ownedByRoots.remove(workspaceUri);
    if (ownedByTool.contains(workspaceUri)) {
      LOGGER.info("Workspace folder `{}` stays registered: registered explicitly", workspaceUri);
      return;
    }
    if (!createdHere.contains(workspaceUri)) {
      // Корень лишь ссылался на чужую папку (workspace folder LSP-клиента) — забирать её нельзя.
      LOGGER.info("Workspace folder `{}` stays registered: added outside MCP", workspaceUri);
      return;
    }
    try {
      remove(workspaceUri);
      LOGGER.info("Workspace folder `{}` removed (MCP root gone)", workspaceUri);
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to remove workspace folder `{}`", workspaceUri, e);
    }
  }

  /**
   * Зарегистрировать каталог исходников как рабочую папку и проиндексировать его.
   * <p>
   * Уже зарегистрированный каталог индексируется повторно — если это нежелательно,
   * используйте {@link #register(Path, String)}.
   *
   * @param srcDir Каталог исходных файлов.
   * @return Количество проиндексированных файлов.
   */
  public synchronized int index(Path srcDir) {
    return index(srcDir, null);
  }

  /**
   * Зарегистрировать каталог исходников как рабочую папку под заданным именем
   * и проиндексировать его.
   *
   * @param srcDir Каталог исходных файлов.
   * @param workspaceName Имя рабочей папки; {@code null} — взять из последнего сегмента URI.
   * @return Количество проиндексированных файлов.
   * @throws RuntimeException Если индексация не удалась. Папка, добавленная этим вызовом,
   *   откатывается — иначе наполовину собранная считалась бы зарегистрированной и повторный
   *   {@link #register(Path, String)} вернул бы «уже зарегистрирована».
   */
  public synchronized int index(Path srcDir, @Nullable String workspaceName) {
    var workspaceUri = srcDir.toUri();
    var addedHere = !serverContextProvider.getAllContexts().containsKey(workspaceUri);
    var serverContext = serverContextProvider.addWorkspace(workspaceUri, workspaceName);

    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      configuration.update(new File(""));

      var files = new ArrayList<>(BSLFiles.listBslFiles(srcDir, configuration.getExcludePaths()));
      serverContext.populateContext(files);
      LOGGER.info("Indexed {} files in workspace `{}`", files.size(), srcDir);
      if (addedHere) {
        createdHere.add(workspaceUri);
      }
      return files.size();
    } catch (RuntimeException e) {
      if (addedHere) {
        rollbackQuietly(workspaceUri);
      }
      throw e;
    }
  }

  private void rollbackQuietly(URI workspaceUri) {
    try {
      remove(workspaceUri);
    } catch (RuntimeException suppressed) {
      LOGGER.warn("Failed to roll back partially indexed workspace folder `{}`", workspaceUri, suppressed);
    }
  }

  /**
   * Удалить рабочую папку из общего контекста сервера.
   *
   * @param workspaceUri URI ранее добавленной рабочей папки — в том виде, в котором она лежит
   *   в {@link ServerContextProvider}.
   */
  public synchronized void remove(URI workspaceUri) {
    createdHere.remove(workspaceUri);
    ownedByTool.remove(workspaceUri);
    ownedByRoots.remove(workspaceUri);

    var uri = workspaceUri.toString();
    serverContextProvider.removeWorkspace(new WorkspaceFolder(uri, uri));
  }
}
