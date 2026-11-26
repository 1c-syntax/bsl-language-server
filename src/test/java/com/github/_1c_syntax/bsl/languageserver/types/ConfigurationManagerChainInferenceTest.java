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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка цепочки {@code Справочники.<MD>.МетодМенеджера()} —
 * член коллекции должен указывать на менеджер-обёртку
 * (например, {@code СправочникМенеджер.СправочникСМенеджером}), чтобы методы
 * соответствующего {@code ManagerModule.bsl} подтягивались как члены типа.
 */
@CleanupContextBeforeClassAndAfterClass
class ConfigurationManagerChainInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry typeRegistry;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void chainResolvesManagerModuleMethodReturnType() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    // прогреваем ManagerModule, чтобы провайдер увидел DocumentContextContentChangedEvent
    TestUtils.getDocumentContextFromFile(
      "./src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ManagerModule.bsl");

    // sanity: коллекция Справочники — глобальное свойство, её тип-значение
    // (менеджер-обёртка) несёт метод МетодМенеджера.
    var collection = globalScopeProvider.globalMember("Справочники", FileType.BSL).orElseThrow()
      .returnTypes().refs().stream().findFirst().orElseThrow();
    var catalogsMembers = typeRegistry.getMembers(collection, FileType.BSL);
    assertThat(catalogsMembers).extracting(m -> m.name()).contains("СправочникСМенеджером");
    var memberReturn = catalogsMembers.stream()
      .filter(m -> "СправочникСМенеджером".equals(m.name()))
      .findFirst().orElseThrow().returnType();
    assertThat(memberReturn.qualifiedName()).isEqualTo("СправочникМенеджер.СправочникСМенеджером");
    assertThat(typeRegistry.getMembers(memberReturn, FileType.BSL))
      .extracting(m -> m.name())
      .as("methods of manager wrapper should include exported МетодМенеджера")
      .contains("МетодМенеджера");

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ConfigurationManagerChain.bsl");

    var content = documentContext.getContent();

    assertChainReturnType(documentContext, content, "Результат");
    assertChainReturnType(documentContext, content, "РезультатEn");
  }

  private void assertChainReturnType(
    com.github._1c_syntax.bsl.languageserver.context.DocumentContext documentContext,
    String content,
    String assignedVar
  ) {
    // Кладём курсор на имя метода в правой части присваивания, как в
    // CommonModuleCallInferenceTest — expressionTypesAt возвращает тип
    // объемлющего выражения (вызова), а не самой переменной слева.
    var marker = assignedVar + " = ";
    var rhsStart = content.indexOf(marker) + marker.length();
    var methodName = "МетодМенеджера";
    var methodIdx = content.indexOf(methodName, rhsStart);
    int lineStart = content.lastIndexOf('\n', methodIdx) + 1;
    int line = content.substring(0, methodIdx).split("\n").length - 1;
    int pos = methodIdx - lineStart + 1;

    var types = typeService.expressionTypesAt(documentContext, new Position(line, pos));
    assertThat(types.refs())
      .as("chain return type for %s", assignedVar)
      .hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualTo("Массив");
  }
}
