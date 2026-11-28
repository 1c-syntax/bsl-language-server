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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт раскладки документации диагностик.
 * <p>
 * У каждой диагностики — страница в обеих локалях, и страниц без диагностики нет. Раздел описания
 * назван по шаблону: генератор документации ({@code updateDiagnosticDocs}) находит место для
 * автоматических блоков по этому заголовку, а при другом заголовке дублирует шапку страницы.
 */
class DiagnosticDocsLayoutTest {

  private static final Path DIAGNOSTICS_SOURCES =
    Path.of("src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics");
  private static final Path RU_DOCS = Path.of("docs/diagnostics");
  private static final Path EN_DOCS = Path.of("docs/en/diagnostics");
  private static final String INDEX = "index";

  @Test
  void everyDiagnosticHasPageInBothLocalesAndNoPageIsOrphan() throws IOException {
    var diagnostics = diagnosticCodes();

    assertThat(pages(RU_DOCS)).as("страницы docs/diagnostics").isEqualTo(diagnostics);
    assertThat(pages(EN_DOCS)).as("страницы docs/en/diagnostics").isEqualTo(diagnostics);
  }

  @Test
  void descriptionSectionFollowsTemplate() throws IOException {
    var deviations = new ArrayList<String>();
    deviations.addAll(withoutHeading(RU_DOCS, "## Описание диагностики"));
    deviations.addAll(withoutHeading(EN_DOCS, "## Description"));

    assertThat(deviations).isEmpty();
  }

  private static Set<String> diagnosticCodes() throws IOException {
    try (Stream<Path> files = Files.list(DIAGNOSTICS_SOURCES)) {
      return files
        .map(file -> file.getFileName().toString())
        .filter(name -> name.endsWith("Diagnostic.java"))
        .map(name -> name.substring(0, name.length() - ".java".length()))
        .filter(DiagnosticDocsLayoutTest::isDiagnostic)
        .map(className -> className.substring(0, className.length() - "Diagnostic".length()))
        .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  private static boolean isDiagnostic(String simpleClassName) {
    try {
      var diagnosticClass = Class.forName(DiagnosticDocsLayoutTest.class.getPackageName() + "." + simpleClassName);
      return diagnosticClass.isAnnotationPresent(DiagnosticMetadata.class);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Set<String> pages(Path docs) throws IOException {
    try (Stream<Path> files = Files.list(docs)) {
      return files
        .map(file -> file.getFileName().toString())
        .filter(name -> name.endsWith(".md"))
        .map(name -> name.substring(0, name.length() - ".md".length()))
        .filter(page -> !page.equals(INDEX))
        .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  private static Set<String> withoutHeading(Path docs, String heading) throws IOException {
    var result = new TreeSet<String>();
    for (var page : pages(docs)) {
      var file = docs.resolve(page + ".md");
      if (Files.readAllLines(file).stream().noneMatch(line -> line.strip().equals(heading))) {
        result.add(file + ": нет раздела «" + heading + "»");
      }
    }
    return result;
  }
}
