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
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрывает новые API {@link TypeRegistry}, появившиеся в Фазе 1, на состоянии
 * registry после bootstrap'а из {@code builtin-platform-types.json}:
 * <ul>
 *   <li>{@code registerSpecialization} — пробрасывает members generic'а
 *       с подменой placeholder'ов;</li>
 *   <li>{@code findAllGenericsByFamilyCore} — итерирует typeParameters-индекс;</li>
 *   <li>{@code getTypeParameters} — структурное представление placeholder'ов;</li>
 *   <li>{@code hasAnyReadOnlyMember} / {@code isReadOnlyMemberName} /
 *       {@code isReadOnlyMember} — индексы read-only-членов (для diagnostic'а);</li>
 *   <li>{@code getMembers} fallback по qualifiedName (kind-mismatch).</li>
 * </ul>
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TypeRegistrySpecializationTest {

  @Autowired
  private TypeRegistry typeRegistry;

  // === getTypeParameters + findAllGenericsByFamilyCore ===

  @Test
  void getTypeParametersExposesPlaceholderFromJsonFallback() {
    // JSON-fallback регистрирует СправочникСсылка.<Имя справочника> как generic.
    var ref = typeRegistry.resolve("СправочникСсылка.<Имя справочника>").orElseThrow();
    assertThat(typeRegistry.getTypeParameters(ref)).containsExactly("Имя справочника");
  }

  @Test
  void findAllGenericsByFamilyCoreReturnsAllCatalogGenerics() {
    var generics = typeRegistry.findAllGenericsByFamilyCore("Справочник");
    var names = generics.stream().map(TypeRef::qualifiedName).toList();
    assertThat(names)
      .anyMatch(n -> n.startsWith("СправочникСсылка.<"))
      .anyMatch(n -> n.startsWith("СправочникОбъект.<"))
      .anyMatch(n -> n.startsWith("СправочникМенеджер.<"));
  }

  // === registerSpecialization ===

  @Test
  void registerSpecializationSubstitutesPlaceholdersInMembers() {
    var generic = typeRegistry.resolve("СправочникСсылка.<Имя справочника>").orElseThrow();
    var specRef = typeRegistry.registerSpecialization(
      "СправочникСсылка.МойТестовый", generic,
      Map.of("Имя справочника", "МойТестовый"),
      FileType.BSL);

    assertThat(specRef.qualifiedName()).isEqualTo("СправочникСсылка.МойТестовый");

    var members = typeRegistry.getMembers(specRef);
    var memberNames = members.stream().map(MemberDescriptor::name).toList();
    // На СправочникСсылка.<Имя> в JSON есть Ссылка/Код/ПолучитьОбъект и т.п.
    assertThat(memberNames)
      .as("members generic-типа должны пробрасываться")
      .contains("Ссылка", "Код", "ПолучитьОбъект");

    // ПолучитьОбъект возвращает СправочникОбъект.<Имя справочника> — после
    // специализации должен возвращать СправочникОбъект.МойТестовый.
    var getObject = members.stream()
      .filter(m -> "ПолучитьОбъект".equals(m.name()))
      .findFirst().orElseThrow();
    assertThat(getObject.returnType().qualifiedName())
      .isEqualTo("СправочникОбъект.МойТестовый");
  }

  @Test
  void specializedTypeDisplayNameIsBilingualFromGenericDisplay() {
    // Двуязычное display-имя специализации: ru — из qualifiedName, en —
    // структурная подстановка MD-имени в en-написание display-имени generic'а
    // (placeholder'ы из bsl-context, без парсинга скобок на стороне LS).
    var generic = typeRegistry.registerConfigurationType("ТестДженерик.<Имя>");
    typeRegistry.registerDisplayName(generic,
      BilingualString.of("ТестДженерик.<Имя>", "TestGeneric.<Name>"));

    var specRef = typeRegistry.registerSpecialization(
      "ТестДженерик.Контрагенты", generic,
      Map.of("Имя", "Контрагенты"),
      FileType.BSL);

    assertThat(typeRegistry.displayName(specRef, Language.RU)).isEqualTo("ТестДженерик.Контрагенты");
    assertThat(typeRegistry.displayName(specRef, Language.EN)).isEqualTo("TestGeneric.Контрагенты");
  }

  // === read-only members ===

  @Test
  void readOnlyIndexesPopulatedFromJsonAccessModeMarkers() {
    // В builtin-platform-types.json свойство Ссылка на СправочникСсылка
    // помечено accessMode=READ. После bootstrap'а это должно отражаться
    // в индексах.
    assertThat(typeRegistry.hasAnyReadOnlyMember()).isTrue();
    assertThat(typeRegistry.isReadOnlyMemberName("Ссылка")).isTrue();
    assertThat(typeRegistry.isReadOnlyMemberName("Несуществующее")).isFalse();

    var refType = typeRegistry.resolve("СправочникСсылка.<Имя справочника>").orElseThrow();
    assertThat(typeRegistry.isReadOnlyMember(refType, "Ссылка"))
      .as("у СправочникСсылка все свойства read-only — мутабельны только у Объекта")
      .isTrue();
    assertThat(typeRegistry.isReadOnlyMember(refType, "Код"))
      .as("Код у СправочникСсылка тоже read-only (мутабелен только у Объекта)")
      .isTrue();
    assertThat(typeRegistry.isReadOnlyMember(refType, "Несуществующее"))
      .as("несуществующий member не должен быть в индексе")
      .isFalse();
  }

  // === getMembers fallback по qualifiedName ===

  @Test
  void getMembersFallsBackToCanonicalRefByName() {
    var configRef = typeRegistry.registerConfigurationType("ТипДляFallback");
    typeRegistry.registerMemberSource(configRef,
      () -> List.of(MemberDescriptor.property("М", TypeRef.UNKNOWN, "")),
      FileType.BSL);

    // Запрос с другим kind (PLATFORM вместо CONFIGURATION) — должен через
    // aliasIndex резолвиться в canonical ref и отдать тот же member.
    var platformLookup = new TypeRef(TypeKind.PLATFORM, "ТипДляFallback");
    var members = typeRegistry.getMembers(platformLookup);
    assertThat(members)
      .extracting(MemberDescriptor::name)
      .containsExactly("М");
  }

  // === Двуязычные имена из JSON-fallback ===

  @Test
  void jsonNameRuAndNameEnPropagateToMemberDescriptor() {
    // В builtin-platform-types.json у Массив.Добавить заданы nameRu/nameEn,
    // и аналогично для его параметра Значение → Value. После bootstrap'а
    // member и параметр должны резолвиться через matches() в обе стороны
    // и отдавать правильное displayName для нужной локали.
    // BSL-провайдер хранит localized-names; OScript-JSON держит тип Массив со
    // своим набором членов без имен. Фильтр по FileType.BSL отбрасывает
    // OScript-источник (его scope=OS), и в getMembers попадает только BSL.
    var arrayRef = typeRegistry.resolve("Массив").orElseThrow();
    var add = typeRegistry.getMembers(arrayRef,
        com.github._1c_syntax.bsl.languageserver.context.FileType.BSL).stream()
      .filter(m -> "Добавить".equalsIgnoreCase(m.name()))
      .findFirst().orElseThrow();
    assertThat(add.bilingualName().ru()).isEqualTo("Добавить");
    assertThat(add.bilingualName().en()).isEqualTo("Add");
    assertThat(add.matches("Add"))
      .as("lookup по en-имени должен находить member, объявленный в ru")
      .isTrue();
    assertThat(add.matches("Добавить")).isTrue();
    assertThat(add.displayName(
      com.github._1c_syntax.bsl.languageserver.configuration.Language.EN)).isEqualTo("Add");
    assertThat(add.displayName(
      com.github._1c_syntax.bsl.languageserver.configuration.Language.RU)).isEqualTo("Добавить");

    var param = add.signatures().get(0).parameters().get(0);
    assertThat(param.bilingualName().ru()).isEqualTo("Значение");
    assertThat(param.bilingualName().en()).isEqualTo("Value");
    assertThat(param.matches("Value")).isTrue();
    assertThat(param.matches("Значение")).isTrue();
  }
}
