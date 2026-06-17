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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия issue #3994 (PR #4135): платформенный глобальный контекст (bsl-context)
 * содержит свойства-менеджеры коллекций ({@code Справочники → СправочникиМенеджер},
 * {@code Перечисления → ПеречисленияМенеджер}). Они попадают в
 * {@link TypeRegistry#GLOBAL_CONTEXT} на bootstrap'е — РАНЬШЕ, чем
 * {@link ConfigurationTypesProvider} зарегистрирует одноимённые
 * коллекции-namespace конфигурации (с членами-MD-объектами).
 * <p>
 * Член {@code GLOBAL_CONTEXT} резолвится через {@code computeMembers}
 * (putIfAbsent по имени — первый источник побеждает), поэтому
 * конфигурационная коллекция должна регистрироваться как <b>override</b>
 * (в начало списка источников), иначе платформенное свойство затеняет её и
 * {@code Справочники.<Объект>} не резолвится (completion членов менеджера пуст).
 */
class GlobalContextCollectionShadowingTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void configurationCollectionWinsOverPlatformGlobalProperty() {
    // given: workspace настроен, но ещё НЕ populated (событие не выпущено)
    initServerContext(Absolute.path(PATH_TO_METADATA), false);
    typeRegistry.ensureInitialized();

    // and: платформенное свойство ГлобальногоКонтекста `Справочники` → пустой
    // менеджер-тип, зарегистрированное РАНЬШЕ конфигурации (имитация bsl-context)
    var platformManager = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджерПлатформенный");
    typeRegistry.registerMemberSource(TypeRegistry.GLOBAL_CONTEXT,
      () -> List.of(MemberDescriptor.property("Справочники", platformManager)), FileType.BSL);

    // when: конфигурация populated → ConfigurationTypesProvider регистрирует
    // коллекцию-namespace `Справочники` с членами-MD (Справочник1, …)
    context.populateContext();

    // then: безпрефиксное `Справочники` резолвится в конфигурационную коллекцию,
    // а не в затеняющий платформенный менеджер — и её члены содержат каталог
    var spr = typeRegistry.globalMember("Справочники", FileType.BSL);
    assertThat(spr).as("Справочники резолвится как член GLOBAL_CONTEXT").isPresent();

    var returnType = spr.orElseThrow().returnTypes().refs().stream().findFirst().orElseThrow();
    var memberNames = typeRegistry.getMembers(returnType, FileType.BSL).stream()
      .map(MemberDescriptor::name)
      .toList();
    assertThat(memberNames)
      .as("Справочники.<Объект> должны быть видны (config-коллекция выигрывает у платформенного свойства)")
      .contains("Справочник1");
  }
}
