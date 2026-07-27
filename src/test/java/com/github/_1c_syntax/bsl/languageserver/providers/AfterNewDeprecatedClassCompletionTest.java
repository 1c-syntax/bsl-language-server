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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пункт автодополнения платформенного класса после {@code Новый} помечается
 * устаревшим, если тип устарел для целевой версии платформы — тем же правилом
 * {@code target >= deprecatedSinceVersion}, что и члены типов.
 * <p>
 * Метаданные типа приходят из синтакс-помощника, в JSON-fallback'е их нет,
 * поэтому здесь они регистрируются в реестре напрямую. Класс изолирован
 * ({@link CleanupContextBeforeClassAndAfterClass}), чтобы синтетическое
 * устаревание {@code Массив} не утекло в другие тесты.
 */
@CleanupContextBeforeClassAndAfterClass
class AfterNewDeprecatedClassCompletionTest extends AbstractServerContextAwareTest {

  private static final String CONTENT = "Х = Новый Масс";

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void deprecatedClassIsMarkedAndSortedBelowActualOnes() {
    // given — тип устарел с 8.3.5, целевая версия платформы выше
    var configuration = deprecateArraySince("8.3.5");
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      // when
      var item = itemFor("Массив");

      // then
      assertThat(isMarkedDeprecated(item))
        .as("устаревший для целевой версии класс помечается зачёркнутым")
        .isTrue();
      assertThat(item.getSortText())
        .as("флаг устаревания опускает пункт вниз своей корзины")
        .isEqualTo("31_Массив");
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void classIsMarkedWhenTargetEqualsDeprecationVersion() {
    // given — граница правила «target >= deprecatedSinceVersion»
    var configuration = deprecateArraySince("8.3.5");
    configuration.getV8PlatformOptions().setTargetVersion("8.3.5");
    try {
      // when
      var item = itemFor("Массив");

      // then — на самой версии устаревания класс уже помечается
      assertThat(isMarkedDeprecated(item)).isTrue();
      assertThat(item.getSortText()).isEqualTo("31_Массив");
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void classIsNotMarkedWhenTargetBelowDeprecationVersion() {
    // given — целевая версия ниже версии устаревания типа
    var configuration = deprecateArraySince("8.3.5");
    configuration.getV8PlatformOptions().setTargetVersion("8.3.4");
    try {
      // when
      var item = itemFor("Массив");

      // then
      assertThat(isMarkedDeprecated(item)).isFalse();
      assertThat(item.getSortText()).isEqualTo("30_Массив");
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  /**
   * Регистрирует синтетическое устаревание типа {@code Массив} и отдаёт
   * per-workspace конфигурацию для настройки целевой версии.
   */
  private LanguageServerConfiguration deprecateArraySince(String version) {
    initServerContext();
    var ref = typeRegistry.resolve("Массив").orElseThrow();
    typeRegistry.registerTypeMetadata(ref, new PlatformMetadata(
      "", version, List.of(), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()), FileType.BSL);
    return context.getLanguageServerConfiguration();
  }

  private CompletionItem itemFor(String label) {
    var documentContext = TestUtils.getDocumentContext(CONTENT);
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, CONTENT.length()));
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .filter(item -> label.equals(item.getLabel()))
      .findFirst()
      .orElseThrow(() -> new AssertionError(label + " должен попасть в completion после `Новый`"));
  }

  private static boolean isMarkedDeprecated(CompletionItem item) {
    return (item.getTags() != null && item.getTags().contains(CompletionItemTag.Deprecated))
      || Boolean.TRUE.equals(item.getDeprecated());
  }
}
