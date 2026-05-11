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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.lsp4j.Diagnostic;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Вычислитель игнорирования диагностик на основе данных git blame.
 * <p>
 * Для каждой строки файла определяет автора (по email из git blame)
 * и помечает строку как игнорируемую, если email автора входит
 * в список {@code ignoredAuthors} из конфигурации.
 */
@Slf4j
public class GitBlameComputer implements Computer<GitBlameComputer.Data> {

  private final URI uri;
  private final Set<String> ignoredAuthors;

  public GitBlameComputer(URI uri, Set<String> ignoredAuthors) {
    this.uri = uri;
    this.ignoredAuthors = ignoredAuthors;
  }

  @Override
  public Data compute() {
    if (ignoredAuthors.isEmpty()) {
      return Data.empty();
    }

    var file = new File(uri);
    if (!file.exists()) {
      return Data.empty();
    }

    try {
      var repoBuilder = new FileRepositoryBuilder();
      try (var repository = repoBuilder
        .findGitDir(file)
        .build()) {

        if (repository.getDirectory() == null) {
          return Data.empty();
        }

        var workTree = repository.getWorkTree().toPath();
        var relativePath = workTree.relativize(file.toPath())
          .toString()
          .replace('\\', '/');

        try (var git = new Git(repository)) {
          var blameResult = git.blame()
            .setFilePath(relativePath)
            .call();

          if (blameResult == null) {
            return Data.empty();
          }

          Set<Integer> ignoredLines = new HashSet<>();
          int lineCount = blameResult.getResultContents().size();

          for (int i = 0; i < lineCount; i++) {
            var author = blameResult.getSourceAuthor(i);
            if (author != null && ignoredAuthors.contains(author.getEmailAddress().toLowerCase(Locale.ROOT))) {
              ignoredLines.add(i); // JGit lines are 0-indexed, same as LSP
            }
          }

          return new Data(Collections.unmodifiableSet(ignoredLines));
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Failed to compute git blame for {}", uri, e);
      return Data.empty();
    }
  }

  /**
   * Результат вычисления игнорирования диагностик на основе git blame.
   */
  @AllArgsConstructor
  public static class Data {

    private static final Data EMPTY = new Data(Collections.emptySet());

    private final Set<Integer> ignoredLines;

    /**
     * @return Пустой экземпляр (нет игнорируемых строк).
     */
    public static Data empty() {
      return EMPTY;
    }

    /**
     * Проверить, должна ли диагностика быть проигнорирована на основе git blame.
     *
     * @param diagnostic Диагностика для проверки
     * @return {@code true}, если строка начала диагностики написана автором из списка игнорируемых
     */
    public boolean diagnosticShouldBeIgnored(Diagnostic diagnostic) {
      if (ignoredLines.isEmpty()) {
        return false;
      }
      int line = diagnostic.getRange().getStart().getLine();
      return ignoredLines.contains(line);
    }
  }
}
