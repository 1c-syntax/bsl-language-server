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

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link BuiltinPlatformTypesProvider}. JSON-фолбэк должен
 * отдавать типы только когда bsl-context недоступен.
 */
@ExtendWith(MockitoExtension.class)
class BuiltinPlatformTypesProviderTest {

  @Mock
  private BslContextHolder holder;

  @Mock
  private ContextProvider contextProvider;

  @Test
  void getTypesReturnsBuiltinsWhenBslContextUnavailable() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var types = provider.getTypes();

    // then — JSON-fallback всегда содержит хотя бы примитивы и базовые платформенные типы
    assertThat(types).isNotEmpty();
  }

  @Test
  void getTypesReturnsEmptyWhenBslContextAvailable() {
    // given — bsl-context доступен → JSON-fallback не нужен
    when(holder.get()).thenReturn(Optional.of(contextProvider));
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var types = provider.getTypes();

    // then
    assertThat(types).isEmpty();
  }

  @Test
  void languageScopeIsBsl() {
    // given
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when / then
    assertThat(provider.getLanguageScope()).isEqualTo(LanguageScope.BSL);
  }

  @Test
  void typesIncludePrimitiveStringAndNumber() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var typeNames = provider.getTypes().stream()
      .map(td -> td.name().primary())
      .toList();

    // then — встроенный JSON-pack обязан включать основные примитивы.
    assertThat(typeNames).contains("Строка", "Число", "Булево");
  }

  @Test
  void typesIncludeBasicCollectionTypes() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var typeNames = provider.getTypes().stream()
      .map(td -> td.name().primary())
      .toList();

    // then
    assertThat(typeNames).contains("Массив", "Структура", "Соответствие");
  }

  @Test
  void platformTypeMassivHasPlatformMembers() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var arrayDecl = provider.getTypes().stream()
      .filter(td -> "Массив".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(arrayDecl.members())
      .extracting(m -> m.name())
      .containsAnyOf("Добавить", "Количество", "ВерхняяГраница", "Очистить");
  }

  @Test
  void oscriptBilingualMemberPairsAreMergedIntoSingleBilingualMember() {
    // В дампе OneScript Массив.Добавить и Массив.Add — два отдельных
    // моноязычных члена подряд. После склейки при загрузке это один
    // двуязычный член, как в BSL-модели.
    var types = BuiltinPlatformTypesProvider.loadFromResource(
      "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-platform-types.json");

    var arrayDecl = types.stream()
      .filter(td -> "Массив".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    var addMembers = arrayDecl.members().stream()
      .filter(m -> m.matches("Добавить") || m.matches("Add"))
      .toList();

    assertThat(addMembers)
      .as("Добавить/Add должны схлопнуться в один двуязычный член")
      .hasSize(1);
    var add = addMembers.get(0);
    assertThat(add.matches("Добавить")).as("находится по русскому имени").isTrue();
    assertThat(add.matches("Add")).as("находится по английскому имени").isTrue();
    assertThat(add.displayName(Language.RU)).isEqualTo("Добавить");
    assertThat(add.displayName(Language.EN)).isEqualTo("Add");
  }

  @Test
  void mixedScriptMemberPairsAreMergedIntoSingleBilingualMember() {
    // Русское имя метода может содержать латинскую аббревиатуру (ЗаписатьJSON,
    // ПрочитатьXML). Оно всё равно должно склеиваться со своей англоязычной
    // парой (WriteJSON, ReadXML): наличие кириллицы однозначно относит имя к
    // русскому, встроенная латиница склейке не мешает.
    var typeDecl = loadMixedScriptType();

    var jsonMembers = typeDecl.members().stream()
      .filter(m -> m.matches("ЗаписатьJSON") || m.matches("WriteJSON"))
      .toList();
    assertThat(jsonMembers)
      .as("ЗаписатьJSON/WriteJSON должны схлопнуться в один двуязычный член")
      .hasSize(1);
    var json = jsonMembers.get(0);
    assertThat(json.matches("ЗаписатьJSON")).as("находится по русскому имени").isTrue();
    assertThat(json.matches("WriteJSON")).as("находится по английскому имени").isTrue();
    assertThat(json.displayName(Language.RU)).isEqualTo("ЗаписатьJSON");
    assertThat(json.displayName(Language.EN)).isEqualTo("WriteJSON");

    var xmlMembers = typeDecl.members().stream()
      .filter(m -> m.matches("ПрочитатьXML") || m.matches("ReadXML"))
      .toList();
    assertThat(xmlMembers)
      .as("ПрочитатьXML/ReadXML должны схлопнуться в один двуязычный член")
      .hasSize(1);
    assertThat(xmlMembers.get(0).displayName(Language.RU)).isEqualTo("ПрочитатьXML");
    assertThat(xmlMembers.get(0).displayName(Language.EN)).isEqualTo("ReadXML");
  }

  @Test
  void latinOnlyMemberWithoutCyrillicPartnerIsNotMerged() {
    // Метод с чисто-латинским именем и без кириллической пары (Flush) не
    // является русской стороной склейки и не должен теряться: остаётся
    // самостоятельным членом, видимым в обоих языках одинаково.
    var typeDecl = loadMixedScriptType();

    var flush = typeDecl.members().stream()
      .filter(m -> m.matches("Flush"))
      .toList();
    assertThat(flush)
      .as("Flush должен остаться ровно одним самостоятельным членом")
      .hasSize(1);
    assertThat(flush.get(0).displayName(Language.RU)).isEqualTo("Flush");
    assertThat(flush.get(0).displayName(Language.EN)).isEqualTo("Flush");
  }

  private static TypePackProvider.TypeDecl loadMixedScriptType() {
    return BuiltinPlatformTypesProvider.loadFromResource(
        "com/github/_1c_syntax/bsl/languageserver/types/registry/mixed-script-member-merge.json")
      .stream()
      .filter(td -> "ТестовыйТип".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();
  }

  @Test
  void asyncFlagIsReadFromJsonForMethods() {
    var types = BuiltinPlatformTypesProvider.loadFromResource(
      "com/github/_1c_syntax/bsl/languageserver/types/registry/async-method.json");
    var typeDecl = types.stream()
      .filter(td -> "ТипСАсинхМетодом".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    var asyncMethod = typeDecl.members().stream()
      .filter(m -> m.matches("ИнициализироватьАсинх"))
      .findFirst().orElseThrow();
    assertThat(asyncMethod.async()).as("метод с \"async\": true помечается асинхронным").isTrue();

    var plainMethod = typeDecl.members().stream()
      .filter(m -> m.matches("Инициализировать"))
      .findFirst().orElseThrow();
    assertThat(plainMethod.async()).as("обычный метод не асинхронный").isFalse();
  }

  @Test
  void primitiveTypesHaveNoConstructors() {
    // given
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when — Число — это примитив, конструкторов у него быть не должно.
    var numberDecl = provider.getTypes().stream()
      .filter(td -> "Число".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(numberDecl.constructors()).isEmpty();
  }

  @Test
  void parameterTypesAreLoadedFromBuiltinJson() {
    // given
    var structure = bslType("Структура");

    // when — у метода Вставить(Ключ, Значение) параметры несут типы из HBK
    var insert = member(structure, "Вставить");
    var firstParam = insert.signatures().get(0).parameters().get(0);

    // then
    assertThat(firstParam.name()).isEqualTo("Ключ");
    assertThat(typeNames(firstParam.types())).containsExactly("Строка");
  }

  @Test
  void unionReturnTypesAreLoadedFromBuiltinJson() {
    // given
    var table = bslType("ТаблицаЗначений");

    // when — Найти возвращает СтрокаТаблицыЗначений либо Неопределено
    var find = member(table, "Найти");

    // then
    assertThat(typeNames(find.returnTypes()))
      .containsExactlyInAnyOrder("СтрокаТаблицыЗначений", "Неопределено");
  }

  @Test
  void constructorsWithParameterTypesAreEnrichedFromBuiltinJson() {
    // given
    var structure = bslType("Структура");

    // when / then — конструкторы дозаполнены из HBK и несут типы параметров
    assertThat(structure.constructors()).isNotEmpty();
    var anyParamWithType = structure.constructors().stream()
      .flatMap(c -> c.parameters().stream())
      .anyMatch(p -> !p.types().isEmpty());
    assertThat(anyParamWithType).isTrue();
  }

  private static TypePackProvider.TypeDecl bslType(String name) {
    return BuiltinPlatformTypesProvider.loadFromResource(
        "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json")
      .stream()
      .filter(td -> name.equals(td.name().primary()))
      .findFirst()
      .orElseThrow();
  }

  private static MemberDescriptor member(TypePackProvider.TypeDecl typeDecl, String name) {
    return typeDecl.members().stream()
      .filter(m -> m.matches(name))
      .findFirst()
      .orElseThrow();
  }

  private static List<String> typeNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
