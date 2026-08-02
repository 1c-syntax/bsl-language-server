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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Автодополнение после точки у объекта XDTO-пакета: свойства берутся из схемы пакета.
 */
@CleanupContextBeforeClassAndAfterClass
class XdtoPackageCompletionTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
  }

  @Test
  void completionAfterDotOnReferencedObjectListsSchemaProperties() {
    // given: параметр типизирован ссылкой на объект пакета.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Адрес - См. XDTOПакет.ПакетТест.Адрес
      Процедура ОбработкаАдреса(Адрес) Экспорт
      	Адрес.
      КонецПроцедуры
      """, context);

    // when
    var labels = labels(documentContext, 3, 7);

    // then: видны свойства схемы и платформенная часть объекта XDTO.
    assertThat(labels).contains("Страна", "Индекс", "Актуальный", "Состав");
    assertThat(labels)
      .as("объект XDTO приносит свои методы")
      .contains("Установить", "Получить", "Проверить");
  }

  @Test
  void completionAfterDotOnFactoryCreatedObjectListsSchemaProperties() {
    // given: объект создан фабрикой по пространству имён и имени типа.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура СозданиеЧерезФабрику() Экспорт
      	ТипАдреса = ФабрикаXDTO.Тип("http://www.example.org/test-package", "Адрес");
      	Адрес = ФабрикаXDTO.Создать(ТипАдреса);
      	Адрес.
      КонецПроцедуры
      """, context);

    // when
    var labels = labels(documentContext, 3, 7);

    // then
    assertThat(labels).contains("Страна", "Индекс", "Актуальный");
  }

  @Test
  void completionOnNestedPackageTypeListsItsProperties() {
    // given: свойство объявлено типом того же пакета.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Контакт - См. XDTOПакет.ПакетТест.КонтактнаяИнформация
      Процедура ОбработкаКонтакта(Контакт) Экспорт
      	АдресКонтакта = Контакт.Адрес;
      	АдресКонтакта.
      КонецПроцедуры
      """, context);

    // when
    var labels = labels(documentContext, 4, 15);

    // then
    assertThat(labels).contains("Страна", "Индекс");
  }

  private List<String> labels(DocumentContext documentContext, int line, int character) {
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(line, character));
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .map(CompletionItem::getLabel)
      .toList();
  }
}
