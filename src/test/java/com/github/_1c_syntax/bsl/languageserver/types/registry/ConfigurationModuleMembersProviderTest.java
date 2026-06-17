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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ConfigurationModuleMembersProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void registersManagerModuleMembers() {
    initServerContext(PATH_TO_METADATA);

    // прогреваем lazy-конфигурацию и события документов
    context.getConfiguration();

    // СправочникСМенеджером (пустой ManagerModule.bsl/ObjectModule.bsl) — типы должны зарегистрироваться,
    // даже если набор членов из модуля пустой (источник членов заведён, но
    // добавил 0 элементов).
    var managerRu = typeRegistry.resolve("СправочникМенеджер.Справочник1");
    var managerEn = typeRegistry.resolve("CatalogManager.Справочник1");
    var objectRu = typeRegistry.resolve("СправочникОбъект.Справочник1");
    var objectEn = typeRegistry.resolve("CatalogObject.Справочник1");

    assertThat(managerRu).isPresent();
    assertThat(managerEn).isPresent();
    assertThat(managerEn.get()).isEqualTo(managerRu.get());
    assertThat(objectRu).isPresent();
    assertThat(objectEn).isPresent();
    assertThat(objectEn.get()).isEqualTo(objectRu.get());

    // members могут быть пустыми (модуль пуст), но запрос не должен падать
    assertThat(typeRegistry.getMembers(managerRu.get(), FileType.BSL)).isNotNull();
  }

  @Test
  void registersCommonModuleAsNamespace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    // прогреваем DocumentContext общего модуля, чтобы провайдер получил событие
    TestUtils.getDocumentContextFromFile("src/test/resources/metadata/designer/CommonModules/"
      + "ПервыйОбщийМодуль/Ext/Module.bsl");

    var ns = globalScopeProvider.globalMember("ПервыйОбщийМодуль", FileType.BSL)
      .map(member -> member.returnTypes().refs().stream().findFirst().orElseThrow());
    assertThat(ns).isPresent();

    var members = typeRegistry.getMembers(ns.get(), FileType.BSL);
    assertThat(members)
      .extracting(m -> m.name())
      .contains("НеУстаревшаяПроцедура", "НеУстаревшаяФункция", "УстаревшаяПроцедура", "УстаревшаяФункция");

    // не-экспортные методы общего модуля наружу не выставляются
    assertThat(members)
      .extracting(m -> m.name())
      .doesNotContain("Тест", "РегистрацияИзмененийПередУдалением");
  }

  @Test
  void registersCommonModuleAsGlobalContextMember() {
    // given: workspace c общим модулем, его DocumentContext прогрет
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    TestUtils.getDocumentContextFromFile("src/test/resources/metadata/designer/CommonModules/"
      + "ПервыйОбщийМодуль/Ext/Module.bsl");
    typeRegistry.ensureInitialized();

    // when: члены синтетического типа ГлобальныйКонтекст
    var members = typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, FileType.BSL);

    // then: общий модуль — свойство-член контекста (issue #3994)
    assertThat(members)
      .as("общий модуль должен быть свойством-членом ГлобальногоКонтекста")
      .anyMatch(member -> member.kind() == MemberKind.PROPERTY && member.matches("ПервыйОбщийМодуль"));
  }

  @Test
  void resolvesReturnTypeFromMethodDescription() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    TestUtils.getDocumentContextFromFile(
      "src/test/resources/metadata/designer/CommonModules/ОбщегоНазначения/Ext/Module.bsl");

    var ns = globalScopeProvider.globalMember("ОбщегоНазначения", FileType.BSL).orElseThrow()
      .returnTypes().refs().stream().findFirst().orElseThrow();
    var members = typeRegistry.getMembers(ns, FileType.BSL);

    var method = members.stream()
      .filter(m -> "ЗначениеВМассиве".equals(m.name()))
      .findFirst()
      .orElseThrow();

    // "Массив из Произвольный" → "Массив" → платформенный TypeRef
    assertThat(method.returnType().qualifiedName()).isEqualTo("Массив");
    assertThat(method.signatures()).isNotEmpty();
    assertThat(method.signatures().get(0).returnType().qualifiedName()).isEqualTo("Массив");
  }
}
