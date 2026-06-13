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
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты на разрешение глобальных свойств (system enums) — {@code КодировкаТекста.UTF8}.
 */
@SpringBootTest
class GlobalEnumPropertyInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;
  @Autowired
  private GlobalScopeProvider globalScopeProvider;
  @Autowired
  private TypeRegistry typeRegistry;

  @BeforeEach
  void setUpWorkspaceContext() {
    initServerContext();
  }

  @Test
  void findGlobalContextByName() {
    typeRegistry.resolve("");
    var encoding = globalScopeProvider.findGlobalContext("КодировкаТекста", FileType.BSL);
    assertThat(encoding).isPresent();
    assertThat(encoding.get().qualifiedName()).isEqualTo("КодировкаТекста");
  }

  @Test
  void englishAliasIsRegistered() {
    typeRegistry.resolve("");
    var encoding = globalScopeProvider.findGlobalContext("TextEncoding", FileType.BSL);
    assertThat(encoding).isPresent();
  }

  @Test
  void globalPropertyNamesIncludeBuiltinEnums() {
    typeRegistry.resolve("");
    assertThat(globalScopeProvider.getGlobalContextNames(FileType.BSL))
      .contains("КодировкаТекста", "НаправлениеСортировки", "ВидСравнения");
  }

  @Test
  void enumMembersResolveBackToEnum() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/EnumAccess.bsl");
    // курсор на UTF8 (строка 0, ~21-я позиция)
    var content = documentContext.getContent();
    var line = 0;
    var pos = content.indexOf("UTF8") + 1;
    var types = typeService.expressionTypesAt(documentContext, new Position(line, pos));
    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName())
      .isEqualTo("КодировкаТекста");
  }
}
