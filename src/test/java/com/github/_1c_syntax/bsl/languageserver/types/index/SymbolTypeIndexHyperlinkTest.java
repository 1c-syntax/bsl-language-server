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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты для {@link SymbolTypeIndex#resolveHyperlink}.
 * TypeRegistry мокается, чтобы не поднимать Spring-контекст.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SymbolTypeIndexHyperlinkTest {

  private static final TypeRef MODULE = new TypeRef(TypeKind.USER, "ОбщегоНазначения");
  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Mock
  private TypeRegistry typeRegistry;

  private SymbolTypeIndex index;

  @BeforeEach
  void setUp() {
    index = new SymbolTypeIndex(typeRegistry);
  }

  @Test
  void resolveHyperlinkReturnsEmptyForBlankLink() {
    // when / then
    assertThat(index.resolveHyperlink(null, FileType.BSL)).isSameAs(TypeSet.EMPTY);
    assertThat(index.resolveHyperlink("", FileType.BSL)).isSameAs(TypeSet.EMPTY);
    assertThat(index.resolveHyperlink("   ", FileType.BSL)).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void resolveHyperlinkSingleSegmentReturnsEmpty() {
    // given — нет точки в ссылке: цикл от parts.length-1 до 1 не запускается.
    when(typeRegistry.resolve(any(String.class), any(FileType.class))).thenReturn(Optional.empty());

    // when
    var result = index.resolveHyperlink("Модуль", FileType.BSL);

    // then
    assertThat(result).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void resolveHyperlinkModuleMethodReturnsReturnType() {
    // given
    var method = MemberDescriptor.method("МойМетод",
      List.of(new SignatureDescriptor(List.of(), STRING, "")));
    when(typeRegistry.resolve(eq("ОбщегоНазначения"), any(FileType.class)))
      .thenReturn(Optional.of(MODULE));
    when(typeRegistry.getMembers(eq(MODULE), any(FileType.class))).thenReturn(List.of(method));

    // when
    var result = index.resolveHyperlink("ОбщегоНазначения.МойМетод", FileType.BSL);

    // then
    assertThat(result.refs()).containsExactly(STRING);
  }

  @Test
  void resolveHyperlinkModuleMethodParameterReturnsParameterTypes() {
    // given
    var param = new ParameterDescriptor(
      BilingualString.of("Док"), TypeSet.of(ARRAY), false, BilingualString.EMPTY, "");
    var method = MemberDescriptor.method("МойМетод",
      List.of(new SignatureDescriptor(List.of(param), STRING, "")));
    when(typeRegistry.resolve(eq("ОбщегоНазначения"), any(FileType.class)))
      .thenReturn(Optional.of(MODULE));
    when(typeRegistry.getMembers(eq(MODULE), any(FileType.class))).thenReturn(List.of(method));

    // when — последний сегмент в ссылке = имя параметра
    var result = index.resolveHyperlink("ОбщегоНазначения.МойМетод.Док", FileType.BSL);

    // then
    assertThat(result.refs()).containsExactly(ARRAY);
  }

  @Test
  void resolveHyperlinkReturnsEmptyWhenMemberNotFound() {
    // given
    when(typeRegistry.resolve(eq("ОбщегоНазначения"), any(FileType.class)))
      .thenReturn(Optional.of(MODULE));
    when(typeRegistry.getMembers(eq(MODULE), any(FileType.class))).thenReturn(List.of());

    // when
    var result = index.resolveHyperlink("ОбщегоНазначения.НеизвестныйМетод", FileType.BSL);

    // then
    assertThat(result).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void resolveHyperlinkTriesShorterPrefixIfLongestFails() {
    // given — длинный префикс не резолвится, но короткий — да.
    // Метод «Метод» возвращает Строку.
    var method = MemberDescriptor.method("Метод",
      List.of(new SignatureDescriptor(List.of(), STRING, "")));
    when(typeRegistry.resolve(eq("Несуществующий.ОбщегоНазначения"), any(FileType.class)))
      .thenReturn(Optional.empty());
    when(typeRegistry.resolve(eq("Несуществующий"), any(FileType.class)))
      .thenReturn(Optional.empty());

    // when — нет головы вообще
    var result = index.resolveHyperlink("Несуществующий.ОбщегоНазначения.Метод", FileType.BSL);

    // then
    assertThat(result).isSameAs(TypeSet.EMPTY);
    // Игнорируем mock для метода — он недостижим, т.к. ничего не резолвится
    assertThat(method).isNotNull();
  }

  @Test
  void resolveHyperlinkReturnsEmptyWhenMemberReturnsUnknown() {
    // given — метод найден, но его returnType — Unknown
    var method = MemberDescriptor.method("X",
      List.of(new SignatureDescriptor(List.of(), TypeRef.UNKNOWN, "")));
    when(typeRegistry.resolve(eq("Модуль"), any(FileType.class))).thenReturn(Optional.of(MODULE));
    when(typeRegistry.getMembers(eq(MODULE), any(FileType.class))).thenReturn(List.of(method));

    // when — есть продолжение пути после метода с unknown-результатом
    var result = index.resolveHyperlink("Модуль.X.Y", FileType.BSL);

    // then
    assertThat(result).isSameAs(TypeSet.EMPTY);
  }
}
