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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы конфигурации регистрируются сразу по добавлению workspace'а (см. {@link
 * ConfigurationTypesProvider#handleEvent}) — раньше, чем {@code ServerContext.populateContext()}
 * успевает построить хоть одно дерево символов {@code .bsl}-файла. Это гарантирует, что классификация
 * методов-обработчиков событий (см. {@code context.symbol.EventHandlerClassifier}) верна уже при
 * первом построении дерева, без необходимости реактивно пересчитывать его впоследствии.
 * <p>
 * {@code populate=false} в этом тесте намеренно: он доказывает, что регистрация НЕ зависит от
 * заполнения контекста документами — только от {@code configurationRoot}, который {@code
 * ServerContextProvider#addWorkspace} устанавливает ДО публикации {@code WorkspaceAddedEvent}.
 */
class ConfigurationTypesProviderAutoRegistrationTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void typesAreRegisteredWithoutPopulatingAnyDocument() {
    initServerContext(Path.of(PATH_TO_METADATA), false);

    assertThat(context.getDocuments()).isEmpty();
    assertThat(typeRegistry.resolve("СправочникМенеджер.Справочник1")).isPresent();
  }
}
