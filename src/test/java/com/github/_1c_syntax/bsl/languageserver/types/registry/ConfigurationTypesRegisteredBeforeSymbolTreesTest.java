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
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.util.concurrent.atomic.AtomicReference;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фиксирует ключевой инвариант порядка загрузки: конфигурационные типы зарегистрированы
 * ДО того, как будет построено первое дерево символов.
 * <p>
 * Всё, что при построении дерева опирается на типы (классификация обработчиков событий
 * и т.п.), считается один раз и кэшируется в дереве, поэтому запоздавшая регистрация уже
 * не исправила бы результат — он оставался бы неверным до первой правки файла. Раньше типы
 * регистрировались по завершении {@code populateContext}, то есть заведомо позже всех
 * деревьев; теперь — на {@code WorkspaceAddedEvent}, к моменту которого {@code addWorkspace}
 * уже выставил {@code configurationRoot} из workspace-конфигурации.
 */
@CleanupContextBeforeClassAndAfterClass
@Import(ConfigurationTypesRegisteredBeforeSymbolTreesTest.FirstSymbolTreeProbe.class)
class ConfigurationTypesRegisteredBeforeSymbolTreesTest extends AbstractServerContextAwareTest {

  /** Тип, который заводит {@code ConfigurationTypesProvider} по справочнику тестовой конфигурации. */
  private static final String CONFIGURATION_TYPE = "СправочникМенеджер.Справочник1";

  @Autowired
  private FirstSymbolTreeProbe probe;

  @Test
  void configurationTypesAreRegisteredBeforeFirstSymbolTreeIsBuilt() {
    probe.reset();

    initServerContext(PATH_TO_METADATA);

    assertThat(probe.typesResolvedAtFirstTree())
      .as("к моменту построения первого дерева символов типы конфигурации уже зарегистрированы")
      .isTrue();
  }

  /**
   * Запоминает, были ли конфигурационные типы зарегистрированы в момент построения
   * САМОГО ПЕРВОГО дерева символов: {@code DocumentContextContentChangedEvent} публикуется
   * по завершении {@code DocumentContext.rebuild()}, внутри которого дерево и считается.
   */
  @TestComponent
  static class FirstSymbolTreeProbe {

    private final TypeRegistry typeRegistry;
    private final AtomicReference<@Nullable Boolean> resolvedAtFirstTree = new AtomicReference<>();

    FirstSymbolTreeProbe(TypeRegistry typeRegistry) {
      this.typeRegistry = typeRegistry;
    }

    @EventListener
    public void handleEvent(DocumentContextContentChangedEvent event) {
      resolvedAtFirstTree.compareAndSet(null, typeRegistry.resolve(CONFIGURATION_TYPE).isPresent());
    }

    void reset() {
      resolvedAtFirstTree.set(null);
    }

    @Nullable
    Boolean typesResolvedAtFirstTree() {
      return resolvedAtFirstTree.get();
    }
  }
}
