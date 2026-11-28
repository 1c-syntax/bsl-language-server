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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Наполнитель индекса — синглтон на все рабочие области, а данные индекса у каждой свои.
 * Очистка одной рабочей области не должна снимать отметки о наполнении документов другой:
 * по ним другая область решает, переиндексировать ли документ после регистрации типов.
 */
class ReferenceIndexFillerWorkspacesTest {

  private static final URI A1 = URI.create("file:///a/Первый.bsl");
  private static final URI A2 = URI.create("file:///a/Второй.bsl");
  private static final URI B1 = URI.create("file:///b/Модуль.bsl");

  @Test
  void clearingOneWorkspaceKeepsFilledMarksOfAnother() {
    // given — документы обеих областей наполнены
    var index = mock(ReferenceIndex.class);
    var filler = new ReferenceIndexFiller(index, mock(LanguageServerConfiguration.class),
      mock(WorkspaceNames.class), mock(SelfMemberResolver.class));
    filled(filler).addAll(Set.of(A1, A2, B1));

    var workspaceA = mock(ServerContext.class);
    when(workspaceA.isClearing()).thenReturn(true);
    // первый документ области уже снят с карты, второй ещё в ней
    when(workspaceA.getDocuments()).thenReturn(Map.of(A2, mock(DocumentContext.class)));

    // when — область A очищается целиком: удаление каждого её документа
    filler.handleEvent(new ServerContextDocumentRemovedEvent(workspaceA, A1));
    when(workspaceA.getDocuments()).thenReturn(Map.of());
    filler.handleEvent(new ServerContextDocumentRemovedEvent(workspaceA, A2));

    // then — отметки области B на месте, индекс A очищен один раз
    assertThat(filled(filler)).containsExactly(B1);
    verify(index, times(1)).clear();
  }

  @SuppressWarnings("unchecked")
  private static Set<URI> filled(ReferenceIndexFiller filler) {
    return (Set<URI>) ReflectionTestUtils.getField(filler, "filled");
  }
}
