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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Стандартный реквизит {@code Владелец} подчинённого справочника. В синтакс-помощнике он
 * объявлен типом {@code Неопределено}; конкретный тип известен только из метаданных —
 * из списка владельцев.
 */
@CleanupContextBeforeClassAndAfterClass
class CatalogOwnerTypeTest extends AbstractServerContextAwareTest {

  /**
   * В общей фикстуре справочники никому не подчинены, поэтому нужна эта: справочник,
   * подчинённый другому справочнику той же конфигурации.
   * <p>
   * Порядок обработки этих двух справочников зафиксировать нельзя — конфигурация
   * отдаёт детей неизменяемой хеш-картой, обход которой JDK рандомизирует от запуска
   * к запуску. Поэтому тест проходит независимо от порядка только потому, что типы
   * владельцев резолвятся лениво (см. {@code registerOwnerMembers}); при резолве на
   * регистрации он был бы плавающим.
   */
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/catalogOwners";

  private static final String OWNER = "Владелец";

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
  void ownerOfSubordinateCatalogIsTypedByItsOwners() {
    assertThat(memberTypes("СправочникСсылка.Справочник1", OWNER))
      .containsExactly("СправочникСсылка.Справочник2");
  }

  @Test
  void ownerIsRefinedInEveryTypeOfTheFamilyThatDeclaresIt() {
    // Члены перебираются у дженерика, поэтому реквизит уточняется всюду, где объявлен, —
    // без перечисления типов семейства в коде.
    assertThat(memberTypes("СправочникОбъект.Справочник1", OWNER))
      .containsExactly("СправочникСсылка.Справочник2");
  }

  @Test
  void ownerOfIndependentCatalogStaysUndefined() {
    // Для неподчинённого справочника в синтакс-помощнике так и написано:
    // «Неопределено — для неподчиненного справочника». Тип не теряется — его и нет.
    assertThat(memberTypes("СправочникСсылка.Справочник2", OWNER))
      .containsExactly("Неопределено");
    assertThat(memberTypes("СправочникОбъект.Справочник2", OWNER))
      .containsExactly("Неопределено");
  }

  @Test
  void standardAttributeWithoutTypeInMetadataKeepsThePlatformDeclaration() {
    // Реквизиты из mdclasses регистрируются поверх платформенных, поэтому бестиповый
    // стандартный реквизит перекрывал бы объявление платформы вместе с его типом.
    // Проверяется на дженерике: у него источник из конфигурации заведомо один — платформа.
    assertThat(memberTypes("СправочникСсылка.<Имя справочника>", OWNER))
      .containsExactly("Неопределено");
  }

  private List<String> memberTypes(String typeName, String memberName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    var member = typeRegistry.getMembers(ref, FileType.BSL).stream()
      .filter(m -> m.kind() == MemberKind.PROPERTY && m.matches(memberName))
      .findFirst();
    assertThat(member).as("реквизит %s у %s", memberName, typeName).isPresent();
    return member.orElseThrow().returnTypes().refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }
}
