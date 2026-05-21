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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты на synthetic-символы {@link ConstructorCallSymbol} и
 * {@link PlatformMemberSymbol} — getters, SymbolKind, accept (no-op),
 * equals/hashCode.
 */
class PlatformSymbolsTest {

  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Test
  void constructorCallSymbolExposesAllFields() {
    // given
    var sig = new SignatureDescriptor(List.of(), TypeRef.UNKNOWN, "");
    var ctor = new ConstructorCallSymbol("Массив", ARRAY, 2, List.of(sig), "Динамический массив");

    // when / then
    assertThat(ctor.getName()).isEqualTo("Массив");
    assertThat(ctor.getTypeName()).isEqualTo("Массив");
    assertThat(ctor.getTypeRef()).isEqualTo(ARRAY);
    assertThat(ctor.getArgCount()).isEqualTo(2);
    assertThat(ctor.getConstructors()).containsExactly(sig);
    assertThat(ctor.getClassDescription()).isEqualTo("Динамический массив");
    assertThat(ctor.getSymbolKind()).isEqualTo(SymbolKind.Constructor);
  }

  @Test
  void constructorCallSymbolAcceptIsNoOp() {
    // given
    var ctor = new ConstructorCallSymbol("Массив", ARRAY, 0, List.of(), "");
    var visitor = Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor.class);

    // when
    ctor.accept(visitor);

    // then — synthetic не участвует в обходе
    Mockito.verifyNoInteractions(visitor);
  }

  @Test
  void constructorCallSymbolEqualsBasedOnTypeNameAndArgCount() {
    // given
    var a = new ConstructorCallSymbol("Массив", ARRAY, 1, List.of(), "");
    var sameKey = new ConstructorCallSymbol("Массив", ARRAY, 1, List.of(), "иное описание");
    var differentArity = new ConstructorCallSymbol("Массив", ARRAY, 2, List.of(), "");
    var differentType = new ConstructorCallSymbol("Структура", ARRAY, 1, List.of(), "");

    // when / then
    assertThat(a).isEqualTo(sameKey).hasSameHashCodeAs(sameKey);
    assertThat(a).isNotEqualTo(differentArity);
    assertThat(a).isNotEqualTo(differentType);
  }

  @Test
  void platformMemberSymbolExposesAllFields() {
    // given
    var descriptor = MemberDescriptor.method("Добавить");
    var sym = new PlatformMemberSymbol("Добавить", ARRAY, descriptor, 1,
      List.of(com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.of(NUMBER)));

    // when / then
    assertThat(sym.getName()).isEqualTo("Добавить");
    assertThat(sym.getOwner()).isEqualTo(ARRAY);
    assertThat(sym.getDescriptor()).isEqualTo(descriptor);
    assertThat(sym.getCallArgCount()).isEqualTo(1);
    assertThat(sym.getArgTypes()).hasSize(1);
    assertThat(sym.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }

  @Test
  void platformMemberSymbolKindIsPropertyForNonMethod() {
    // given
    var descriptor = MemberDescriptor.property("ВерхняяГраница", NUMBER);
    var sym = new PlatformMemberSymbol("ВерхняяГраница", ARRAY, descriptor, -1, List.of());

    // when / then
    assertThat(sym.getSymbolKind()).isEqualTo(SymbolKind.Property);
  }

  @Test
  void platformMemberSymbolOwnerNullableForGlobals() {
    // given
    var descriptor = MemberDescriptor.method("Сообщить");
    var sym = new PlatformMemberSymbol("Сообщить", null, descriptor, 1, List.of());

    // when / then
    assertThat(sym.getOwner()).isNull();
  }

  @Test
  void platformMemberSymbolEqualsBasedOnNameAndOwner() {
    // given
    var d1 = MemberDescriptor.method("F");
    var d2 = MemberDescriptor.method("F");
    var a = new PlatformMemberSymbol("F", ARRAY, d1, 0, List.of());
    var sameKey = new PlatformMemberSymbol("F", ARRAY, d2, 5, List.of());
    var differentName = new PlatformMemberSymbol("G", ARRAY, d1, 0, List.of());
    var differentOwner = new PlatformMemberSymbol("F", NUMBER, d1, 0, List.of());

    // when / then
    assertThat(a).isEqualTo(sameKey).hasSameHashCodeAs(sameKey);
    assertThat(a).isNotEqualTo(differentName);
    assertThat(a).isNotEqualTo(differentOwner);
  }

  @Test
  void platformMemberSymbolAcceptIsNoOp() {
    // given
    var sym = new PlatformMemberSymbol("F", null,
      MemberDescriptor.method("F"), -1, List.of());
    var visitor = Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor.class);

    // when
    sym.accept(visitor);

    // then
    Mockito.verifyNoInteractions(visitor);
  }
}
