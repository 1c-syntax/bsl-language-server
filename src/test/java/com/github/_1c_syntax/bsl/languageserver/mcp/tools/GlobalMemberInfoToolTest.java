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
package com.github._1c_syntax.bsl.languageserver.mcp.tools;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceResolver;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalMemberInfoToolTest {

  private static final URI WORKSPACE = URI.create("file:///global-member-info-test/");

  @Test
  void globalPropertyCardRespectsLanguageAndKeepsMetadataAndAllValueTypes() {
    // given — свойство с переводом описания, метаданными синтакс-помощника и двумя типами значения
    var metadata = new PlatformMetadata("8.3.10", "", List.of(), Set.of(Availability.THIN_CLIENT), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    var property = MemberDescriptor.property("ПараметрыСеанса", TypeSet.of(
        new TypeRef(TypeKind.PLATFORM, "Строка"), new TypeRef(TypeKind.PLATFORM, "Число")), "")
      .withBilingualDescription(BilingualString.of("Описание", "Description"))
      .withMetadata(metadata);
    var globalScope = mock(GlobalScopeProvider.class);
    when(globalScope.globalMember("ПараметрыСеанса", FileType.BSL)).thenReturn(Optional.of(property));
    var resolver = mock(McpWorkspaceResolver.class);
    when(resolver.resolveWorkspaceFolderUri(any())).thenReturn(WORKSPACE);
    var tool = new GlobalMemberInfoTool(resolver, mock(TypeRegistry.class), globalScope);

    // when
    WorkspaceContextHolder.registerWorkspace(WORKSPACE, "t");
    GlobalMemberInfoTool.Result result;
    try {
      result = tool.globalMemberInfo("ПараметрыСеанса", FileType.BSL, WORKSPACE.toString(), Language.EN);
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(WORKSPACE);
    }

    // then — та же карточка члена, что у остальных инструментов
    assertThat(result.member()).isEqualTo(TypeMemberDto.from(property, Language.EN));
  }
}
