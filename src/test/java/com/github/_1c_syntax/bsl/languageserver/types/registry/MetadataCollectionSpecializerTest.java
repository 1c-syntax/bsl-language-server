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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Collection;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрытие {@link MetadataCollectionSpecializer}: цепочки
 * {@code Метаданные.<Группа>.<имя>.<коллекция>.<имя ребёнка>} должны давать
 * конкретный {@code ОбъектМетаданных: <тип>}, а не общий union.
 * <p>
 * Тестовая конфигурация — {@code src/test/resources/metadata/designer}: содержит
 * Документ1 с реквизитами и табличной частью, Справочник1, РегистрСведений1
 * и т.п. (см. соответствующие XML).
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Тест требует реальный HBK 1С (выключён по умолчанию). "
    + "Локально: BSL_LANGUAGE_SERVER_RUN_HBK_TESTS=true ./gradlew test --tests '*MetadataCollectionSpecializerTest*'")
class MetadataCollectionSpecializerTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void topLevelGroupCollectionsAreSpecialized() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метаданные.Документы → property Документы у ОбъектМетаданныхКонфигурация
    // должно указывать на synthetic КоллекцияОбъектовМетаданных.Документы.
    var rootRef = typeRegistry.resolve("ОбъектМетаданныхКонфигурация").orElse(null);
    assertThat(rootRef).as("ОбъектМетаданныхКонфигурация должен быть зарегистрирован").isNotNull();

    var documentsMember = findMember(typeRegistry.getMembers(rootRef, FileType.BSL), "Документы");
    assertThat(documentsMember)
      .as("Свойство Документы должно присутствовать на ОбъектМетаданныхКонфигурация")
      .isNotNull();
    assertThat(documentsMember.returnType().qualifiedName())
      .as("returnType Метаданные.Документы должен быть synthetic specialized")
      .isEqualTo("КоллекцияОбъектовМетаданных.Документы");
  }

  @Test
  void baseCollectionExposesGenericMemberAndMethods() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // КоллекцияОбъектовМетаданных приходит из bsl-context: проверяем что у
    // base-типа в реестре есть generic-property и методы Получить/Найти/Количество.
    var baseRef = typeRegistry.resolve("КоллекцияОбъектовМетаданных").orElse(null);
    assertThat(baseRef).as("КоллекцияОбъектовМетаданных должен быть в реестре").isNotNull();

    var members = typeRegistry.getMembers(baseRef, FileType.BSL);
    var hasGeneric = members.stream().anyMatch(MemberDescriptor::generic);
    var names = memberNames(members);
    assertThat(hasGeneric)
      .as("В members базовой коллекции должен быть generic-member. Реальный список: %s", names)
      .isTrue();
    assertThat(names)
      .as("Базовая коллекция содержит методы Получить/Найти/Количество")
      .anySatisfy(n -> assertThat(n.toLowerCase()).matches("получить|get|найти|find|количество|count|contains|содержит"));
  }

  @Test
  void perMdoCollectionMemberPointsToPerMdoTypeRef() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метаданные.Документы.Документ1 → ОбъектМетаданных: Документ.Документ1.
    var groupRef = typeRegistry.intern(TypeKind.PLATFORM, "КоллекцияОбъектовМетаданных.Документы");
    var members = typeRegistry.getMembers(groupRef, FileType.BSL);
    var allNames = memberNames(members);
    var doc1Member = findMember(members, "Документ1");
    assertThat(doc1Member)
      .as("Member Документ1 должен быть материализован в КоллекцияОбъектовМетаданных.Документы. "
        + "Реальные members: %s", allNames)
      .isNotNull();
    assertThat(doc1Member.returnType().qualifiedName())
      .as("Документ1 → ОбъектМетаданных: Документ.Документ1 (per-MDO)")
      .isEqualTo("ОбъектМетаданных: Документ.Документ1");
  }

  @Test
  void perMdoTypeRefHasOverridesForKnownCollections() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Триггерим материализацию через group collection — это активирует
    // регистрацию per-MDO source'ов внутри лямбды.
    var groupRef = typeRegistry.intern(TypeKind.PLATFORM, "КоллекцияОбъектовМетаданных.Документы");
    typeRegistry.getMembers(groupRef, FileType.BSL);  // force lazy

    var perMdoRef = typeRegistry.intern(TypeKind.PLATFORM, "ОбъектМетаданных: Документ.Документ1");
    var names = memberNames(typeRegistry.getMembers(perMdoRef, FileType.BSL));
    assertThat(names)
      .as("На per-MDO Документ1 должны быть property для всех известных коллекций. Получено: %s", names)
      .contains("Реквизиты", "СтандартныеРеквизиты", "ТабличныеЧасти", "Формы", "Макеты", "Команды");
  }

  @Test
  void perMdoAttributesExpandedFromMdclasses() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метаданные.Документы.Документ1.Реквизиты → членов Реквизит1/2/3
    // (пользовательские реквизиты Документа1 из mdclasses).
    var perMdoTypeRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "ОбъектМетаданных: Документ.Документ1");
    var attrsMember = findMember(typeRegistry.getMembers(perMdoTypeRef, FileType.BSL), "Реквизиты");
    assertThat(attrsMember)
      .as("На per-MDO типе должно быть property Реквизиты")
      .isNotNull();
    assertThat(attrsMember.returnType().qualifiedName())
      .as("Реквизиты у Документ1 — per-MDO/per-collection synthetic")
      .isEqualTo("КоллекцияОбъектовМетаданных.Реквизиты.Документ1");

    var attrsCollRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.Реквизиты.Документ1");
    var names = memberNames(typeRegistry.getMembers(attrsCollRef, FileType.BSL));
    assertThat(names)
      .as("Реквизиты Документ1: имена реквизитов из mdclasses")
      .contains("Реквизит1", "Реквизит2", "Реквизит3");
  }

  @Test
  void perMdoStandardAttributesAreHardcoded() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Стандартные реквизиты Документа1: Ссылка, Дата, Номер, Проведен,
    // ПометкаУдаления, МоментВремени. У каждого returnType — ОписаниеСтандартногоРеквизита.
    var perMdoTypeRef = typeRegistry.intern(TypeKind.PLATFORM, "ОбъектМетаданных: Документ.Документ1");
    var stdAttrsMember = findMember(typeRegistry.getMembers(perMdoTypeRef, FileType.BSL), "СтандартныеРеквизиты");
    assertThat(stdAttrsMember)
      .as("На per-MDO типе должно быть property СтандартныеРеквизиты")
      .isNotNull();
    assertThat(stdAttrsMember.returnType().qualifiedName())
      .isEqualTo("ОписанияСтандартныхРеквизитов.СтандартныеРеквизиты.Документ1");

    var stdAttrsCollRef = typeRegistry.intern(TypeKind.PLATFORM,
      "ОписанияСтандартныхРеквизитов.СтандартныеРеквизиты.Документ1");
    var members = typeRegistry.getMembers(stdAttrsCollRef, FileType.BSL);
    var names = memberNames(members);
    assertThat(names)
      .as("Стандартные реквизиты Документ1: hardcoded набор")
      .contains("Ссылка", "ПометкаУдаления", "Номер", "Дата", "Проведен", "МоментВремени");

    // ВАЖНО: returnType каждого стандартного реквизита — ОписаниеСтандартногоРеквизита
    // (не ОбъектМетаданных: ...), иначе дальнейшая цепочка `.Ссылка.<член>` ломается.
    var ssylka = findMember(members, "Ссылка");
    assertThat(ssylka).isNotNull();
    assertThat(ssylka.returnType().qualifiedName())
      .as("returnType стандартного реквизита — ОписаниеСтандартногоРеквизита")
      .isEqualTo("ОписаниеСтандартногоРеквизита");
  }

  @Test
  void tabularSectionExposesStandardAttributes() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метаданные.Документы.Документ1.ТабличныеЧасти.ТабличнаяЧасть1.СтандартныеРеквизиты
    // должен дать НомерСтроки и Ссылка (стандартные реквизиты ТЧ).
    var perTsRef = typeRegistry.intern(TypeKind.PLATFORM,
      "ОбъектМетаданных: ТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
    // Триггерим material через top-level (lazy внутри source-лямбд иначе не сработает).
    typeRegistry.getMembers(typeRegistry.intern(TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.ТабличныеЧасти.Документ1"), FileType.BSL);
    var perTsMembers = typeRegistry.getMembers(perTsRef, FileType.BSL);
    var stdAttrsMember = findMember(perTsMembers, "СтандартныеРеквизиты");
    assertThat(stdAttrsMember)
      .as("На per-TS типе должно быть property СтандартныеРеквизиты. Members: %s",
        memberNames(perTsMembers))
      .isNotNull();
    assertThat(stdAttrsMember.returnType().qualifiedName())
      .isEqualTo("ОписанияСтандартныхРеквизитов.СтандартныеРеквизиты.Документ1.ТабличнаяЧасть1");

    var stdAttrsCollRef = typeRegistry.intern(TypeKind.PLATFORM,
      "ОписанияСтандартныхРеквизитов.СтандартныеРеквизиты.Документ1.ТабличнаяЧасть1");
    var names = memberNames(typeRegistry.getMembers(stdAttrsCollRef, FileType.BSL));
    assertThat(names)
      .as("Стандартные реквизиты ТЧ: НомерСтроки и Ссылка")
      .contains("НомерСтроки", "Ссылка");
  }

  @Test
  void tabularSectionGetsRecursivePerSectionType() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метаданные.Документы.Документ1.ТабличныеЧасти.ТабличнаяЧасть1 →
    // ОбъектМетаданных: ТабличнаяЧасть.Документ1.ТабличнаяЧасть1 (per-TS).
    var tsCollRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.ТабличныеЧасти.Документ1");
    var tsMember = findMember(typeRegistry.getMembers(tsCollRef, FileType.BSL), "ТабличнаяЧасть1");
    assertThat(tsMember).as("ТабличнаяЧасть1 материализована").isNotNull();
    assertThat(tsMember.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: ТабличнаяЧасть.Документ1.ТабличнаяЧасть1");

    // На per-TS типе property Реквизиты → имена колонок ТЧ из mdclasses.
    var perTsRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "ОбъектМетаданных: ТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
    var tsAttrsMember = findMember(typeRegistry.getMembers(perTsRef, FileType.BSL), "Реквизиты");
    assertThat(tsAttrsMember).isNotNull();
    assertThat(tsAttrsMember.returnType().qualifiedName())
      .isEqualTo("КоллекцияОбъектовМетаданных.Реквизиты.Документ1.ТабличнаяЧасть1");

    var tsAttrsCollRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.Реквизиты.Документ1.ТабличнаяЧасть1");
    var tsAttrsNames = memberNames(typeRegistry.getMembers(tsAttrsCollRef, FileType.BSL));
    assertThat(tsAttrsNames)
      .as("Реквизиты ТабличнаяЧасть1 — колонки ТЧ из mdclasses")
      .contains("Реквизит1", "Реквизит2");
  }

  @Test
  void compositeTypePropertyKeepsAllTypes() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // ПериодичностьНомера у ОбъектМетаданных: Документ имеет composite-тип
    // в HBK: ПериодичностьНомераБизнесПроцесса, ПериодичностьНомераДокумента.
    // Оба должны попасть в returnTypes — иначе hover показывает только первый.
    var docTypeRef = typeRegistry.resolve("ОбъектМетаданных: Документ").orElse(null);
    assertThat(docTypeRef).isNotNull();
    var periodicity = findMember(typeRegistry.getMembers(docTypeRef, FileType.BSL), "ПериодичностьНомера");
    assertThat(periodicity).as("Свойство ПериодичностьНомера должно быть").isNotNull();
    var typeNames = periodicity.returnTypes().refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
    assertThat(typeNames)
      .as("returnTypes ПериодичностьНомера: composite из двух типов")
      .containsExactlyInAnyOrder("ПериодичностьНомераБизнесПроцесса", "ПериодичностьНомераДокумента");
  }

  @Test
  void otherBaseCollectionsAreSpecialized() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Свойства на других базовых типах (НЕ КоллекцияОбъектовМетаданных):
    // returnType должен быть synthetic specialized с правильным base'ом.
    var perMdoTypeRef = typeRegistry.intern(TypeKind.PLATFORM, "ОбъектМетаданных: Документ.Документ1");
    var members = typeRegistry.getMembers(perMdoTypeRef, FileType.BSL);

    var dvizheniya = findMember(members, "Движения");
    assertThat(dvizheniya).as("Свойство Движения должно быть на Документ1").isNotNull();
    assertThat(dvizheniya.returnType().qualifiedName())
      .isEqualTo("КоллекцияЗначенийСвойстваОбъектаМетаданных.Движения.Документ1");

    var basedOn = findMember(members, "ВводитсяНаОсновании");
    assertThat(basedOn).isNotNull();
    assertThat(basedOn.returnType().qualifiedName())
      .isEqualTo("КоллекцияЗначенийСвойстваОбъектаМетаданных.ВводитсяНаОсновании.Документ1");

    var inputByString = findMember(members, "ВводПоСтроке");
    assertThat(inputByString).isNotNull();
    assertThat(inputByString.returnType().qualifiedName())
      .isEqualTo("СписокПолей.ВводПоСтроке.Документ1");

    var dataLockFields = findMember(members, "ПоляБлокировкиДанных");
    assertThat(dataLockFields).isNotNull();
    assertThat(dataLockFields.returnType().qualifiedName())
      .isEqualTo("СписокПолей.ПоляБлокировкиДанных.Документ1");

    var additionalIndexes = findMember(members, "ДополнительныеИндексы");
    assertThat(additionalIndexes).isNotNull();
    assertThat(additionalIndexes.returnType().qualifiedName())
      .isEqualTo("ДополнительныеИндексы.ДополнительныеИндексы.Документ1");
  }

  @Test
  void movementsExpandToConcreteRegisterTypeRefs() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Документ1 имеет RegisterRecords: AccumulationRegister.РегистрНакопления1,
    // CalculationRegister.РегистрРасчета1, InformationRegister.РегистрСведений2.
    // Каждый member в синтетике .Движения должен указывать на конкретный
    // ОбъектМетаданных: <тип регистра>.<имя>, не на общий
    // ЗначениеСвойстваОбъектаМетаданных.
    var movementsCollRef = typeRegistry.intern(TypeKind.PLATFORM,
      "КоллекцияЗначенийСвойстваОбъектаМетаданных.Движения.Документ1");
    var members = typeRegistry.getMembers(movementsCollRef, FileType.BSL);
    var byName = new java.util.HashMap<String, MemberDescriptor>();
    for (var m : members) {
      byName.put(m.name(), m);
    }

    var accReg = byName.get("РегистрНакопления1");
    assertThat(accReg).as("РегистрНакопления1 как член коллекции Движения").isNotNull();
    assertThat(accReg.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: РегистрНакопления.РегистрНакопления1");

    var calcReg = byName.get("РегистрРасчета1");
    assertThat(calcReg).isNotNull();
    assertThat(calcReg.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: РегистрРасчета.РегистрРасчета1");

    var infoReg = byName.get("РегистрСведений2");
    assertThat(infoReg).isNotNull();
    assertThat(infoReg.returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: РегистрСведений.РегистрСведений2");
  }

  @Test
  void elementReturningMethodsAreSpecialized() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Метод Получить на synthetic-типе → returnType конкретный element-type.
    var groupRef = typeRegistry.intern(
      com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM,
      "КоллекцияОбъектовМетаданных.Документы");
    var getMethod = findMember(typeRegistry.getMembers(groupRef, FileType.BSL), "Получить");
    assertThat(getMethod).as("Метод Получить должен присутствовать").isNotNull();
    assertThat(getMethod.returnType().qualifiedName())
      .as("Получить() на КоллекцияОбъектовМетаданных.Документы → ОбъектМетаданных: Документ")
      .isEqualTo("ОбъектМетаданных: Документ");
  }

  // --- helpers ---

  private static MemberDescriptor findMember(Collection<MemberDescriptor> members, String name) {
    for (var m : members) {
      if (m.matches(name)) {
        return m;
      }
    }
    return null;
  }

  private static List<String> memberNames(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }
}
