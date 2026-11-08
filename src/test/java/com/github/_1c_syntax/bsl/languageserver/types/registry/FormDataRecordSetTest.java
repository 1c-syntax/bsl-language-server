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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.types.ValueTypeDescription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Набор записей на управляемой форме — {@code ДанныеФормыСтруктураСКоллекцией}: коллекция
 * строк {@code ДанныеФормыЭлементКоллекции} с полями записи регистра.
 * <p>
 * Типы набора и записи заведены в тесте вручную: так видна сама регистрация данных формы,
 * без зависимости от того, какие семейства регистров знает синтакс-помощник.
 */
@CleanupContextBeforeClassAndAfterClass
class FormDataRecordSetTest extends AbstractServerContextAwareTest {

  private static final String RECORD_SET = "РегистрСведенийНаборЗаписей.Тест";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private FormDataTypesRegistrar formDataTypes;

  @Test
  void recordSetOnTheFormIsACollectionOfRowsWithRecordColumns() {
    // given: запись регистра с полем и набор, элементы которого — эти записи.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();
    var recordRef = typeRegistry.registerConfigurationType("РегистрСведенийЗапись.Тест");
    typeRegistry.registerMemberSource(recordRef, () -> List.of(FormPlatformTypes.platformProperty(
      BilingualString.of("Поле1", "Field1"), typeRegistry.resolve("Строка").orElseThrow(),
      BilingualString.EMPTY)), FileType.BSL);
    var setRef = typeRegistry.registerConfigurationType(RECORD_SET);
    typeRegistry.registerDefaultElementTypes(setRef, List.of(recordRef));

    // when
    var dataRef = formDataTypes.prepareAttributeTypes(List.of(recordSetAttribute()), FormKind.MANAGED, "Форма")
      .get("наборзаписей").refs().iterator().next();

    // then: обход набора даёт строку с полями записи.
    assertThat(typeRegistry.displayName(dataRef, Language.RU)).isEqualTo("ДанныеФормыСтруктураСКоллекцией");
    var rowRef = typeRegistry.getDefaultElementTypes(dataRef).refs().iterator().next();
    assertThat(typeRegistry.displayName(rowRef, Language.RU)).isEqualTo("ДанныеФормыЭлементКоллекции");
    assertThat(names(typeRegistry.getMembers(rowRef, FileType.BSL)))
      .as("поля записи и методы строки коллекции данных формы")
      .contains("Поле1", "ПолучитьИдентификатор");
    assertThat(formDataTypes.rowOfCollection(dataRef))
      .as("её же отдают ТекущиеДанные таблицы над набором")
      .isEqualTo(rowRef);

    // and: методы набора, отдающие строку, отдают её же, а не обобщённую.
    var added = typeRegistry.getMembers(dataRef, FileType.BSL).stream()
      .filter(member -> member.matches("Добавить"))
      .findFirst()
      .orElseThrow();
    assertThat(added.returnTypes().refs()).extracting(TypeRef::qualifiedName)
      .containsExactly(rowRef.qualifiedName());
  }

  @Test
  void objectFormDataUsesItsOwnAvailabilityAndPreservesPropertyDocumentation() {
    initServerContext(PATH_TO_METADATA);
    var object = typeRegistry.registerConfigurationType("ДокументОбъект.ТестДоступности");
    var reference = typeRegistry.registerConfigurationType("ДокументСсылка.ТестДоступности");
    var server = new PlatformMetadata("8.2", "8.3.25", List.of("Replacement"),
      Set.of(Availability.SERVER), null, BilingualString.EMPTY,
      BilingualString.of("Примечание", "Note"), List.of(), List.of());
    var base = typeRegistry.resolve("ДанныеФормыСтруктура").orElseThrow();
    var available = new PlatformMetadata("8.2", "", List.of(),
      Set.of(Availability.SERVER, Availability.THIN_CLIENT, Availability.WEB_CLIENT), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    typeRegistry.registerTypeMetadata(base, available, FileType.BSL);
    typeRegistry.registerTypeMetadata(object, server, FileType.BSL);
    typeRegistry.registerMemberSource(object, () -> List.of(
      MemberDescriptor.property("Ссылка", reference).withMetadata(server),
      MemberDescriptor.method("Записать").withMetadata(server)), FileType.BSL);
    var attribute = mock(FormAttribute.class);
    when(attribute.getName()).thenReturn("Объект");
    when(attribute.getValueType()).thenReturn(ValueTypeDescription.create(
      com.github._1c_syntax.bsl.types.ValueTypes.getOrCompute("DocumentObject.ТестДоступности")));
    when(attribute.getColumns()).thenReturn(List.of());

    var data = formDataTypes.prepareAttributeTypes(List.of(attribute), FormKind.MANAGED, "Форма")
      .get("объект").refs().iterator().next();
    var members = typeRegistry.getMembers(data, FileType.BSL);
    var link = members.stream().filter(member -> member.matches("Ссылка")).findFirst().orElseThrow();
    var contexts = typeRegistry.getTypeMetadata(base, FileType.BSL).availabilities();
    assertThat(contexts).contains(Availability.THIN_CLIENT);
    assertThat(link.metadata().availabilities()).isEqualTo(contexts);
    assertThat(typeRegistry.getTypeMetadata(data, FileType.BSL).availabilities()).isEqualTo(contexts);
    assertThat(link.metadata().sinceVersion()).isEqualTo(server.sinceVersion());
    assertThat(link.metadata().deprecatedSinceVersion()).isEqualTo(server.deprecatedSinceVersion());
    assertThat(link.metadata().notes()).isEqualTo(server.notes());
    assertThat(members).noneMatch(member -> member.matches("Записать"));
    assertThat(typeRegistry.getMembers(object, FileType.BSL).stream()
      .filter(member -> member.matches("Ссылка")).findFirst().orElseThrow().metadata()).isEqualTo(server);
  }

  private static FormAttribute recordSetAttribute() {
    // Тип значения — тот, что mdclasses собирает из `cfg:InformationRegisterRecordSet.Тест`.
    var valueType = com.github._1c_syntax.bsl.types.ValueTypes.getOrCompute("InformationRegisterRecordSet.Тест");
    var attribute = mock(FormAttribute.class);
    when(attribute.getName()).thenReturn("НаборЗаписей");
    when(attribute.getValueType()).thenReturn(ValueTypeDescription.create(valueType));
    when(attribute.getColumns()).thenReturn(List.of());
    return attribute;
  }

  private static List<String> names(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }
}
