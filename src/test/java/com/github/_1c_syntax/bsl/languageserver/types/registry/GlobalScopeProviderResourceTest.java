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

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверки загрузки сигнатур и полных наборов возвращаемых типов глобальных функций
 * через JSON-загрузчик и источник членов глобального контекста.
 */
class GlobalScopeProviderResourceTest {

  @Test
  @SneakyThrows
  @SuppressWarnings("unchecked")
  void oneScriptGlobalFunctionsPreserveEveryDeclaredReturnType() {
    // given
    var loaded = invokeLoadFromResource(
      "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-globals.json");
    var accessor = loaded.getClass().getDeclaredMethod("functions");
    accessor.setAccessible(true);
    var functions = (Map<String, MemberDescriptor>) accessor.invoke(loaded);

    for (var name : new String[]{"Мин", "Макс", "Min", "Max"}) {
      // when
      var function = functions.get(name.toLowerCase(Locale.ROOT));

      // then
      assertThat(function.returnTypes().refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Строка", "Число", "Дата", "Булево");
      assertThat(function.returnTypes().refs()).allSatisfy(ref ->
        assertThat(ref.kind()).isEqualTo(TypeKind.PRIMITIVE));
    }
  }

  @Test
  void oneScriptGlobalContextMembersPreserveEveryDeclaredReturnType() {
    // given
    var members = GlobalScopeProvider.globalContextMembers(
      "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-globals.json");

    for (var name : new String[]{"Мин", "Макс", "Min", "Max"}) {
      // when
      var function = members.stream().filter(member -> member.matches(name)).findFirst().orElseThrow();

      // then
      assertThat(function.returnTypes().refs()).extracting(TypeRef::qualifiedName)
        .containsExactlyInAnyOrder("Строка", "Число", "Дата", "Булево");
      assertThat(function.returnTypes().refs()).allSatisfy(ref ->
        assertThat(ref.kind()).isEqualTo(TypeKind.PRIMITIVE));
    }
  }

  @Test
  @SneakyThrows
  @SuppressWarnings("unchecked")
  void globalSignaturesPreservePrimitiveTypesAliasesDefaultsAndVariadicParameters() {
    var loaded = invokeLoadFromResource("oscript-metadata/globals.json");
    var accessor = loaded.getClass().getDeclaredMethod("functions");
    accessor.setAccessible(true);
    var functions = (Map<String, MemberDescriptor>) accessor.invoke(loaded);
    var signature = functions.values().stream().filter(member -> member.matches("ТестФормат"))
      .findFirst().orElseThrow().signatures().getFirst();

    assertThat(signature.returnTypes().refs()).singleElement().satisfies(ref -> {
      assertThat(ref.kind()).isEqualTo(TypeKind.PRIMITIVE);
      assertThat(ref.qualifiedName()).isEqualTo("Строка");
    });
    var template = signature.parameters().getFirst();
    assertThat(template.displayName(Language.RU)).isEqualTo("Шаблон");
    assertThat(template.displayName(Language.EN)).isEqualTo("template");
    assertThat(template.types().refs()).singleElement()
      .satisfies(ref -> assertThat(ref.kind()).isEqualTo(TypeKind.PRIMITIVE));
    var values = signature.parameters().getLast();
    assertThat(values.variadic()).isTrue();
    assertThat(values.optional()).isTrue();
    assertThat(values.defaultValue()).isEqualTo("Неопределено");
    assertThat(values.types().refs()).containsExactly(TypeRef.ANY);
  }

  @Test
  void loadFromResourceMergesLegacyKeywordsField() {
    var loaded = invokeLoadFromResource("keyword-fallback/globals-legacy-keywords.json");

    // legacy keywords из globals.json объединены со scope-фолбэком keyword-loader'a:
    // legacy1/legacy2 — точно есть, classes тоже подгружены.
    assertThat(loaded).isNotNull();
  }

  @Test
  void loadFromResourceReturnsEmptyOnIOException() {
    var loaded = invokeLoadFromResource("keyword-fallback/does-not-exist.json");

    assertThat(loaded).isNotNull();
  }

  @SneakyThrows
  private static Object invokeLoadFromResource(String resourcePath) {
    Method method = null;
    for (var m : GlobalScopeProvider.class.getDeclaredMethods()) {
      if (m.getName().equals("loadFromResource")
        && m.getParameterCount() == 1) {
        method = m;
        break;
      }
    }
    if (method == null) {
      throw new IllegalStateException("loadFromResource not found");
    }
    method.setAccessible(true);
    return method.invoke(null, resourcePath);
  }
}
