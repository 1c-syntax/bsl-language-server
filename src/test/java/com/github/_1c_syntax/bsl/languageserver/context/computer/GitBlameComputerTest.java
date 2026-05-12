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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.jgit.api.Git;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GitBlameComputerTest {

  @TempDir
  Path tempDir;

  @Test
  void testEmptyIgnoredAuthors_returnsEmptyData() throws Exception {
    // given
    var testFile = tempDir.resolve("test.bsl");
    Files.writeString(testFile, "А = 1;\n");

    // when
    var data = new GitBlameComputer(testFile.toUri(), Set.of()).compute();

    // then
    var diagnostic = createDiagnostic(0);
    assertThat(data.diagnosticShouldBeIgnored(diagnostic)).isFalse();
  }

  @Test
  void testFileNotInGitRepo_returnsEmptyData() throws Exception {
    // given - file not in any git repo (tempDir has no .git)
    var testFile = tempDir.resolve("test.bsl");
    Files.writeString(testFile, "А = 1;\n");

    // when
    var data = new GitBlameComputer(testFile.toUri(), Set.of("author@example.com")).compute();

    // then
    var diagnostic = createDiagnostic(0);
    assertThat(data.diagnosticShouldBeIgnored(diagnostic)).isFalse();
  }

  @Test
  void testIgnoredAuthor_diagnosticFiltered() throws Exception {
    // given - a git repo with a committed file
    var gitDir = tempDir.resolve("repo");
    Files.createDirectories(gitDir);

    try (var git = Git.init().setDirectory(gitDir.toFile()).call()) {
      git.getRepository().getConfig().setString("user", null, "email", "author@example.com");
      git.getRepository().getConfig().setString("user", null, "name", "Test Author");
      git.getRepository().getConfig().save();

      var testFile = gitDir.resolve("test.bsl");
      Files.writeString(testFile, "А = 1;\nБ = 2;\nВ = 3;\n");

      git.add().addFilepattern("test.bsl").call();
      git.commit()
        .setAuthor("Test Author", "author@example.com")
        .setCommitter("Test Author", "author@example.com")
        .setMessage("Initial commit")
        .call();

      // when
      var data = new GitBlameComputer(testFile.toUri(), Set.of("author@example.com")).compute();

      // then - all lines authored by the ignored author should be filtered
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(0))).isTrue();
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(1))).isTrue();
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(2))).isTrue();
    }
  }

  @Test
  void testNonIgnoredAuthor_diagnosticNotFiltered() throws Exception {
    // given - a git repo with a committed file
    var gitDir = tempDir.resolve("repo");
    Files.createDirectories(gitDir);

    try (var git = Git.init().setDirectory(gitDir.toFile()).call()) {
      git.getRepository().getConfig().setString("user", null, "email", "other@example.com");
      git.getRepository().getConfig().setString("user", null, "name", "Other Author");
      git.getRepository().getConfig().save();

      var testFile = gitDir.resolve("test.bsl");
      Files.writeString(testFile, "А = 1;\n");

      git.add().addFilepattern("test.bsl").call();
      git.commit()
        .setAuthor("Other Author", "other@example.com")
        .setCommitter("Other Author", "other@example.com")
        .setMessage("Initial commit")
        .call();

      // when - ignored list contains a different email
      var data = new GitBlameComputer(testFile.toUri(), Set.of("author@example.com")).compute();

      // then - diagnostic should not be filtered
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(0))).isFalse();
    }
  }

  @Test
  void testMixedAuthors_onlyIgnoredAuthorLinesFiltered() throws Exception {
    // given - a git repo where lines were committed by different authors
    var gitDir = tempDir.resolve("repo");
    Files.createDirectories(gitDir);

    try (var git = Git.init().setDirectory(gitDir.toFile()).call()) {
      git.getRepository().getConfig().setString("user", null, "email", "first@example.com");
      git.getRepository().getConfig().setString("user", null, "name", "First Author");
      git.getRepository().getConfig().save();

      var testFile = gitDir.resolve("test.bsl");
      Files.writeString(testFile, "А = 1;\nБ = 2;\n");

      git.add().addFilepattern("test.bsl").call();
      git.commit()
        .setAuthor("First Author", "first@example.com")
        .setCommitter("First Author", "first@example.com")
        .setMessage("First commit")
        .call();

      // second author adds a third line
      Files.writeString(testFile, "А = 1;\nБ = 2;\nВ = 3;\n");
      git.add().addFilepattern("test.bsl").call();
      git.commit()
        .setAuthor("Second Author", "second@example.com")
        .setCommitter("Second Author", "second@example.com")
        .setMessage("Second commit")
        .call();

      // when - only first@example.com is ignored
      var data = new GitBlameComputer(testFile.toUri(), Set.of("first@example.com")).compute();

      // then - lines 0 and 1 by first author are filtered, line 2 by second author is not
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(0))).isTrue();
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(1))).isTrue();
      assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(2))).isFalse();
    }
  }

  @Test
  void testEmptyIgnoredLines_dataAlwaysReturnsFalse() {
    // given
    var data = GitBlameComputer.Data.empty();

    // then
    assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(0))).isFalse();
    assertThat(data.diagnosticShouldBeIgnored(createDiagnostic(100))).isFalse();
  }

  private static Diagnostic createDiagnostic(int line) {
    var diagnostic = new Diagnostic();
    diagnostic.setCode("TestDiagnostic");
    diagnostic.setRange(Ranges.create(line, 0, line, 0));
    return diagnostic;
  }
}
