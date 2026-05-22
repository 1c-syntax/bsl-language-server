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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты register*-API и lookup-методов {@link TypeRegistry} (intern,
 * registerUserType/ConfigurationType/Alias, resolveGenericByPrefix,
 * findAllGenericsByFamilyCore, setLanguageScope).
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@ExtendWith(MockitoExtension.class)
class TypeRegistryRegistrationTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Mock
  private SourceDefinedSymbol declaration;

  @Test
  void internReturnsSameInstanceForSameKindAndName() {
    // given / when
    var a = typeRegistry.intern(TypeKind.USER, "X");
    var b = typeRegistry.intern(TypeKind.USER, "X");

    // then
    assertThat(a).isSameAs(b);
  }

  @Test
  void internReturnsDifferentInstanceForDifferentKind() {
    // given / when
    var user = typeRegistry.intern(TypeKind.USER, "X");
    var conf = typeRegistry.intern(TypeKind.CONFIGURATION, "X");

    // then
    assertThat(user).isNotEqualTo(conf);
  }

  @Test
  void registerUserTypeAddsAlias() {
    // given / when
    var ref = typeRegistry.registerUserType("МойКласс", declaration);

    // then
    assertThat(typeRegistry.resolve("МойКласс")).contains(ref);
    assertThat(typeRegistry.resolve("мойкласс"))
      .as("alias case-insensitive")
      .contains(ref);
  }

  @Test
  void registerUserTypeRespectsLanguageScope() {
    // given / when
    var ref = typeRegistry.registerUserType("МойOSКласс", declaration, LanguageScope.OS);

    // then
    assertThat(typeRegistry.getLanguageScope(ref)).isEqualTo(LanguageScope.OS);
    assertThat(typeRegistry.resolve("МойOSКласс", FileType.OS)).contains(ref);
    assertThat(typeRegistry.resolve("МойOSКласс", FileType.BSL)).isEmpty();
  }

  @Test
  void registerConfigurationTypeIsBslOnly() {
    // given / when
    var ref = typeRegistry.registerConfigurationType("Справочники.Контрагенты");

    // then
    assertThat(typeRegistry.getLanguageScope(ref)).isEqualTo(LanguageScope.BSL);
    assertThat(typeRegistry.resolve("Справочники.Контрагенты", FileType.BSL)).contains(ref);
    assertThat(typeRegistry.resolve("Справочники.Контрагенты", FileType.OS))
      .as("конфигурационные типы недоступны в OS")
      .isEmpty();
  }

  @Test
  void registerConfigurationTypeAliasMakesAdditionalNameResolvable() {
    // given
    var ref = typeRegistry.registerConfigurationType("Справочники.Counterparts");

    // when
    typeRegistry.registerConfigurationTypeAlias("Catalogs.Counterparts", ref);

    // then
    assertThat(typeRegistry.resolve("Catalogs.Counterparts")).contains(ref);
    assertThat(typeRegistry.resolve("catalogs.counterparts")).contains(ref);
  }

  @Test
  void setLanguageScopeOverwritesExistingScope() {
    // given
    var ref = typeRegistry.registerUserType("Y", declaration, LanguageScope.OS);

    // when
    typeRegistry.setLanguageScope(ref, LanguageScope.BOTH);

    // then
    assertThat(typeRegistry.getLanguageScope(ref)).isEqualTo(LanguageScope.BOTH);
  }

  @Test
  void getLanguageScopeReturnsBothForUnknownRef() {
    // given
    var ref = new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(
      TypeKind.USER, "НезарегистрированныйТип");

    // when / then
    assertThat(typeRegistry.getLanguageScope(ref)).isEqualTo(LanguageScope.BOTH);
  }

  @Test
  void resolveReturnsEmptyForBlankName() {
    // when / then
    assertThat(typeRegistry.resolve(null)).isEmpty();
    assertThat(typeRegistry.resolve("")).isEmpty();
  }

  @Test
  void resolveGenericByPrefixReturnsEmptyForBlank() {
    // when / then
    assertThat(typeRegistry.resolveGenericByPrefix(null)).isEmpty();
    assertThat(typeRegistry.resolveGenericByPrefix("")).isEmpty();
  }

  @Test
  void findAllGenericsByFamilyCoreReturnsEmptyForBlank() {
    // when / then
    assertThat(typeRegistry.findAllGenericsByFamilyCore(null)).isEmpty();
    assertThat(typeRegistry.findAllGenericsByFamilyCore("")).isEmpty();
  }

  @Test
  void registerDescriptionIgnoresBlankInput() {
    // given
    var ref = typeRegistry.registerUserType("Т", declaration);

    // when
    typeRegistry.registerDescription(null, "x", LanguageScope.BOTH);
    typeRegistry.registerDescription(ref, null, LanguageScope.BOTH);
    typeRegistry.registerDescription(ref, "  ", LanguageScope.BOTH);

    // then — описание не сохранилось
    assertThat(typeRegistry.getDescription(ref)).isEmpty();
  }

  @Test
  void registerDescriptionStoresAndExposesByScope() {
    // given
    var ref = typeRegistry.registerUserType("Т2", declaration);

    // when
    typeRegistry.registerDescription(ref, "ru-описание", LanguageScope.BOTH);

    // then
    assertThat(typeRegistry.getDescription(ref)).isEqualTo("ru-описание");
  }

  @Test
  void registerConstructorsIgnoresEmptyList() {
    // given
    var ref = typeRegistry.registerUserType("ТипK", declaration);

    // when
    typeRegistry.registerConstructors(ref, java.util.List.of(), LanguageScope.BOTH);
    typeRegistry.registerConstructors(null,
      java.util.List.of(com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor.EMPTY),
      LanguageScope.BOTH);

    // then — конструкторы не зарегистрированы
    assertThat(typeRegistry.getConstructors(ref)).isEmpty();
  }

  @Test
  void supportsForEachAndIndexAccessReturnFalseByDefault() {
    // given
    var ref = typeRegistry.registerUserType("ТипУ", declaration);

    // when / then — без явной регистрации флаги выключены.
    assertThat(typeRegistry.supportsForEach(ref)).isFalse();
    assertThat(typeRegistry.supportsIndexAccess(ref)).isFalse();
  }

  @Test
  void getTypeParametersReturnsEmptyForNonGeneric() {
    // given
    var ref = typeRegistry.registerUserType("ТипП", declaration);

    // when / then
    assertThat(typeRegistry.getTypeParameters(ref)).isEmpty();
  }

  @Test
  void displayNameFallsBackToQualifiedNameWhenNoBilingual() {
    // given
    var ref = typeRegistry.registerUserType("ТипD", declaration);

    // when
    var ru = typeRegistry.displayName(ref,
      com.github._1c_syntax.bsl.languageserver.configuration.Language.RU);

    // then
    assertThat(ru).isEqualTo("ТипD");
  }

  @Test
  void getForEachDescriptionDefaultsToEmpty() {
    // given
    var ref = typeRegistry.registerUserType("ТипF", declaration);

    // when / then
    assertThat(typeRegistry.getForEachDescription(ref)).isEmpty();
    assertThat(typeRegistry.getIndexAccessDescription(ref)).isEmpty();
  }

  @Test
  void getDefaultElementTypesEmptyForNonCollection() {
    // given
    var ref = typeRegistry.registerUserType("ТипE", declaration);

    // when / then
    assertThat(typeRegistry.getDefaultElementTypes(ref))
      .isSameAs(com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.EMPTY);
  }

  @Test
  void resolveByKindAndQualifiedNameReturnsInternedRef() {
    // given
    var ref = typeRegistry.intern(TypeKind.USER, "ТипR2");

    // when
    var resolved = typeRegistry.resolve(TypeKind.USER, "ТипR2");

    // then
    assertThat(resolved).contains(ref);
  }

  @Test
  void resolveByKindReturnsEmptyForUnknownType() {
    // when / then
    assertThat(typeRegistry.resolve(TypeKind.USER, "ОченьНеизвестныйТип")).isEmpty();
  }

  @Test
  void getReturnsUnknownTypeForUnregistered() {
    // given
    var ref = new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(
      TypeKind.USER, "ЕщёНеизвестныйТип");

    // when
    var t = typeRegistry.get(ref);

    // then — для незарегистрированного ref возвращается UnknownType.INSTANCE
    assertThat(t).isInstanceOf(
      com.github._1c_syntax.bsl.languageserver.types.model.UnknownType.class);
  }

  @Test
  void registerConstructorSourceWithoutScopeUsesBoth() {
    // given
    var ref = typeRegistry.registerUserType("МойТипC", declaration);
    var sig = com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor.EMPTY;

    // when — overload без scope.
    typeRegistry.registerConstructorSource(ref, () -> java.util.List.of(sig));

    // then — конструктор виден через getConstructors.
    assertThat(typeRegistry.getConstructors(ref)).contains(sig);
  }

  @Test
  void registerConstructorSourceIgnoresNullArgs() {
    // given
    var ref = typeRegistry.registerUserType("МойТипC2", declaration);

    // when
    typeRegistry.registerConstructorSource(null, () -> java.util.List.of(), LanguageScope.BOTH);
    typeRegistry.registerConstructorSource(ref, null, LanguageScope.BOTH);

    // then — никаких источников не зарегистрировано.
    assertThat(typeRegistry.getConstructors(ref)).isEmpty();
  }

  @Test
  void getConstructorsScopeMismatchFiltersOutSource() {
    // given — конструктор-источник зарегистрирован с BSL-only scope.
    var ref = typeRegistry.registerUserType("МойТипK", declaration);
    var sig = com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor.EMPTY;
    typeRegistry.registerConstructorSource(ref, () -> java.util.List.of(sig), LanguageScope.BSL);

    // when / then — для BSL виден, для OS — отфильтрован.
    assertThat(typeRegistry.getConstructors(ref, FileType.BSL)).contains(sig);
    assertThat(typeRegistry.getConstructors(ref, FileType.OS)).doesNotContain(sig);
  }

  @Test
  void registerConstructorsScopeMismatchFiltersOut() {
    // given
    var ref = typeRegistry.registerUserType("МойТипR3", declaration);
    var sig = com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor.EMPTY;
    typeRegistry.registerConstructors(ref, java.util.List.of(sig), LanguageScope.BSL);

    // when / then
    assertThat(typeRegistry.getConstructors(ref, FileType.BSL)).contains(sig);
    assertThat(typeRegistry.getConstructors(ref, FileType.OS)).doesNotContain(sig);
  }

  @Test
  void isReadOnlyMemberNameFalseForUnknownNameAndNull() {
    // when / then
    assertThat(typeRegistry.isReadOnlyMemberName(
      "СовершенноНепохожиЙНаПлатформенный_МемберName_XYZ")).isFalse();
    assertThat(typeRegistry.isReadOnlyMemberName(null)).isFalse();
  }

  @Test
  void registerSpecializationWithEmptyGenericReturnsEmptyMembers() {
    // given — specialized type, generic type без членов.
    var generic = typeRegistry.registerUserType("ПустойGeneric", declaration);
    var specialized = typeRegistry.registerUserType("СпециализированныйТип", declaration);
    java.util.Map<String, String> bindings = java.util.Map.of("Имя", "МойТип");

    // when
    typeRegistry.registerSpecialization(specialized, generic, bindings, LanguageScope.BOTH);

    // then — нет членов у specialized.
    assertThat(typeRegistry.getMembers(specialized)).isEmpty();
  }

  @Test
  void isReadOnlyMemberFalseForNullArgsAndUnregisteredRef() {
    // given
    var ref = typeRegistry.registerUserType("Тип1ReadOnlyCheck", declaration);

    // when / then
    assertThat(typeRegistry.isReadOnlyMember(ref, "X")).isFalse();
    assertThat(typeRegistry.isReadOnlyMember(null, "X")).isFalse();
    assertThat(typeRegistry.isReadOnlyMember(ref, null)).isFalse();
  }

  @Test
  void getDescriptionByLanguageFallsBackToFileTypeMethod() {
    // given
    var ref = typeRegistry.registerUserType("ТипD2", declaration);

    // when — описание не регистрировали; и (lang) и (FileType) → "".
    var ruDesc = typeRegistry.getDescription(ref,
      com.github._1c_syntax.bsl.languageserver.configuration.Language.RU);

    // then
    assertThat(ruDesc).isEmpty();
  }

  @Test
  void getForEachDescriptionAndIndexAccessRespectLanguage() {
    // given
    var ref = typeRegistry.registerUserType("ТипL", declaration);

    // when / then — без регистрации для обоих языков empty.
    assertThat(typeRegistry.getForEachDescription(ref,
      com.github._1c_syntax.bsl.languageserver.configuration.Language.EN)).isEmpty();
    assertThat(typeRegistry.getIndexAccessDescription(ref,
      com.github._1c_syntax.bsl.languageserver.configuration.Language.EN)).isEmpty();
  }

  @Test
  void resolveGenericByPrefixIsCaseInsensitive() {
    // given — стандартные generic-типы платформы СправочникСсылка.<...>
    // должны находиться при различных регистрах префикса.
    // Запускается после init-bootstrap (через resolve("") trigger).
    typeRegistry.resolve("");

    // when
    var lower = typeRegistry.resolveGenericByPrefix("справочникссылка");
    var mixed = typeRegistry.resolveGenericByPrefix("СправочникСсылка");

    // then — либо оба пусты (нет данных платформы), либо равны.
    if (lower.isPresent() || mixed.isPresent()) {
      assertThat(lower).isEqualTo(mixed);
    }
  }
}
