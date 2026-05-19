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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ConfigurationTypesProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private TypeService typeService;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void registersCatalogTypesWithRuAndEnAliases() {
    initServerContext(PATH_TO_METADATA);
    // прогреваем lazy-конфигурацию
    context.getConfiguration();
    provider.tryRegister();

    var ru = typeRegistry.resolve("Справочники.Справочник1");
    var en = typeRegistry.resolve("Catalogs.Справочник1");
    var managerRu = typeRegistry.resolve("СправочникМенеджер.Справочник1");
    var managerEn = typeRegistry.resolve("CatalogManager.Справочник1");

    assertThat(ru).isPresent();
    assertThat(en).isPresent();
    assertThat(managerRu).isPresent();
    assertThat(managerEn).isPresent();
    assertThat(en.get()).isEqualTo(ru.get());
    // Каноническая регистрация — менеджер; короткие формы — алиасы на тот же TypeRef.
    assertThat(ru.get()).isEqualTo(managerRu.get());
    assertThat(managerEn.get()).isEqualTo(managerRu.get());
  }

  @Test
  void registersCollectionNamespacesWithMetadataMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    var nsRu = typeService.findGlobalPropertyType("Справочники");
    var nsEn = typeService.findGlobalPropertyType("Catalogs");

    assertThat(nsRu).isPresent();
    assertThat(nsEn).isPresent();
    assertThat(nsEn.get()).isEqualTo(nsRu.get());

    var members = typeRegistry.getMembers(nsRu.get());
    assertThat(members)
      .extracting(m -> m.name())
      .contains("Справочник1");

    var member = members.stream()
      .filter(m -> "Справочник1".equals(m.name()))
      .findFirst()
      .orElseThrow();
    // Член коллекции теперь указывает на менеджер-обёртку, чтобы методы
    // ManagerModule корректно подтягивались через единый TypeRef.
    assertThat(member.returnType().qualifiedName()).isEqualTo("СправочникМенеджер.Справочник1");
  }

  @Test
  void registersConfigurationQualifiedNamesForCompletion() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    var qualified = globalScopeProvider.getConfigurationQualifiedNames();
    assertThat(qualified)
      .as("должны быть составные имена коллекция.Имя для no-dot completion")
      .contains("Справочники.Справочник1", "Catalogs.Справочник1");
  }

  @Test
  void documentRefInheritsPlatformMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Конкретный тип `ДокументСсылка.Документ1` получает members обоих источников:
    // реквизиты из метаданных + платформенные members из generic-семейства
    // (Дата, Номер, ВерсияДанных, Метаданные(), ПолучитьОбъект()).
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var members = typeRegistry.getMembers(ref.get());
    var names = members.stream().map(m -> m.name()).toList();
    assertThat(names)
      .as("платформенные стандартные свойства должны подмешиваться")
      .contains("Дата", "Номер", "Ссылка", "Проведен", "ВерсияДанных");
    assertThat(names)
      .as("платформенные методы тоже")
      .contains("Метаданные", "ПолучитьОбъект");
    assertThat(names)
      .as("реквизиты из метаданных тоже на месте")
      .contains("Реквизит1");
  }

  @Test
  void documentManagerInheritsPlatformMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // На `ДокументМенеджер.Документ1` подмешиваются методы платформенного
    // generic-менеджера документа (НайтиПоНомеру, СоздатьДокумент, …).
    var ref = typeRegistry.resolve("ДокументМенеджер.Документ1");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get()).stream().map(m -> m.name()).toList();
    assertThat(names).contains("НайтиПоНомеру", "СоздатьДокумент", "ПустаяСсылка", "Выбрать");
  }

  @Test
  void documentsCollectionInheritsCollectionManagerMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // На коллекции `Документы` подмешиваются методы платформенного
    // `ДокументыМенеджер` (ТипВсеСсылки).
    var ref = typeRegistry.resolve("Документы");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get()).stream().map(m -> m.name()).toList();
    assertThat(names)
      .as("MD-инстансы и метод коллекции-менеджера")
      .contains("Документ1", "ТипВсеСсылки");
  }

  @Test
  void genericSlotsFilteredOutFromInheritedMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // Generic-слоты <Имя реквизита>, <Имя общего реквизита> платформенного
    // generic-типа не должны утекать в специализированный ref/object тип.
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get()).stream().map(m -> m.name()).toList();
    assertThat(names).noneMatch(n -> n.startsWith("<") && n.endsWith(">"));
  }

  @Test
  void standardAttributesGetDescriptionsFromPlatform() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // У стандартного реквизита `Дата` в mdclasses описание пустое, а
    // платформенный generic-тип в HBK содержит описание — оно должно
    // подмешиваться при сборке member'а.
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var data = typeRegistry.getMembers(ref.get()).stream()
      .filter(m -> "Дата".equalsIgnoreCase(m.name()))
      .findFirst();
    assertThat(data).isPresent();
    assertThat(data.get().description())
      .as("описание `Дата` должно прийти из платформенного generic-типа")
      .contains("дату");
  }

  @Test
  void documentExposesTabularSectionMember() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // На `ДокументОбъект.Документ1` есть member-property `ТабличнаяЧасть1`,
    // тип которого — `ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1`.
    var objectRef = typeRegistry.resolve("ДокументОбъект.Документ1");
    assertThat(objectRef).isPresent();
    var ts = typeRegistry.getMembers(objectRef.get()).stream()
      .filter(m -> "ТабличнаяЧасть1".equals(m.name()))
      .findFirst();
    assertThat(ts).isPresent();
    assertThat(ts.get().returnType().qualifiedName())
      .isEqualTo("ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
  }

  @Test
  void commonAttributeAddedToApplicableDocument() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    // `ОбщийРеквизит1`: content явно включает Документ1 (Use=USE) и исключает
    // Справочник1 (DontUse). Поэтому ref-тип документа получает реквизит как
    // member, а ref-тип справочника — нет.
    var docRef = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(docRef).isPresent();
    var docMembers = typeRegistry.getMembers(docRef.get()).stream().map(m -> m.name()).toList();
    assertThat(docMembers).contains("ОбщийРеквизит1");

    var catRef = typeRegistry.resolve("СправочникСсылка.Справочник1");
    assertThat(catRef).isPresent();
    var catMembers = typeRegistry.getMembers(catRef.get()).stream().map(m -> m.name()).toList();
    assertThat(catMembers).doesNotContain("ОбщийРеквизит1");
  }

  @Test
  void tabularSectionRowExposesColumns() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    var rowRef = typeRegistry.resolve("ДокументТабличнаяЧастьСтрока.Документ1.ТабличнаяЧасть1");
    assertThat(rowRef).isPresent();
    var names = typeRegistry.getMembers(rowRef.get()).stream().map(m -> m.name()).toList();
    assertThat(names).contains("Реквизит1", "Реквизит2");
  }
}
