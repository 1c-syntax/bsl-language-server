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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы объектов XDTO-пакета конфигурации: ссылка {@code См. XDTOПакет.<Пакет>.<Тип>}
 * и свойства такого объекта.
 */
@CleanupContextBeforeClassAndAfterClass
class XdtoPackageTypesTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
  }

  @Test
  void seeRefToXdtoObjectTypeGivesItsType() {
    // given: параметр типизирован ссылкой на объектный тип пакета.
    var documentContext = documentWithReference();

    // when
    var types = at(documentContext, "ТипАдреса = Адрес", "ТипАдреса = ".length());

    // then
    assertThat(names(types)).containsExactly("XDTOПакет.ПакетТест.Адрес");
  }

  @Test
  void xdtoObjectPropertiesAreTypedBySchema() {
    // given / when: свойства объекта берут типы из схемы пакета.
    var documentContext = documentWithReference();

    // then
    assertThat(names(at(documentContext, "Страна = Адрес.Страна", "Страна = Адрес.".length())))
      .as("xs:string — строка")
      .containsExactly("Строка");
    assertThat(names(at(documentContext, "Актуальный = Адрес.Актуальный", "Актуальный = Адрес.".length())))
      .as("xs:boolean — булево")
      .containsExactly("Булево");
    assertThat(names(at(documentContext, "Индекс = Адрес.Индекс", "Индекс = Адрес.".length())))
      .as("простой тип пакета с базой xs:decimal — число")
      .containsExactly("Число");
  }

  @Test
  void propertyOfPackageTypeGivesThatType() {
    // given: свойство объявлено типом того же пакета.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Контакт - См. XDTOПакет.ПакетТест.КонтактнаяИнформация
      Процедура ОбработкаКонтакта(Контакт) Экспорт

      	АдресКонтакта = Контакт.Адрес;
      	СтранаКонтакта = АдресКонтакта.Страна;

      КонецПроцедуры
      """, context);

    // when
    var address = at(documentContext, "АдресКонтакта = Контакт.Адрес", "АдресКонтакта = Контакт.".length());
    var country = at(documentContext, "СтранаКонтакта = АдресКонтакта.Страна",
      "СтранаКонтакта = АдресКонтакта.".length());

    // then
    assertThat(names(address)).containsExactly("XDTOПакет.ПакетТест.Адрес");
    assertThat(names(country))
      .as("через свойство-объект доступны свойства его типа")
      .containsExactly("Строка");
  }

  @Test
  void factoryCreatesObjectOfTypeAddressedByNamespace() {
    // given: тип адресуется пространством имён и именем, как в коде без комментариев.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура СозданиеЧерезФабрику() Экспорт

      	ТипАдреса = ФабрикаXDTO.Тип("http://www.example.org/test-package", "Адрес");
      	Адрес = ФабрикаXDTO.Создать(ТипАдреса);
      	СтранаАдреса = Адрес.Страна;

      КонецПроцедуры
      """, context);

    // when
    var address = at(documentContext, "Адрес = ФабрикаXDTO.Создать(ТипАдреса)", "Адрес = ".length());
    var country = at(documentContext, "СтранаАдреса = Адрес.Страна", "СтранаАдреса = Адрес.".length());

    // then
    assertThat(names(address)).containsExactly("XDTOПакет.ПакетТест.Адрес");
    assertThat(names(country))
      .as("у созданного объекта доступны свойства его типа")
      .containsExactly("Строка");
  }

  @Test
  void inlineSeeRefTypesObjectWhenNamespaceIsComputed() {
    // given: пространство имён вычисляется, поэтому тип задан строчной ссылкой.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура СозданиеСВычисляемымПространством(ПространствоИмен) Экспорт

      	ТипАдреса = ФабрикаXDTO.Тип(ПространствоИмен, "Адрес");
      	Адрес = ФабрикаXDTO.Создать(ТипАдреса); // См. XDTOПакет.ПакетТест.Адрес
      	ТипПеременной = Адрес;
      	СтранаАдреса = Адрес.Страна;

      КонецПроцедуры
      """, context);

    // when
    var address = at(documentContext, "ТипПеременной = Адрес", "ТипПеременной = ".length());
    var country = at(documentContext, "СтранаАдреса = Адрес.Страна", "СтранаАдреса = Адрес.".length());

    // then: расчётный тип рекомендация не заменяет, а дополняет — поэтому проверяется вхождение.
    assertThat(names(address)).contains("XDTOПакет.ПакетТест.Адрес");
    assertThat(names(country))
      .as("строчная ссылка даёт тип, и через него доступны свойства")
      .containsExactly("Строка");
  }

  private DocumentContext documentWithReference() {
    return TestUtils.getDocumentContext("""
      // Параметры:
      //  Адрес - См. XDTOПакет.ПакетТест.Адрес
      Процедура ОбработкаАдреса(Адрес) Экспорт

      	ТипАдреса = Адрес;
      	Страна = Адрес.Страна;
      	Актуальный = Адрес.Актуальный;
      	Индекс = Адрес.Индекс;

      КонецПроцедуры
      """, context);
  }

  private TypeSet at(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static List<String> names(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
