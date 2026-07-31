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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы регистров, которые специализация по имени регистра не покрывает: они объявлены
 * платформой через плейсхолдер, который к имени регистра отношения не имеет.
 * <p>
 * Конфигурация-фикстура своя: в общей есть только независимый регистр сведений, а нужен
 * регистр, подчинённый регистратору, и документ, пишущий в него движения.
 */
@CleanupContextBeforeClassAndAfterClass
class RegisterRecorderTypesTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_REGISTERS = "src/test/resources/metadata/registerRecorders";

  private static final String RECORDER = "Регистратор";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private RecorderIndex recorderIndex;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_REGISTERS));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void recorderOfRegisterRecordIsTypedByDocumentsWritingToIt() {
    assertThat(memberTypes("РегистрБухгалтерииЗапись.РегистрБухгалтерии1", RECORDER))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void recorderOfRecordKeyIsTypedTheSameWay() {
    assertThat(memberTypes("РегистрБухгалтерииКлючЗаписи.РегистрБухгалтерии1", RECORDER))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void recorderIndexIsBuiltFromDocumentsBecauseRegistersDoNotKnowTheirRecorders() {
    assertThat(recorderIndex.recordersOf("РегистрБухгалтерии.РегистрБухгалтерии1"))
      .containsExactly("Документ1");
    assertThat(recorderIndex.recordersOf("РегистрСведений.РегистрСведений2"))
      .as("регистра, в который никто не пишет, в индексе нет вовсе")
      .isEmpty();
  }

  @Test
  void rebuildingTheIndexReplacesItsContentInsteadOfAppending() {
    // Индекс публикуется целиком, поэтому повторная сборка по другому набору объектов
    // не оставляет от прежней ничего — ни лишних регистров, ни лишних документов.
    recorderIndex.index(List.of());
    assertThat(recorderIndex.recordersOf("РегистрБухгалтерии.РегистрБухгалтерии1")).isEmpty();

    recorderIndex.index(context.getConfiguration().getChildrenByMdoRef().values());
    assertThat(recorderIndex.recordersOf("РегистрБухгалтерии.РегистрБухгалтерии1"))
      .containsExactly("Документ1");
  }

  @Test
  void memberDeclaredWithForeignRegisterFamilyIsFixedUp() {
    // Ошибка синтакс-помощника: у РегистрБухгалтерииНаборЗаписей метод Вставить
    // объявлен возвращающим РегистрНакопленияЗапись.<Имя регистра накопления>,
    // хотя соседний Добавить — РегистрБухгалтерииЗапись. Плейсхолдер чужого
    // семейства подстановкой имени регистра не покрывается и доезжал до пользователя.
    var setType = "РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1";

    assertThat(memberTypes(setType, "Добавить"))
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
    assertThat(memberTypes(setType, "Вставить"))
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
  }

  @Test
  void fixedUpMemberCarriesTheTypeInItsSignatureToo() {
    // Тип возврата читают и по члену, и по сигнатуре — если поправить только член,
    // подсказка сигнатуры продолжит показывать чужое семейство.
    var member = member("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1", "Вставить");

    assertThat(member.signatures()).hasSize(1);
    assertThat(member.signatures().get(0).returnTypes().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
  }

  @Test
  void recordSetIsACollectionOfItsOwnRecords() {
    var setRef = typeRegistry.resolve("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1").orElseThrow();

    assertThat(typeRegistry.getDefaultElementTypes(setRef).refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
  }

  @Test
  void unloadedRecordSetCarriesTheColumnsOfItsRecord() {
    // Платформа объявляет Выгрузить() возвращающим просто ТаблицаЗначений, хотя колонки
    // у выгруженной таблицы те же, что у записи регистра.
    var table = member("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1", "Выгрузить").returnTypes();
    var tableRef = table.refs().iterator().next();
    assertThat(tableRef.qualifiedName()).isEqualTo("ТаблицаЗначений");

    var row = table.getElementTypes(tableRef);
    assertThat(row.refs()).extracting(TypeRef::qualifiedName).containsExactly("СтрокаТаблицыЗначений");
    assertThat(row.getFieldTypes(RECORDER).refs())
      .as("колонка выгруженной таблицы несёт тип, достроенный по регистраторам")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void recorderParameterOfSelectByRecorderIsTypedToo() {
    // Плейсхолдер стоит не в типе возврата, а в параметре: у ВыбратьПоРегистратору
    // первый параметр объявлен как ДокументСсылка.<Имя документа>.
    var member = member("РегистрБухгалтерииМенеджер.РегистрБухгалтерии1", "ВыбратьПоРегистратору");

    assertThat(member.signatures()).hasSize(1);
    assertThat(member.signatures().get(0).parameters().get(0).types().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void createRecordSetChainStaysSpecialized() {
    // Репорт: в дот-комплишене на РегистрыБухгалтерии.X.СоздатьНаборЗаписей() метод
    // Добавить показывает <Имя регистра бухгалтерии>. Проверяем звено за звеном.
    assertThat(memberTypes("РегистрБухгалтерииМенеджер.РегистрБухгалтерии1", "СоздатьНаборЗаписей"))
      .containsExactly("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1");
    assertThat(memberTypes("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1", "Добавить"))
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
  }

  @Test
  void addMethodIsSpecializedInItsSignatureToo() {
    // Репорт: в подсказке автодополнения у Добавить() стоял <Имя регистра бухгалтерии>.
    // Подсказка берёт тип из сигнатуры (CompletionProvider.applyMethodDetail), а
    // уточнялся только тип на уровне члена.
    var member = member("РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1", "Добавить");

    assertThat(member.signatures()).hasSize(1);
    assertThat(member.signatures().get(0).returnTypes().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("РегистрБухгалтерииЗапись.РегистрБухгалтерии1");
  }

  private List<String> memberTypes(String typeName, String memberName) {
    return member(typeName, memberName).returnTypes().refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }

  private MemberDescriptor member(String typeName, String memberName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    var found = typeRegistry.getMembers(ref, FileType.BSL).stream()
      .filter(m -> m.matches(memberName))
      .findFirst();
    assertThat(found).as("член %s у %s", memberName, typeName).isPresent();
    return found.orElseThrow();
  }
}
