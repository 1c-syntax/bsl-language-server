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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathExclusionUtilsTest {

  @Test
  void isExcludedReturnsFalseWhenRootIsNull() {
    Path path = Path.of(".").toAbsolutePath();
    assertThat(PathExclusionUtils.isExcluded(null, path, List.of(".git"))).isFalse();
  }

  @Test
  void isExcludedReturnsFalseWhenPatternsIsNull() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve(".git").resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(root, path, null)).isFalse();
  }

  @Test
  void isExcludedReturnsFalseWhenPatternsIsEmpty() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve(".git").resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of())).isFalse();
  }

  @Test
  void isExcludedReturnsFalseWhenPathIsOutsideRoot() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.getParent().resolve("sibling").resolve("file.bsl");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of("other"))).isFalse();
  }

  @Test
  void isExcludedReturnsTrueWhenPathMatchesSimpleNamePattern() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve(".git").resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of(".git"))).isTrue();
  }

  @Test
  void isExcludedReturnsTrueWhenPathMatchesGlobPattern() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve("repo").resolve(".git").resolve("refs").resolve("HEAD");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of("**/.git/**"))).isTrue();
  }

  @Test
  void isExcludedReturnsFalseWhenPathDoesNotMatchAnyPattern() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve("CommonModules").resolve("Module").resolve("Ext").resolve("Module.bsl");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of(".git", "node_modules"))).isFalse();
  }

  @Test
  void isExcludedSkipsNullAndBlankPatterns() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve(".git").resolve("HEAD");
    var patternsWithNullAndBlank = new ArrayList<String>();
    patternsWithNullAndBlank.add(null);
    patternsWithNullAndBlank.add("  ");
    patternsWithNullAndBlank.add(".git");
    assertThat(PathExclusionUtils.isExcluded(root, path, patternsWithNullAndBlank)).isTrue();
  }

  @Test
  void isExcludedMatchesDirectorySegmentWithSimpleName() {
    Path root = Path.of("workspace").toAbsolutePath();
    Path path = root.resolve("node_modules").resolve("pkg").resolve("index.bsl");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of("node_modules"))).isTrue();
  }

  @Test
  void isExcludedWhenPathEqualsRootReturnsFalse() {
    Path root = Path.of("project").toAbsolutePath();
    assertThat(PathExclusionUtils.isExcluded(root, root, List.of(".git"))).isFalse();
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void isExcludedWhenPathOnDifferentDriveReturnsFalse() {
    Path root = FileSystems.getDefault().getPath("C:/project");
    Path path = FileSystems.getDefault().getPath("D:/other/file.bsl");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of("**/*.bsl"))).isFalse();
  }

  @Test
  void isExcludedWhenRelativePathIsEmptyReturnsFalse() {
    Path root = Path.of("project").toAbsolutePath();
    assertThat(PathExclusionUtils.isExcluded(root, root, List.of("**/*.bsl"))).isFalse();
  }

  @Test
  void isExcludedReturnsFalseWhenGlobPatternIsInvalid() {
    Path root = Path.of("project").toAbsolutePath();
    Path path = root.resolve("src").resolve("Module.bsl");
    assertThat(PathExclusionUtils.isExcluded(root, path, List.of("**[invalid"))).isFalse();
  }
}
