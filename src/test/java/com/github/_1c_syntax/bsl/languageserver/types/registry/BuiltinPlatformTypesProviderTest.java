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
  void primitiveStringHasNoMembers() {
    // given — у примитива Строка нет методов ни в BSL, ни в OneScript:
    // Длина/ВРег/НРег — это глобальные функции (СтрДлина/ВРег/НРег), а не члены типа.
    when(holder.get()).thenReturn(Optional.empty());
    var provider = new BuiltinPlatformTypesProvider(holder);

    // when
    var stringDecl = provider.getTypes().stream()
      .filter(td -> "Строка".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // then
    assertThat(stringDecl.members())
      .as("у примитива Строка не должно быть методов")
      .isEmpty();
  }

  @Test
  void oscriptBilingualMemberIsSingleBilingualMember() {
    // Члены oscript-дампа двуязычны явными полями nameRu/nameEn (как в BSL-модели):
    // Массив.Добавить/Add — один член, а не два моноязычных. Никакой склейки по
    // порядку при загрузке нет.
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
      .as("Добавить/Add — один двуязычный член")
      .hasSize(1);
    var add = addMembers.get(0);
    assertThat(add.matches("Добавить")).as("находится по русскому имени").isTrue();
    assertThat(add.matches("Add")).as("находится по английскому имени").isTrue();
    assertThat(add.displayName(Language.RU)).isEqualTo("Добавить");
    assertThat(add.displayName(Language.EN)).isEqualTo("Add");
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
  void oscriptObjectPropertiesCarryReturnType() {
    // Регрессия на #4006: свойства объектов OneScript должны нести тип, иначе
    // вывод типов даёт Unknown и пропадают подсказки. Заголовки у HTTPЗапрос —
    // это Соответствие; тип берётся из исходников OneScript-движка.
    var httpRequest = oscriptType("HTTPЗапрос");

    var headers = member(httpRequest, "Заголовки");
    assertThat(typeNames(headers.returnTypes()))
      .as("Заголовки имеют тип Соответствие")
      .containsExactly("Соответствие");

    var resourceAddress = member(httpRequest, "АдресРесурса");
    assertThat(typeNames(resourceAddress.returnTypes()))
      .containsExactly("Строка");
  }

  @Test
  void oscriptObjectPropertiesAreBilingualSingleMember() {
    // #4006-follow-up: после простановки returnType ru/en-пара свойства всё
    // ещё должна схлопываться в один двуязычный член (одинаковый returnType
    // → совпадает s/fingerprint склейки). Иначе в ru-локали показались бы
    // оба написания (Заголовки и Headers).
    var httpRequest = oscriptType("HTTPЗапрос");

    var headersMembers = httpRequest.members().stream()
      .filter(m -> m.matches("Заголовки") || m.matches("Headers"))
      .toList();

    assertThat(headersMembers)
      .as("Заголовки/Headers — ровно один двуязычный член")
      .hasSize(1);
    var headers = headersMembers.get(0);
    assertThat(headers.displayName(Language.RU)).isEqualTo("Заголовки");
    assertThat(headers.displayName(Language.EN)).isEqualTo("Headers");
    assertThat(headers.matches("Заголовки")).isTrue();
    assertThat(headers.matches("Headers")).isTrue();
  }

  @Test
  void oscriptObjectMethodReturnTypesAreSpecific() {
    // Регрессия на #4006: возвращаемые значения методов объектов OneScript
    // не должны теряться. ПолучитьТелоКакПоток возвращает Поток.
    var httpRequest = oscriptType("HTTPЗапрос");

    var getBodyAsStream = member(httpRequest, "ПолучитьТелоКакПоток");
    assertThat(typeNames(getBodyAsStream.returnTypes()))
      .containsExactly("Поток");
  }

  private static TypePackProvider.TypeDecl oscriptType(String name) {
    return BuiltinPlatformTypesProvider.loadFromResource(
        "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-platform-types.json")
      .stream()
      .filter(td -> name.equals(td.name().primary()))
      .findFirst()
      .orElseThrow();
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
