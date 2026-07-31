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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Реквизиты иерархии есть не у каждого справочника, хотя платформа объявляет их у всего
 * семейства сразу. Проверяется на синтакс-помощнике: в JSON-фолбэке этих объявлений нет,
 * и подавлять было бы нечего.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS", matches = "true",
  disabledReason = "Требует синтакс-помощник: без него платформенных объявлений нет. "
    + "Локально: BSL_LANGUAGE_SERVER_RUN_HBK_TESTS=true ./gradlew test --tests '*HierarchyMemberSuppressionTest*'")
class HierarchyMemberSuppressionTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

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
  void flatCatalogHasNoHierarchyAttributes() {
    // Справочник1 в фикстуре неиерархический — этих реквизитов у него не существует,
    // хотя у семейства справочников платформа их объявляет.
    assertThat(propertyNames("СправочникСсылка.Справочник1"))
      .doesNotContain("Родитель", "ЭтоГруппа");
    assertThat(propertyNames("СправочникОбъект.Справочник1"))
      .doesNotContain("Родитель", "ЭтоГруппа");
  }

  @Test
  void hierarchicalCatalogKeepsThem() {
    assertThat(propertyNames("СправочникСсылка.СправочникСМенеджером"))
      .contains("Родитель", "ЭтоГруппа");
  }

  @Test
  void suppressionRemovesBothSpellings() {
    // Подавление сравнивает по дескриптору, а не по строке: английское написание
    // не должно пережить подавление русского имени.
    var ref = typeRegistry.resolve("СправочникСсылка.Справочник1").orElseThrow();
    assertThat(typeRegistry.findMember(ref, MemberKind.PROPERTY, "IsFolder", FileType.BSL))
      .as("IsFolder — то же самое, что ЭтоГруппа")
      .isEmpty();
    assertThat(typeRegistry.findMember(ref, MemberKind.PROPERTY, "Parent", FileType.BSL))
      .isEmpty();
  }

  @Test
  void otherMembersSurvive() {
    assertThat(propertyNames("СправочникСсылка.Справочник1"))
      .as("подавление снимает только объявленное, а не всё подряд")
      .contains("Код", "Наименование", "Ссылка", "ПометкаУдаления");
  }

  @Test
  void hierarchyOfItemsKeepsParentButNotIsFolder() {
    // Третий режим: иерархия есть, а групп в ней не бывает — значит нет и признака группы.
    assertThat(propertyNames("СправочникСсылка.СправочникБезГрупп"))
      .contains("Родитель")
      .doesNotContain("ЭтоГруппа");
  }

  @Test
  void suppressionSurvivesNonCanonicalReference() {
    // Источники членов находятся и по неканонической ссылке — через индекс алиасов.
    // Подавления обязаны идти тем же путём, иначе подавленный член вернулся бы боком.
    var canonical = typeRegistry.resolve("СправочникСсылка.Справочник1").orElseThrow();
    var alien = new TypeRef(TypeKind.PLATFORM, canonical.qualifiedName());
    assertThat(alien).isNotEqualTo(canonical);
    assertThat(typeRegistry.getMembers(alien, FileType.BSL))
      .as("по неканонической ссылке члены есть — значит и подавление должно действовать")
      .isNotEmpty()
      .noneMatch(member -> member.matches("Родитель") || member.matches("ЭтоГруппа"));
  }

  private List<String> propertyNames(String typeName) {
    var ref = typeRegistry.resolve(typeName).orElseThrow();
    return typeRegistry.getMembers(ref, FileType.BSL).stream()
      .filter(member -> member.kind() == MemberKind.PROPERTY)
      .map(MemberDescriptor::name)
      .toList();
  }
}
