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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.lsp4j.Diagnostic;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

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
    this.ignoredAuthors = Set.copyOf(ignoredAuthors);
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
      return computeWithGit(file);
    } catch (Exception e) {
      LOGGER.debug("Failed to compute git blame for {}", uri, e);
      return Data.empty();
    }
  }

  private Data computeWithGit(File file) throws Exception {
    var repoBuilder = new FileRepositoryBuilder();
    try (var repository = repoBuilder.findGitDir(file).build()) {
      if (repository.getDirectory() == null) {
        return Data.empty();
      }

      var workTree = repository.getWorkTree().toPath();
      var relativePath = workTree.relativize(file.toPath()).toString().replace('\\', '/');

      try (var git = new Git(repository)) {
        var blameResult = git.blame().setFilePath(relativePath).call();

        if (blameResult == null) {
          return Data.empty();
        }

        var lineCount = blameResult.getResultContents().size();
        var ignoredLines = IntStream.range(0, lineCount)
          .filter(i -> isAuthorIgnored(blameResult.getSourceAuthor(i)))
          .boxed()
          .collect(HashSet<Integer>::new, HashSet::add, HashSet::addAll);

        return new Data(Collections.unmodifiableSet(ignoredLines));
      }
    }
  }

  private boolean isAuthorIgnored(PersonIdent author) {
    return author != null && ignoredAuthors.contains(author.getEmailAddress().toLowerCase(Locale.ROOT));
  }

  /**
   * Результат вычисления игнорирования диагностик на основе git blame.
   */
  @AllArgsConstructor
  public static class Data {

    private final Set<Integer> ignoredLines;

    /**
     * @return Пустой экземпляр (нет игнорируемых строк).
     */
    public static Data empty() {
      return new Data(Collections.emptySet());
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
