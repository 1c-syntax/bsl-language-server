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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypePackProvider.TypeDecl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты общего загрузчика встроенных JSON-паков платформенных типов
 * {@link BuiltinTypesJsonLoader} — на данных и BSL, и OneScript-паков.
 */
class BuiltinTypesJsonLoaderTest {

  private static final String OSCRIPT_RESOURCE =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-platform-types.json";
  private static final String BSL_RESOURCE =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json";

  @Test
  void oscriptBilingualMemberIsSingleBilingualMember() {
    // given — члены oscript-дампа двуязычны явными полями nameRu/nameEn (как в
    // BSL-модели): Массив.Добавить/Add — один член, а не два моноязычных.
    var arrayDecl = oscriptType("Массив");

    // when
    var addMembers = arrayDecl.members().stream()
      .filter(m -> m.matches("Добавить") || m.matches("Add"))
      .toList();

    // then — ровно один двуязычный член, никакой склейки по порядку
    assertThat(addMembers).as("Добавить/Add — один двуязычный член").hasSize(1);
    var add = addMembers.get(0);
    assertThat(add.matches("Добавить")).as("находится по русскому имени").isTrue();
    assertThat(add.matches("Add")).as("находится по английскому имени").isTrue();
    assertThat(add.displayName(Language.RU)).isEqualTo("Добавить");
    assertThat(add.displayName(Language.EN)).isEqualTo("Add");
  }

  @Test
  void asyncFlagIsReadFromJsonForMethods() {
    // given
    var typeDecl = BuiltinTypesJsonLoader.load(
        "com/github/_1c_syntax/bsl/languageserver/types/registry/async-method.json")
      .stream()
      .filter(td -> "ТипСАсинхМетодом".equals(td.name().primary()))
      .findFirst()
      .orElseThrow();

    // when
    var asyncMethod = member(typeDecl, "ИнициализироватьАсинх");
    var plainMethod = member(typeDecl, "Инициализировать");

    // then
    assertThat(asyncMethod.async()).as("метод с \"async\": true помечается асинхронным").isTrue();
    assertThat(plainMethod.async()).as("обычный метод не асинхронный").isFalse();
  }

  @Test
  void oscriptObjectPropertiesCarryReturnType() {
    // given — регрессия на #4006: свойства объектов OneScript должны нести тип,
    // иначе вывод типов даёт Unknown. Заголовки у HTTPЗапрос — это Соответствие.
    var httpRequest = oscriptType("HTTPЗапрос");

    // when
    var headers = member(httpRequest, "Заголовки");
    var resourceAddress = member(httpRequest, "АдресРесурса");

    // then
    assertThat(typeNames(headers.returnTypes()))
      .as("Заголовки имеют тип Соответствие")
      .containsExactly("Соответствие");
    assertThat(typeNames(resourceAddress.returnTypes())).containsExactly("Строка");
  }

  @Test
  void oscriptObjectPropertyIsBilingualSingleMember() {
    // given — #4006-follow-up: ru/en-написание свойства — один двуязычный член,
    // иначе в ru-локали показались бы оба написания (Заголовки и Headers).
    var httpRequest = oscriptType("HTTPЗапрос");

    // when
    var headersMembers = httpRequest.members().stream()
      .filter(m -> m.matches("Заголовки") || m.matches("Headers"))
      .toList();

    // then
    assertThat(headersMembers).as("Заголовки/Headers — ровно один двуязычный член").hasSize(1);
    var headers = headersMembers.get(0);
    assertThat(headers.displayName(Language.RU)).isEqualTo("Заголовки");
    assertThat(headers.displayName(Language.EN)).isEqualTo("Headers");
    assertThat(headers.matches("Заголовки")).isTrue();
    assertThat(headers.matches("Headers")).isTrue();
  }

  @Test
  void oscriptObjectMethodReturnTypesAreSpecific() {
    // given — регрессия на #4006: возвращаемые значения методов не теряются.
    var httpRequest = oscriptType("HTTPЗапрос");

    // when
    var getBodyAsStream = member(httpRequest, "ПолучитьТелоКакПоток");

    // then — ПолучитьТелоКакПоток возвращает Поток
    assertThat(typeNames(getBodyAsStream.returnTypes())).containsExactly("Поток");
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

    // when
    var anyParamWithType = structure.constructors().stream()
      .flatMap(c -> c.parameters().stream())
      .anyMatch(p -> !p.types().isEmpty());

    // then — конструкторы дозаполнены из HBK и несут типы параметров
    assertThat(structure.constructors()).isNotEmpty();
    assertThat(anyParamWithType).isTrue();
  }

  @Test
  void errorCategoryEnumIsLoadedFromBuiltinJson() {
    // given — системное перечисление КатегорияОшибки перенесено в BSL-пак
    // из bsl-context (синтакс-помощник платформы 8.3.26)
    var errorCategory = bslType("КатегорияОшибки");

    // when
    var networkError = member(errorCategory, "ОшибкаСети");

    // then — перечисление двуязычно, глобально и несёт все 28 значений
    assertThat(errorCategory.isEnum()).isTrue();
    assertThat(errorCategory.exposedAsGlobal()).isTrue();
    assertThat(errorCategory.name().en()).isEqualTo("ErrorCategory");
    assertThat(errorCategory.members()).hasSize(28);
    assertThat(networkError.matches("NetworkError")).as("значение находится по en-имени").isTrue();
    assertThat(typeNames(networkError.returnTypes())).containsExactly("КатегорияОшибки");
    assertThat(networkError.metadata().sinceVersion()).isEqualTo("8.3.17");
  }

  private static TypeDecl oscriptType(String name) {
    return type(OSCRIPT_RESOURCE, name);
  }

  private static TypeDecl bslType(String name) {
    return type(BSL_RESOURCE, name);
  }

  private static TypeDecl type(String resource, String name) {
    return BuiltinTypesJsonLoader.load(resource).stream()
      .filter(td -> name.equals(td.name().primary()))
      .findFirst()
      .orElseThrow();
  }

  private static MemberDescriptor member(TypeDecl typeDecl, String name) {
    return typeDecl.members().stream()
      .filter(m -> m.matches(name))
      .findFirst()
      .orElseThrow();
  }

  private static List<String> typeNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
