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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeAtPositionToolTest {

  @Test
  void membersAreTheOnesShownForTheRenderedLanguage() {
    // given — у типа есть член, применимый только к английскому написанию (устаревший псевдоним)
    var type = new TypeRef(TypeKind.PLATFORM, "Массив");
    var common = MemberDescriptor.property("Количество", new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var englishOnly = MemberDescriptor.property("OldCount", new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var document = mock(DocumentContext.class);
    when(document.getFileType()).thenReturn(FileType.BSL);
    var typeService = mock(TypeService.class);
    when(typeService.expressionTypesAt(eq(document), any(Position.class))).thenReturn(TypeSet.of(type));
    when(typeService.getMembers(type, FileType.BSL)).thenReturn(List.of(common, englishOnly));
    when(typeService.getMembers(type, FileType.BSL, Language.RU)).thenReturn(List.of(common));
    var reader = mock(McpDocumentReader.class);
    when(reader.analyze(eq("file.bsl"), any())).thenAnswer(invocation ->
      invocation.<Function<DocumentContext, ?>>getArgument(1).apply(document));
    var tool = new TypeAtPositionTool(reader, typeService);

    // when
    var result = tool.typeAtPosition("file.bsl", 0, 0);

    // then — как у type_info: члены, применимые к языку, на котором карточка показана
    assertThat(result.members()).extracting(TypeMemberDto::name).containsExactly("Количество");
  }
}
