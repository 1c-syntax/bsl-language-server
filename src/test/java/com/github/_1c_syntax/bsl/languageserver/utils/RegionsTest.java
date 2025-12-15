/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RegionsTest {

  @Test
  void testGetStandardRegionsPatternsByModuleTypeUnknown() {
    // Given
    ModuleType unknownType = ModuleType.UNKNOWN;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(unknownType);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeFormModule() {
    // Given
    ModuleType formModule = ModuleType.FormModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(formModule);

    // Then
    assertThat(result).isNotEmpty();
    // Проверяем, что паттерны соответствуют ожидаемым областям для FormModule
    assertThat(result.stream()
      .anyMatch(p -> p.matcher(Keywords.VARIABLES_REGION.getRu()).matches() || p.matcher(Keywords.VARIABLES_REGION.getEn()).matches()))
      .isTrue(); // ОписаниеПеременных
    assertThat(result.stream()
      .anyMatch(p -> p.matcher(Keywords.FORM_EVENT_HANDLERS_REGION.getRu()).matches() || p.matcher(Keywords.FORM_EVENT_HANDLERS_REGION.getEn()).matches()))
      .isTrue(); // ОбработчикиСобытийФормы
    assertThat(result.stream()
      .anyMatch(p -> p.matcher(Keywords.INITIALIZE_REGION.getRu()).matches() || p.matcher(Keywords.INITIALIZE_REGION.getEn()).matches()))
      .isTrue(); // Инициализация
    assertThat(result.stream()
      .anyMatch(p -> p.matcher(Keywords.PRIVATE_REGION.getRu()).matches() || p.matcher(Keywords.PRIVATE_REGION.getEn()).matches()))
      .isTrue(); // СлужебныеПроцедурыИФункции
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeObjectModule() {
    // Given
    ModuleType objectModule = ModuleType.ObjectModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(objectModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeRecordSetModule() {
    // Given
    ModuleType recordSetModule = ModuleType.RecordSetModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(recordSetModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeValueManagerModule() {
    // Given
    ModuleType valueManagerModule = ModuleType.ValueManagerModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(valueManagerModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeCommonModule() {
    // Given
    ModuleType commonModule = ModuleType.CommonModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(commonModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeApplicationModule() {
    // Given
    ModuleType applicationModule = ModuleType.ApplicationModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(applicationModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeManagedApplicationModule() {
    // Given
    ModuleType managedApplicationModule = ModuleType.ManagedApplicationModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(managedApplicationModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeOrdinaryApplicationModule() {
    // Given
    ModuleType ordinaryApplicationModule = ModuleType.OrdinaryApplicationModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(ordinaryApplicationModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeCommandModule() {
    // Given
    ModuleType commandModule = ModuleType.CommandModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(commandModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeSessionModule() {
    // Given
    ModuleType sessionModule = ModuleType.SessionModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(sessionModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeHTTPServiceModule() {
    // Given
    ModuleType httpServiceModule = ModuleType.HTTPServiceModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(httpServiceModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeWEBServiceModule() {
    // Given
    ModuleType webServiceModule = ModuleType.WEBServiceModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(webServiceModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeExternalConnectionModule() {
    // Given
    ModuleType externalConnectionModule = ModuleType.ExternalConnectionModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(externalConnectionModule);

    // Then
    assertThat(result).isNotEmpty();
  }

  @Test
  void testGetStandardRegionsPatternsByModuleTypeManagerModule() {
    // Given
    ModuleType managerModule = ModuleType.ManagerModule;

    // When
    Set<Pattern> result = Regions.getStandardRegionsPatternsByModuleType(managerModule);

    // Then
    assertThat(result).isNotEmpty();
    // Verify that Initialization region is included (core fix for #3497)
    assertThat(result.stream()
      .anyMatch(p -> p.matcher(Keywords.INITIALIZE_REGION.getRu()).matches() || p.matcher(Keywords.INITIALIZE_REGION.getEn()).matches()))
      .isTrue();
  }

  @Test
  void testGetOneScriptStandardRegionsRussian() {
    // Given
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getOneScriptStandardRegions(russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.VARIABLES_REGION.getRu())
      .contains(Keywords.PUBLIC_REGION.getRu())
      .contains(Keywords.INTERNAL_REGION.getRu())
      .contains(Keywords.PRIVATE_REGION.getRu());
  }

  @Test
  void testGetOneScriptStandardRegionsEnglish() {
    // Given
    ScriptVariant english = ScriptVariant.ENGLISH;

    // When
    Set<String> result = Regions.getOneScriptStandardRegions(english);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.VARIABLES_REGION.getEn())
      .contains(Keywords.PUBLIC_REGION.getEn())
      .contains(Keywords.INTERNAL_REGION.getEn())
      .contains(Keywords.PRIVATE_REGION.getEn());
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeFormModuleRussian() {
    // Given
    ModuleType formModule = ModuleType.FormModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(formModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeFormModuleEnglish() {
    // Given
    ModuleType formModule = ModuleType.FormModule;
    ScriptVariant english = ScriptVariant.ENGLISH;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(formModule, english);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getEn()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeObjectModuleRussian() {
    // Given
    ModuleType objectModule = ModuleType.ObjectModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(objectModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeRecordSetModuleRussian() {
    // Given
    ModuleType recordSetModule = ModuleType.RecordSetModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(recordSetModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeValueManagerModuleRussian() {
    // Given
    ModuleType valueManagerModule = ModuleType.ValueManagerModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(valueManagerModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeCommonModuleRussian() {
    // Given
    ModuleType commonModule = ModuleType.CommonModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(commonModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeApplicationModuleRussian() {
    // Given
    ModuleType applicationModule = ModuleType.ApplicationModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(applicationModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeManagedApplicationModuleRussian() {
    // Given
    ModuleType managedApplicationModule = ModuleType.ManagedApplicationModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(managedApplicationModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeOrdinaryApplicationModuleRussian() {
    // Given
    ModuleType ordinaryApplicationModule = ModuleType.OrdinaryApplicationModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(ordinaryApplicationModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeCommandModuleRussian() {
    // Given
    ModuleType commandModule = ModuleType.CommandModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(commandModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeSessionModuleRussian() {
    // Given
    ModuleType sessionModule = ModuleType.SessionModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(sessionModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeHTTPServiceModuleRussian() {
    // Given
    ModuleType httpServiceModule = ModuleType.HTTPServiceModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(httpServiceModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeWEBServiceModuleRussian() {
    // Given
    ModuleType webServiceModule = ModuleType.WEBServiceModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(webServiceModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeExternalConnectionModuleRussian() {
    // Given
    ModuleType externalConnectionModule = ModuleType.ExternalConnectionModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(externalConnectionModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeManagerModuleRussian() {
    // Given
    ModuleType managerModule = ModuleType.ManagerModule;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(managerModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()) // PRIVATE_REGION всегда добавляется
      .contains(Keywords.INITIALIZE_REGION.getRu()); // Verify that Initialization region is included (core fix for #3497)
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeManagerModuleEnglish() {
    // Given
    ModuleType managerModule = ModuleType.ManagerModule;
    ScriptVariant english = ScriptVariant.ENGLISH;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(managerModule, english);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getEn())
      .contains(Keywords.INITIALIZE_REGION.getEn()); // Verify that Initialization region is included (core fix for #3497)
  }

  @Test
  void testGetStandardRegionsNamesByModuleTypeUnknownRussian() {
    // Given
    ModuleType unknownModule = ModuleType.UNKNOWN;
    ScriptVariant russian = ScriptVariant.RUSSIAN;

    // When
    Set<String> result = Regions.getStandardRegionsNamesByModuleType(unknownModule, russian);

    // Then
    assertThat(result)
      .isNotEmpty()
      .contains(Keywords.PRIVATE_REGION.getRu()); // PRIVATE_REGION всегда добавляется
  }
}
