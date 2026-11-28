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
package com.github._1c_syntax.bsl.languageserver.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сторож канонических реализаций: рецепт, у которого есть единственный владелец, не должен
 * появляться в исходниках заново. Каждый случай — образец кода, который был сведён к
 * каноническому помощнику, и файлы, где этот образец и есть реализация.
 * <p>
 * Проверка текстовая и намеренно узкая: образец описывает именно ту запись, что была
 * скопирована, а не всё семантически похожее, — ложные срабатывания ценнее не делать, чем
 * ловить всё. Новый канонический помощник с риском повторной копии — новый случай здесь.
 */
class CanonicalImplementationsTest {

  private static final Path SOURCES = Path.of("src/main/java/com/github/_1c_syntax/bsl/languageserver");

  /**
   * Образец и его владельцы.
   *
   * @param canonical каноническая реализация — что использовать вместо копии.
   * @param pattern   запись копии.
   * @param owners    файлы (относительно корня пакета), где запись — сама реализация.
   */
  record Case(String canonical, Pattern pattern, Set<String> owners) {
    @Override
    public String toString() {
      return canonical;
    }
  }

  static Stream<Case> cases() {
    return Stream.of(
      new Case("TypeRef.UNDEFINED",
        Pattern.compile("new TypeRef\\(TypeKind\\.PRIMITIVE, \"Неопределено\"\\)"),
        Set.of("types/model/TypeRef.java")),
      new Case("MemberDescriptor.returnType()",
        Pattern.compile("\\.returnTypes\\(\\)\\.refs\\(\\)\\.stream\\(\\)\\.findFirst\\(\\)\\.orElse\\(TypeRef\\.UNKNOWN\\)"),
        Set.of()),
      new Case("MethodSymbol.getParameterDefinition(name)",
        Pattern.compile("getParameters\\(\\)\\.stream\\(\\)\\s*\\.filter\\(\\w+ -> \\w+\\.getName\\(\\)\\.equalsIgnoreCase"),
        Set.of("context/symbol/MethodSymbol.java")),
      new Case("TypeRegistry.findSpecialization(generic, argument)",
        Pattern.compile("resolve\\(TypeRef\\.specialize\\("),
        // PlaceholderBinder проверяет существование под произвольным набором подстановок
        Set.of("types/registry/TypeRegistry.java", "types/registry/PlaceholderBinder.java")),
      new Case("TypeRegistry.registerConfigurationType(ru, en)",
        Pattern.compile("registerConfigurationType\\(\\w+\\);\\s*(if \\([^\\n]*\\) \\{\\s*)?\\w+\\.registerConfigurationTypeAlias"),
        Set.of()),
      new Case("DiagnosticInfosFactory.createDiagnosticInfos",
        Pattern.compile("new DiagnosticInfo\\("),
        Set.of("diagnostics/infrastructure/DiagnosticInfosFactory.java")),
      new Case("ReportFile.commit(reportName)",
        Pattern.compile("report saved to"),
        Set.of("reporters/ReportFile.java")),
      new Case("CompletionPriority.Fold",
        Pattern.compile("available \\|= \\w+ != UNAVAILABLE"),
        Set.of("completion/CompletionPriority.java")),
      new Case("ParameterDescriptor.withTypes",
        Pattern.compile("new ParameterDescriptor\\(\\w+\\.bilingualName\\(\\),"),
        Set.of()),
      new Case("SignatureDescriptor.withReturnTypes",
        Pattern.compile("new SignatureDescriptor\\(\\w+\\.parameters\\(\\),[^;]*\\w+\\.bilingualDescription\\(\\), \\w+\\.metadata\\(\\)\\)"),
        Set.of()),
      new Case("FormPlatformTypes.namedProperties",
        Pattern.compile("withBilingualName\\(FormPlatformTypes\\.neutral\\([^)]*\\)\\)\\s*"
          + "\\.withBilingualDescription\\(FormPlatformTypes\\.bilingual\\("),
        Set.of()),
      new Case("CollectionReturnsSpecializer.registerCollection",
        Pattern.compile(
          "registerDefaultElementTypes\\([^;]*;\\s*\\w+\\.inheritCollectionTraits\\([^;]*;[^}]*CollectionReturnsSpecializer\\.register\\("),
        Set.of("types/registry/CollectionReturnsSpecializer.java"))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void copiedRecipeDoesNotReappearOutsideItsOwner(Case recipe) throws IOException {
    try (var files = Files.walk(SOURCES)) {
      var offenders = files
        .filter(file -> file.toString().endsWith(".java"))
        .filter(file -> !recipe.owners().contains(SOURCES.relativize(file).toString().replace('\\', '/')))
        .filter(file -> recipe.pattern().matcher(read(file)).find())
        .map(file -> SOURCES.relativize(file).toString())
        .toList();

      assertThat(offenders)
        .as("копии рецепта вместо %s", recipe.canonical())
        .isEmpty();
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
