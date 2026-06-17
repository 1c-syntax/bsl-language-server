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
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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

  @Autowired
  private LanguageServerConfiguration configuration;

  @BeforeEach
  void setUp() {
    // initServerContextOnce + tryRegister один раз на класс — тесты read-only:
    // дёргают только typeRegistry.resolve/getMembers и typeService.displayName,
    // состояние ServerContext'а/реестра не модифицируют.
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void registersCatalogTypesWithRuAndEnAliases() {
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
  void configurationTypeDisplayNamesAreBilingual() {

    // Менеджер-обёртка: в EN показывается CatalogManager.Справочник1, а не ru-написание.
    var managerRef = typeRegistry.resolve("СправочникМенеджер.Справочник1").orElseThrow();
    assertThat(typeService.displayName(managerRef, Language.RU))
      .isEqualTo("СправочникМенеджер.Справочник1");
    assertThat(typeService.displayName(managerRef, Language.EN))
      .isEqualTo("CatalogManager.Справочник1");

    // Коллекция-namespace: Справочники / Catalogs.
    var collectionRef = typeRegistry.resolve("Справочники").orElseThrow();
    assertThat(typeService.displayName(collectionRef, Language.RU)).isEqualTo("Справочники");
    assertThat(typeService.displayName(collectionRef, Language.EN)).isEqualTo("Catalogs");
  }

  @Test
  void informationRegisterManagerInheritsPlatformMembers() {
    // Менеджер регистра сведений специализируется платформенным generic'ом
    // РегистрСведенийМенеджер.<Имя> и наследует его методы.

    var managerRef = typeRegistry.resolve("РегистрСведенийМенеджер.РегистрСведений1").orElseThrow();
    var memberNames = typeRegistry.getMembers(managerRef, FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(memberNames)
      .as("менеджер регистра сведений должен наследовать платформенные методы из generic'а")
      .contains("СоздатьНаборЗаписей", "Выбрать");

    // Цепочка РегистрыСведений.РегистрСведений1 ведёт на тот же менеджер.
    var viaCollection = typeRegistry.resolve("РегистрыСведений.РегистрСведений1").orElseThrow();
    assertThat(viaCollection).isEqualTo(managerRef);
  }

  @Test
  void filterCriterionManagerInheritsPlatformMembers() {
    // Менеджер критерия отбора специализируется платформенным generic'ом
    // КритерийОтбораМенеджер.<Имя> и наследует его методы.

    var managerRef = typeRegistry.resolve("КритерийОтбораМенеджер.КритерийОтбора1").orElseThrow();
    var memberNames = typeRegistry.getMembers(managerRef, FileType.BSL).stream().map(MemberDescriptor::name).toList();
    assertThat(memberNames)
      .as("менеджер критерия отбора должен наследовать платформенные методы из generic'а")
      .contains("Выбрать", "ПолучитьФорму");
  }

  @Test
  void catalogManagerExposesPredefinedValues() {
    // Менеджер справочника отдаёт предопределённые значения как члены
    // (Справочники.Справочник1.ПредопределённыйЭлемент1), включая вложенные в группах.

    var managerRef = typeRegistry.resolve("СправочникМенеджер.Справочник1").orElseThrow();
    var memberNames = typeRegistry.getMembers(managerRef, FileType.BSL).stream().map(MemberDescriptor::name).toList();
    assertThat(memberNames)
      .as("менеджер справочника должен отдавать предопределённые значения, включая вложенные в группах")
      .contains(
        "ПредопределённыйЭлемент1",
        "ПредопределённыйЭлемент2",
        "ПредопределённаяГруппа",
        "ВложенныйЭлемент");

    // Цепочка Справочники.Справочник1 ведёт на тот же менеджер — предопределённые видны и через неё.
    var viaCollection = typeRegistry.resolve("Справочники.Справочник1").orElseThrow();
    assertThat(typeRegistry.getMembers(viaCollection, FileType.BSL).stream().map(MemberDescriptor::name))
      .contains("ПредопределённыйЭлемент1", "ВложенныйЭлемент");
  }

  @Test
  void registersCollectionNamespacesWithMetadataMembers() {

    var nsRu = globalScopeProvider.globalMember("Справочники", FileType.BSL)
      .map(member -> member.returnTypes().refs().stream().findFirst().orElseThrow());
    var nsEn = globalScopeProvider.globalMember("Catalogs", FileType.BSL)
      .map(member -> member.returnTypes().refs().stream().findFirst().orElseThrow());

    assertThat(nsRu).isPresent();
    assertThat(nsEn).isPresent();
    assertThat(nsEn.get()).isEqualTo(nsRu.get());

    var members = typeRegistry.getMembers(nsRu.get(), FileType.BSL);
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

    var qualified = globalScopeProvider.getConfigurationQualifiedNames();
    assertThat(qualified)
      .as("должны быть составные имена коллекция.Имя для no-dot completion")
      .contains("Справочники.Справочник1", "Catalogs.Справочник1");
  }

  @Test
  void documentRefInheritsPlatformMembers() {

    // Конкретный тип `ДокументСсылка.Документ1` получает members обоих источников:
    // реквизиты из метаданных + платформенные members из generic-семейства
    // (Дата, Номер, ВерсияДанных, Метаданные(), ПолучитьОбъект()).
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var members = typeRegistry.getMembers(ref.get(), FileType.BSL);
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

    // На `ДокументМенеджер.Документ1` подмешиваются методы платформенного
    // generic-менеджера документа (НайтиПоНомеру, СоздатьДокумент, …).
    var ref = typeRegistry.resolve("ДокументМенеджер.Документ1");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(names).contains("НайтиПоНомеру", "СоздатьДокумент", "ПустаяСсылка", "Выбрать");
  }

  @Test
  void emptyRefOnManagerReturnsSpecializedRef() {

    // На `ДокументМенеджер.Документ1.ПустаяСсылка()` тип возврата должен
    // специализироваться в конкретный `ДокументСсылка.Документ1`, а не
    // оставаться generic-шаблоном `ДокументСсылка.<Имя документа>`.
    var ref = typeRegistry.resolve("ДокументМенеджер.Документ1").orElseThrow();
    var empty = typeRegistry.getMembers(ref, FileType.BSL).stream()
      .filter(m -> "ПустаяСсылка".equals(m.name()))
      .findFirst().orElseThrow();
    assertThat(empty.returnType().qualifiedName())
      .as("ПустаяСсылка должна вернуть специализированный ДокументСсылка.Документ1")
      .isEqualTo("ДокументСсылка.Документ1");

    var catRef = typeRegistry.resolve("СправочникМенеджер.Справочник1").orElseThrow();
    var catEmpty = typeRegistry.getMembers(catRef, FileType.BSL).stream()
      .filter(m -> "ПустаяСсылка".equals(m.name()))
      .findFirst().orElseThrow();
    assertThat(catEmpty.returnType().qualifiedName())
      .as("ПустаяСсылка на СправочникМенеджер.Справочник1 → СправочникСсылка.Справочник1")
      .isEqualTo("СправочникСсылка.Справочник1");
  }

  @Test
  void documentsCollectionInheritsCollectionManagerMembers() {

    // На коллекции `Документы` подмешиваются методы платформенного
    // `ДокументыМенеджер` (ТипВсеСсылки).
    var ref = typeRegistry.resolve("Документы");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(names)
      .as("MD-инстансы и метод коллекции-менеджера")
      .contains("Документ1", "ТипВсеСсылки");
  }

  @Test
  void genericSlotsFilteredOutFromInheritedMembers() {

    // Generic-слоты <Имя реквизита>, <Имя общего реквизита> платформенного
    // generic-типа не должны утекать в специализированный ref/object тип.
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var names = typeRegistry.getMembers(ref.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(names).isNotEmpty().noneMatch(n -> n.startsWith("<") && n.endsWith(">"));
  }

  @Test
  void standardAttributesGetDescriptionsFromPlatform() {

    // У стандартного реквизита `Дата` в mdclasses описание пустое, а
    // платформенный generic-тип в HBK содержит описание — оно должно
    // подмешиваться при сборке member'а.
    var ref = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(ref).isPresent();
    var data = typeRegistry.getMembers(ref.get(), FileType.BSL).stream()
      .filter(m -> "Дата".equalsIgnoreCase(m.name()))
      .findFirst();
    assertThat(data).isPresent();
    assertThat(data.get().description())
      .as("описание `Дата` должно прийти из платформенного generic-типа")
      .contains("дату");
  }

  @Test
  void documentExposesTabularSectionMember() {

    // На `ДокументОбъект.Документ1` есть member-property `ТабличнаяЧасть1`,
    // тип которого — `ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1`.
    var objectRef = typeRegistry.resolve("ДокументОбъект.Документ1");
    assertThat(objectRef).isPresent();
    var ts = typeRegistry.getMembers(objectRef.get(), FileType.BSL).stream()
      .filter(m -> "ТабличнаяЧасть1".equals(m.name()))
      .findFirst();
    assertThat(ts).isPresent();
    assertThat(ts.get().returnType().qualifiedName())
      .isEqualTo("ДокументТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
  }

  @Test
  void commonAttributeAddedToApplicableDocument() {

    // `ОбщийРеквизит1`: content явно включает Документ1 (Use=USE) и исключает
    // Справочник1 (DontUse). Поэтому ref-тип документа получает реквизит как
    // member, а ref-тип справочника — нет.
    var docRef = typeRegistry.resolve("ДокументСсылка.Документ1");
    assertThat(docRef).isPresent();
    var docMembers = typeRegistry.getMembers(docRef.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(docMembers).contains("ОбщийРеквизит1");

    var catRef = typeRegistry.resolve("СправочникСсылка.Справочник1");
    assertThat(catRef).isPresent();
    var catMembers = typeRegistry.getMembers(catRef.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(catMembers).isNotEmpty().doesNotContain("ОбщийРеквизит1");
  }

  @Test
  void tabularSectionRowExposesColumns() {

    var rowRef = typeRegistry.resolve("ДокументТабличнаяЧастьСтрока.Документ1.ТабличнаяЧасть1");
    assertThat(rowRef).isPresent();
    var names = typeRegistry.getMembers(rowRef.get(), FileType.BSL).stream().map(m -> m.name()).toList();
    assertThat(names).contains("Реквизит1", "Реквизит2");
  }

  @Test
  void standardAttributesAreBilingualIndependentOfConfiguredLanguage() {
    // Стандартные реквизиты (Дата/Номер/Ссылка/...) хранятся в MemberDescriptor
    // двуязычно: bilingualName.matches(name) находит член по любому написанию,
    // displayName(language) возвращает имя в нужной локали. Это и есть единая
    // точка для hover/диагностик (через matches) и completion (через displayName)
    // без необходимости держать два параллельных дескриптора per-language.

    var ref = typeRegistry.resolve("ДокументСсылка.Документ1").orElseThrow();
    var members = typeRegistry.getMembers(ref, FileType.BSL);

    // matches должен находить «Дата»/«Date», «Номер»/«Number», «Ссылка»/«Ref»
    // в одном и том же MemberDescriptor — без дублирования.
    for (var pair : List.of(
      new String[]{"Дата", "Date"},
      new String[]{"Номер", "Number"},
      new String[]{"Ссылка", "Ref"}
    )) {
      var ru = pair[0];
      var en = pair[1];
      var matchedByRu = members.stream().filter(m -> m.matches(ru)).toList();
      var matchedByEn = members.stream().filter(m -> m.matches(en)).toList();
      assertThat(matchedByRu)
        .as("matches(%s) должен находить ровно один член", ru)
        .hasSize(1);
      assertThat(matchedByEn)
        .as("matches(%s) должен находить ровно один член — тот же, что и matches(%s)", en, ru)
        .hasSize(1)
        .containsExactlyElementsOf(matchedByRu);
      var descriptor = matchedByRu.get(0);
      assertThat(descriptor.displayName(Language.RU)).isEqualTo(ru);
      assertThat(descriptor.displayName(Language.EN)).isEqualTo(en);
    }
  }

  @Test
  void standardAttributeDescriptionsAreBilingual() {
    // Описания стандартных реквизитов подмешиваются из платформенного generic-типа
    // (СправочникСсылка.<Имя справочника>). В JSON-fallback они заданы двуязычно
    // (descriptionRu/descriptionEn), и displayDescription(language) должен отдавать
    // описание в нужной локали — иначе hover на en-проекте показывает ru-текст.

    var ref = typeRegistry.resolve("СправочникСсылка.Справочник1").orElseThrow();
    var members = typeRegistry.getMembers(ref, FileType.BSL);

    var ssylka = members.stream()
      .filter(m -> m.matches("Ссылка"))
      .findFirst()
      .orElseThrow();
    assertThat(ssylka.displayName(Language.RU)).isEqualTo("Ссылка");
    assertThat(ssylka.displayName(Language.EN)).isEqualTo("Ref");
    assertThat(ssylka.displayDescription(Language.RU)).contains("ссылку на элемент справочника");
    assertThat(ssylka.displayDescription(Language.EN)).contains("reference to the catalog item");

    var code = members.stream()
      .filter(m -> m.matches("Code"))
      .findFirst()
      .orElseThrow();
    assertThat(code.matches("Код")).isTrue();
    assertThat(code.displayDescription(Language.RU)).contains("код элемента справочника");
    assertThat(code.displayDescription(Language.EN)).contains("catalog item code");
  }
}
