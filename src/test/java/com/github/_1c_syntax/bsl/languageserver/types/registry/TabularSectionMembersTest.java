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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Табличная часть объекта — это платформенный тип {@code Табличная часть} со своими
 * методами и своей строкой; конфигурация добавляет к строке лишь колонки.
 */
@CleanupContextBeforeClassAndAfterClass
class TabularSectionMembersTest extends AbstractServerContextAwareTest {

  private static final String SECTION = "ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1";
  private static final String ROW = "ДокументТабличнаяЧастьСтрока.Документ1.ТабличнаяЧасть1";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void tabularSectionCarriesPlatformMethods() {
    assertThat(names(membersOf(SECTION)))
      .as("методы платформенного типа «Табличная часть»")
      .contains("Добавить", "Вставить", "Получить", "Количество", "Очистить",
        "НайтиСтроки", "Выгрузить", "Загрузить", "Сортировать");
  }

  @Test
  void columnsBelongToTheRowAndNotToTheSection() {
    assertThat(names(membersOf(ROW)))
      .as("колонки и номер строки — у строки")
      .contains("Реквизит1", "НомерСтроки");
    assertThat(names(membersOf(SECTION)))
      .as("у самой табличной части колонок нет: `ТЧ.Цена` в 1С не работает")
      .doesNotContain("Реквизит1");
  }

  @Test
  void sectionIsIteratedOverItsOwnRow() {
    var sectionRef = resolve(SECTION);

    assertThat(typeRegistry.supportsForEach(sectionRef, FileType.BSL)).isTrue();
    assertThat(typeRegistry.supportsIndexAccess(sectionRef, FileType.BSL)).isTrue();
    assertThat(typeRegistry.getDefaultElementTypes(sectionRef).refs())
      .extracting(TypeRef::qualifiedName)
      .as("элемент — строка этой табличной части, а не обобщённая")
      .containsExactly(ROW);
  }

  @Test
  void rowReturningMethodsAreSpecialized() {
    for (var methodName : List.of("Добавить", "Вставить", "Получить", "Найти")) {
      var method = member(SECTION, methodName);
      assertThat(method.returnTypes().refs()).extracting(TypeRef::qualifiedName)
        .as("%s возвращает строку этой табличной части", methodName)
        .contains(ROW);
      assertThat(method.signatures())
        .as("%s: тип уточнён и в сигнатуре — её показывает подсказка автодополнения", methodName)
        .allSatisfy(signature -> assertThat(signature.returnTypes().refs())
          .extracting(TypeRef::qualifiedName)
          .contains(ROW));
    }
  }

  @Test
  void findRowsReturnsArrayOfOwnRows() {
    var findRows = member(SECTION, "НайтиСтроки");

    assertThat(findRows.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");
    assertThat(findRows.returnTypes().getElementTypes().refs()).extracting(TypeRef::qualifiedName)
      .as("массив строк этой табличной части, а не нетипизированный")
      .containsExactly(ROW);
  }

  @Test
  void unloadReturnsValueTableWithSameColumns() {
    var unload = member(SECTION, "Выгрузить");

    assertThat(unload.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("ТаблицаЗначений");
    var row = unload.returnTypes().getElementTypes();
    assertThat(row.refs()).extracting(TypeRef::qualifiedName).containsExactly("СтрокаТаблицыЗначений");
    assertThat(row.getLocalFields(row.refs().iterator().next()))
      .as("колонки выгруженной таблицы — колонки табличной части")
      .containsKey("Реквизит1");
  }

  private TypeRef resolve(String qualifiedName) {
    return typeRegistry.resolve(qualifiedName).orElseThrow();
  }

  private Collection<MemberDescriptor> membersOf(String qualifiedName) {
    return typeRegistry.getMembers(resolve(qualifiedName), FileType.BSL);
  }

  private MemberDescriptor member(String qualifiedName, String memberName) {
    return membersOf(qualifiedName).stream()
      .filter(member -> member.matches(memberName))
      .findFirst()
      .orElseThrow(() -> new AssertionError("нет члена " + memberName + " у " + qualifiedName));
  }

  private static List<String> names(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }
}
