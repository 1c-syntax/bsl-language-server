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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Тесты {@link PathExclusionUtils}: одиночная проверка {@code isExcluded} и фабрика {@code filters}. */
class PathExclusionUtilsTest {

  private static final String GIT = ".git";
  private static final String NODE_MODULES = "node_modules";
  private static final String GLOB_ALL_BSL = "**/*.bsl";
  private static final String PROJECT = "project";
  private static final String MODULE_BSL = "Module.bsl";

  /** {@code patterns == null} — путь не считается исключённым. */
  @Test
  void isExcludedReturnsFalseWhenPatternsIsNull() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve(GIT).resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(path, null)).isFalse();
  }

  /** Пустой список паттернов — путь не считается исключённым. */
  @Test
  void isExcludedReturnsFalseWhenPatternsIsEmpty() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve(GIT).resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(path, List.of())).isFalse();
  }

  /** Простое имя сегмента (например, ".git") должно матчить путь, содержащий такой сегмент. */
  @Test
  void isExcludedReturnsTrueWhenPathMatchesSimpleNamePattern() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve(GIT).resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(path, List.of(GIT))).isTrue();
  }

  /** Glob "**\/.git/**" корректно матчит файл в любой вложенной .git-директории. */
  @Test
  void isExcludedReturnsTrueWhenPathMatchesGlobPattern() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve("repo").resolve(GIT).resolve("refs").resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(path, List.of("**/.git/**"))).isTrue();
  }

  /** Если путь не совпадает ни с одним паттерном — возвращается false. */
  @Test
  void isExcludedReturnsFalseWhenPathDoesNotMatchAnyPattern() {
    Path path = Path.of(PROJECT).toAbsolutePath()
      .resolve("CommonModules").resolve("Module").resolve("Ext").resolve(MODULE_BSL);
    assertThat(PathExclusionUtils.isExcluded(path, List.of(GIT, NODE_MODULES))).isFalse();
  }

  /** {@code null}/blank-элементы в списке игнорируются, остальные паттерны применяются. */
  @Test
  void isExcludedSkipsNullAndBlankPatterns() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve(GIT).resolve("HEAD");
    var patternsWithNullAndBlank = new ArrayList<String>();
    patternsWithNullAndBlank.add(null);
    patternsWithNullAndBlank.add("  ");
    patternsWithNullAndBlank.add(GIT);
    assertThat(PathExclusionUtils.isExcluded(path, patternsWithNullAndBlank)).isTrue();
  }

  /** Простое имя матчит путь, содержащий каталог с этим именем (даже если файл лежит глубже). */
  @Test
  void isExcludedMatchesDirectorySegmentWithSimpleName() {
    Path path = Path.of("workspace").toAbsolutePath().resolve(NODE_MODULES).resolve("pkg").resolve("index.bsl");
    assertThat(PathExclusionUtils.isExcluded(path, List.of(NODE_MODULES))).isTrue();
  }

  /** Невалидный glob-паттерн не должен ронять выполнение — путь просто считается не исключённым. */
  @Test
  void isExcludedReturnsFalseWhenGlobPatternIsInvalid() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve("src").resolve(MODULE_BSL);
    assertThat(PathExclusionUtils.isExcluded(path, List.of("**[invalid"))).isFalse();
  }

  /** Glob без префикса "**\/" автоматически дополняется им и матчит файл на любой глубине. */
  @Test
  void isExcludedAutoAddsRecursivePrefixForBareGlob() {
    Path path = Path.of(PROJECT).toAbsolutePath().resolve("sub").resolve("temp.tmp");
    assertThat(PathExclusionUtils.isExcluded(path, List.of("*.tmp"))).isTrue();
  }

  /** {@code null}, пустой список и список из blank-строк дают синглтон {@link PathExclusionUtils.ExclusionFilters#NONE}. */
  @Test
  void filtersReturnsNoneWhenPatternsAreNullOrBlank() {
    assertThat(PathExclusionUtils.filters(null)).isSameAs(PathExclusionUtils.ExclusionFilters.NONE);
    assertThat(PathExclusionUtils.filters(List.of())).isSameAs(PathExclusionUtils.ExclusionFilters.NONE);

    var blankOnly = new ArrayList<String>();
    blankOnly.add(null);
    blankOnly.add("   ");
    assertThat(PathExclusionUtils.filters(blankOnly)).isSameAs(PathExclusionUtils.ExclusionFilters.NONE);
  }

  /** Синглтон {@code NONE} использует {@link TrueFileFilter#INSTANCE} для обоих фильтров. */
  @Test
  void filtersNoneExposesTrueFileFilter() {
    var filters = PathExclusionUtils.filters(null);
    assertThat(filters.directoryFilter()).isSameAs(TrueFileFilter.INSTANCE);
    assertThat(filters.fileFilter()).isSameAs(TrueFileFilter.INSTANCE);
  }

  /** Простое имя сегмента применяется и к directoryFilter, и к fileFilter. */
  @Test
  void filtersSimpleNameAppliesToBothDirectoriesAndFiles() {
    Path root = Path.of(PROJECT).toAbsolutePath();
    var filters = PathExclusionUtils.filters(List.of(GIT));

    Path gitDir = root.resolve(GIT);
    Path gitFile = gitDir.resolve("HEAD");
    Path otherDir = root.resolve("CommonModules");
    Path otherFile = otherDir.resolve(MODULE_BSL);

    assertThat(filters.directoryFilter().accept(gitDir.toFile())).isFalse();
    assertThat(filters.fileFilter().accept(gitFile.toFile())).isFalse();
    assertThat(filters.directoryFilter().accept(otherDir.toFile())).isTrue();
    assertThat(filters.fileFilter().accept(otherFile.toFile())).isTrue();
  }

  /** Паттерн вида "**\/.git/**" обрезает обход целиком на самой .git-директории. */
  @Test
  void filtersWithDoubleStarSuffixPrunesMatchingDirectory() {
    Path root = Path.of(PROJECT).toAbsolutePath();
    var filters = PathExclusionUtils.filters(List.of("**/.git/**"));

    Path gitDir = root.resolve("repo").resolve(GIT);
    Path gitFile = gitDir.resolve("refs").resolve("HEAD");
    Path keepDir = root.resolve("repo").resolve("src");
    Path keepFile = keepDir.resolve(MODULE_BSL);

    assertThat(filters.directoryFilter().accept(gitDir.toFile())).isFalse();
    assertThat(filters.fileFilter().accept(gitFile.toFile())).isFalse();
    assertThat(filters.directoryFilter().accept(keepDir.toFile())).isTrue();
    assertThat(filters.fileFilter().accept(keepFile.toFile())).isTrue();
  }

  /** Trailing slash ("build/") делает паттерн directory-only — файлы внутри не отфильтровываются индивидуально. */
  @Test
  void filtersWithTrailingSlashIsDirectoryOnly() {
    Path root = Path.of(PROJECT).toAbsolutePath();
    var filters = PathExclusionUtils.filters(List.of("build/"));

    Path buildDir = root.resolve("build");
    Path buildFile = buildDir.resolve("output.bsl");

    assertThat(filters.directoryFilter().accept(buildDir.toFile())).isFalse();
    assertThat(filters.fileFilter().accept(buildFile.toFile())).isTrue();
  }

  /** Невалидный glob полностью игнорируется и приводит к синглтону NONE. */
  @Test
  void filtersIgnoreInvalidGlobPattern() {
    var filters = PathExclusionUtils.filters(List.of("**[invalid"));
    assertThat(filters).isSameAs(PathExclusionUtils.ExclusionFilters.NONE);
  }

  /** Glob "*.tmp" автоматически становится "**\/*.tmp" и матчит вложенные файлы. */
  @Test
  void filtersAutoPrefixBareGlobMatchesAtAnyDepth() {
    Path root = Path.of(PROJECT).toAbsolutePath();
    var filters = PathExclusionUtils.filters(List.of("*.tmp"));

    Path tempFile = root.resolve("sub").resolve("foo.tmp");
    Path bslFile = root.resolve("sub").resolve("foo.bsl");

    assertThat(filters.fileFilter().accept(tempFile.toFile())).isFalse();
    assertThat(filters.fileFilter().accept(bslFile.toFile())).isTrue();
  }

  /** Сам корневой каталог не должен исключаться даже паттерном "**\/*.bsl". */
  @Test
  void filtersDoubleStarPatternKeepsRootItself() {
    Path root = Path.of(PROJECT).toAbsolutePath();
    var filters = PathExclusionUtils.filters(List.of(GLOB_ALL_BSL));

    assertThat(filters.directoryFilter().accept(root.toFile())).isTrue();
  }
}
