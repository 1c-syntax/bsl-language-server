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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import com.github._1c_syntax.bsl.languageserver.providers.SemanticTokensProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Генерирует PNG-снапшоты семантической подсветки фикстур для Sentry Snapshots.
 * <p>
 * Каталог выгрузки задаётся системным свойством {@value #OUTPUT_DIR_PROPERTY}.
 */
@Tag("snapshots")
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SemanticTokensSnapshotsTest {

  private static final String OUTPUT_DIR_PROPERTY = "snapshots.outputDir";
  private static final Path FIXTURES = Path.of("src", "test", "resources", "semantictokens", "snapshots");
  private static final List<SemanticTokensTheme> THEMES = List.of(SemanticTokensTheme.LIGHT, SemanticTokensTheme.DARK);
  private static final String PREFERRED_FONT = "DejaVu Sans Mono";
  private static final int FONT_SIZE = 14;

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  private final SemanticTokensSnapshotRenderer renderer = new SemanticTokensSnapshotRenderer(font(), true);
  private final SnapshotWriter writer = new SnapshotWriter();

  @ParameterizedTest
  @MethodSource("fixtures")
  void rendersFixture(Path fixture) throws IOException {
    // given
    var outputDir = Path.of(System.getProperty(OUTPUT_DIR_PROPERTY, "build/snapshots/semantic-tokens"));
    var documentContext = TestUtils.getDocumentContextFromFile(fixture.toString());
    var params = new SemanticTokensParams(TestUtils.getTextDocumentIdentifier(documentContext.getUri()));
    var fileName = fixture.getFileName().toString();
    var language = fileName.substring(fileName.lastIndexOf('.') + 1);

    // when
    var semanticTokens = provider.getSemanticTokensFull(documentContext, params);
    var tokens = StyledTokensDecoder.decode(semanticTokens.getData(), legend);
    for (var theme : THEMES) {
      var image = renderer.render(documentContext.getContent(), tokens, theme);
      var metadata = new SnapshotMetadata(
        "%s (%s)".formatted(fileName, theme.name()),
        "semantic-tokens",
        theme.name(),
        Map.of("language", language, "theme", theme.name()),
        Map.of("fixture", FIXTURES.resolve(fileName).toString().replace('\\', '/'), "tokens", tokens.size())
      );
      writer.write(outputDir, fileName + "/" + theme.name(), image, metadata);
    }

    // then
    assertThat(tokens).isNotEmpty();
    for (var theme : THEMES) {
      assertThat(outputDir.resolve(fileName).resolve(theme.name() + ".png")).isRegularFile();
    }
  }

  static Stream<Path> fixtures() throws IOException {
    try (var files = Files.list(FIXTURES)) {
      return files
        .filter(Files::isRegularFile)
        .sorted()
        .toList()
        .stream();
    }
  }

  private static Font font() {
    var available = Arrays.asList(
      GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
    );
    var family = available.contains(PREFERRED_FONT) ? PREFERRED_FONT : Font.MONOSPACED;
    return new Font(family, Font.PLAIN, FONT_SIZE);
  }
}
