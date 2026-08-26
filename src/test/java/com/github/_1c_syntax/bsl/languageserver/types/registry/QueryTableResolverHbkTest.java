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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поля таблиц языка запросов складываются из двух половин: платформенную
 * ({@code Представление}, {@code МоментВремени}, поля виртуальных таблиц) знает
 * только синтакс-помощник, поэтому тест требует установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (платформенные поля таблиц берутся из bsl-context)")
class QueryTableResolverHbkTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private QueryTableResolver resolver;

  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void catalogTableHasPlatformFieldsAndOwnAttributes() {
    // when
    var fields = resolver.fields("Catalog.Справочник1");

    // then
    assertThat(names(fields))
      .as("стандартные реквизиты, псевдополя таблицы и собственные реквизиты справочника")
      .contains("Ссылка", "Код", "Наименование", "ПометкаУдаления", "Предопределенный",
        "ИмяПредопределенныхДанных", "ВерсияДанных", "Представление", "Реквизит1")
      .as("общий реквизит, у которого этот справочник исключён из состава, полем не стал")
      .doesNotContain("ОбщийРеквизит1")
      .as("методов и событий у таблицы нет — только поля")
      .doesNotContain("Метаданные", "ПолучитьОбъект");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .as("шаблонный тип поля материализуется именем объекта из имени таблицы")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void documentTableHasPointInTimeAndACommonAttribute() {
    // when
    var fields = resolver.fields("Document.Документ1");

    // then
    assertThat(names(fields))
      .as("МоментВремени объявляет только платформа: в метаданных документа его нет")
      .contains("МоментВремени", "Дата", "Номер", "Проведен", "Ссылка", "Представление")
      .as("общий реквизит, в состав которого документ включён, — тоже поле таблицы")
      .contains("ОбщийРеквизит1");
  }

  @Test
  void informationRegisterTableHasDimensionsAndPlatformFields() {
    // when
    var fields = resolver.fields("InformationRegister.РегистрСведений1");

    // then
    assertThat(names(fields))
      .as("измерения — из метаданных, Активность и МоментВремени — от платформы")
      .contains("Справочник1", "Активность", "МоментВремени", "Период", "Регистратор", "НомерСтроки");
  }

  @Test
  void sliceTableDropsFieldsThatTheSliceHasNot() {
    // when
    var fields = resolver.fields("InformationRegister.РегистрСведений1.SliceLast");

    // then
    assertThat(names(fields))
      .as("измерения у среза есть")
      .contains("Справочник1", "Период")
      .as("а момента времени у среза нет — в отличие от самого регистра")
      .doesNotContain("МоментВремени");
  }

  @Test
  void virtualTableIsFoundByTheNameTheConfiguratorWrites() {
    // when
    // Задачи по исполнителю платформа переименовала в TasksByPerformer, а
    // конфигуратор пишет прежнее имя.
    var fields = resolver.fields("Task.Задача1.TasksByExecutive");

    // then
    assertThat(names(fields))
      .as("таблица нашлась, и её платформенные поля на месте")
      .contains("Ссылка", "Выполнена", "Наименование", "Представление");
  }

  @Test
  void unknownTableHasNoFields() {
    assertThat(resolver.fields("1:0d7c2c47-4b5e-4b0e-8d1a-000000000000")).isEmpty();
  }

  @Test
  void dynamicListRowGetsFieldsOfItsQueryTable() {
    // given
    // Колонки списка в форме не объявлены: за ним стоит основная таблица, и
    // колонками строки служат её поля — включая те, которых у ссылочного типа
    // объекта нет вовсе.
    var itemsType = typeRegistry.resolve("ВсеЭлементыФормы.Справочник.Справочник1.Форма.ФормаСписка")
      .orElseThrow();
    var tableRef = typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(member -> member.matches("Список"))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();
    var currentData = typeRegistry.getMembers(tableRef, FileType.BSL).stream()
      .filter(member -> member.matches("ТекущиеДанные"))
      .findFirst()
      .orElseThrow();

    // when
    var rowRef = currentData.returnTypes().refs().iterator().next();

    // then
    assertThat(names(typeRegistry.getMembers(rowRef, FileType.BSL)))
      .contains("Ссылка", "Код", "Наименование", "Реквизит1")
      .as("псевдополя таблицы — то, чего у ссылочного типа справочника нет")
      .contains("Представление", "ВерсияДанных");
  }

  private static List<String> names(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }

  private static MemberDescriptor field(List<MemberDescriptor> members, String name) {
    return members.stream()
      .filter(member -> member.name().equals(name))
      .findFirst()
      .orElseThrow(() -> new AssertionError("поле не найдено: " + name));
  }

  private static List<String> qualifiedNames(MemberDescriptor member) {
    return member.returnTypes().refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
