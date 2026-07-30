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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Распознавание «открытых» объектов данных и чтение записанных на них полей.
 */
class OpenDataObjectInferenceTest {

  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");
  private static final TypeRef MAP = new TypeRef(TypeKind.PLATFORM, "Соответствие");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void structureAndFixedStructureAreStructureLike() {
    // given / when / then
    assertThat(OpenDataObjectInference.isStructureLike("Структура")).isTrue();
    assertThat(OpenDataObjectInference.isStructureLike("ФиксированнаяСтруктура")).isTrue();
    assertThat(OpenDataObjectInference.isStructureLike("FixedStructure")).isTrue();
  }

  @Test
  void typeNameCaseDoesNotMatter() {
    // given: имена типов в коде 1С пишут как придётся.
    // when / then
    assertThat(OpenDataObjectInference.isStructureLike("структура")).isTrue();
    assertThat(OpenDataObjectInference.isValueTableLike("ТАБЛИЦАЗНАЧЕНИЙ")).isTrue();
    assertThat(OpenDataObjectInference.isTypeDescriptionType("описаниетипов")).isTrue();
  }

  @Test
  void mapIsKeyValueLikeButNotStructureLike() {
    // given / when / then
    assertThat(OpenDataObjectInference.isStructureOrMapLike("Соответствие")).isTrue();
    assertThat(OpenDataObjectInference.isStructureLike("Соответствие")).isFalse();
  }

  @Test
  void arrayIsNotAnOpenDataObject() {
    // given: у массива состав членов в коде не складывается.
    // when / then
    assertThat(OpenDataObjectInference.isStructureOrMapLike("Массив")).isFalse();
    assertThat(OpenDataObjectInference.isValueTableLike("Массив")).isFalse();
  }

  @Test
  void fieldTypesAreFoundIgnoringCase() {
    // given
    var types = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Количество", TypeSet.of(NUMBER));

    // when
    var found = OpenDataObjectInference.fieldTypes(types, "количество");

    // then
    assertThat(found.refs()).containsExactly(NUMBER);
  }

  @Test
  void unknownFieldGivesNothing() {
    // given
    var types = TypeSet.of(STRUCTURE).withField(STRUCTURE, "Количество", TypeSet.of(NUMBER));

    // when
    var found = OpenDataObjectInference.fieldTypes(types, "Цена");

    // then
    assertThat(found).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void fieldsOfSeveralTypesAreMergedByName() {
    // given: получатель может быть и структурой, и соответствием — поля собираются вместе,
    // а одноимённые объединяются по типу.
    var types = TypeSet.of(List.of(STRUCTURE, MAP))
      .withField(STRUCTURE, "Ключ", TypeSet.of(NUMBER))
      .withField(MAP, "Ключ", TypeSet.of(STRING))
      .withField(MAP, "Другой", TypeSet.of(STRING));

    // when
    var fields = OpenDataObjectInference.fieldsOf(types);

    // then
    assertThat(fields).containsOnlyKeys("Ключ", "Другой");
    assertThat(fields.get("Ключ").refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void typeSetWithoutFieldsGivesEmptyMap() {
    // given / when
    var fields = OpenDataObjectInference.fieldsOf(TypeSet.of(STRUCTURE));

    // then
    assertThat(fields).isEmpty();
  }
}
