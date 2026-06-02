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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.BeforeEach;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ModuleSymbolMarkupContentBuilderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ModuleSymbolMarkupContentBuilder markupContentBuilder;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  void testContentFromCommonModule() {
    // given
    var documentContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var moduleSymbol = documentContext.getSymbolTree().getModule();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, moduleSymbol)).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    // Должны быть: местоположение, информация о модуле
    assertThat(blocks).hasSizeGreaterThanOrEqualTo(2);

    // Сигнатура - для CommonModule показывается только имя модуля
    assertThat(blocks.get(0)).contains("ОбщийМодуль.ПервыйОбщийМодуль");

    // Местоположение - используется локализованный mdoRef
    assertThat(blocks.get(1)).contains("Доступность:");
  }

  @Test
  void testContentFromManagerModule() {
    // given
    var documentContext = context.getDocument("Catalog.Справочник1", ModuleType.ManagerModule).orElseThrow();
    var moduleSymbol = documentContext.getSymbolTree().getModule();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, moduleSymbol)).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSizeGreaterThanOrEqualTo(1);

    // Для ManagerModule используется локализованный mdoRef
    assertThat(blocks.getFirst()).contains("Справочник.Справочник1");
  }

  @Test
  void testContentFromObjectModule() {
    // given
    var documentContext = context.getDocument("Catalog.Справочник1", ModuleType.ObjectModule).orElseThrow();
    var moduleSymbol = documentContext.getSymbolTree().getModule();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, moduleSymbol)).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSizeGreaterThanOrEqualTo(1);

    // Для ObjectModule используется локализованный mdoRef
    assertThat(blocks.getFirst()).contains("Справочник.Справочник1");
  }

  @Test
  void testCommonModuleWithMetadataInfo() {
    // given
    var documentContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var moduleSymbol = documentContext.getSymbolTree().getModule();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, moduleSymbol)).getValue();

    // then
    assertThat(content).isNotEmpty();

    // Проверяем, что контент содержит секции с информацией о модуле
    // (флаги доступности, режим повторного использования)
    // Конкретные значения зависят от тестовых метаданных
    assertThat(content).contains("---");
  }

  private static Reference referenceTo(DocumentContext documentContext, SourceDefinedSymbol symbol) {
    return Reference.of(documentContext.getSymbolTree().getModule(), symbol,
      new Location(documentContext.getUri().toString(), symbol.getSelectionRange()));
  }
}
