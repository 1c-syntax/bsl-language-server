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
package com.github._1c_syntax.bsl.languageserver.types.util;

import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureSelectionTest {

  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef FIXED_ARRAY = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");

  // === pickIndexByArity ===

  @Test
  void pickIndexByArityReturnsExactMatch() {
    var sigs = List.of(
      signature(List.of(param("a", false))),
      signature(List.of(param("a", false), param("b", false)))
    );
    assertThat(SignatureSelection.pickIndexByArity(sigs, 1)).isEqualTo(0);
    assertThat(SignatureSelection.pickIndexByArity(sigs, 2)).isEqualTo(1);
  }

  @Test
  void pickIndexByArityRespectsOptional() {
    // (a, b?) — принимает 1 или 2 аргумента.
    var sigs = List.of(
      signature(List.of(param("a", false), param("b", true)))
    );
    assertThat(SignatureSelection.pickIndexByArity(sigs, 1)).isEqualTo(0);
    assertThat(SignatureSelection.pickIndexByArity(sigs, 2)).isEqualTo(0);
  }

  @Test
  void pickIndexByArityReturnsMinusOneIfNoMatch() {
    var sigs = List.of(signature(List.of(param("a", false))));
    // 2 arg при единственной сигнатуре с 1 обязательным — нет match'а
    assertThat(SignatureSelection.pickIndexByArity(sigs, 2)).isEqualTo(-1);
  }

  @Test
  void pickIndexByArityEmptySignatures() {
    assertThat(SignatureSelection.pickIndexByArity(List.of(), 0)).isEqualTo(-1);
  }

  // === pickIndexByActiveParameter ===

  @Test
  void pickIndexByActiveParameterPicksSmallestSignatureCoveringPosition() {
    var sigs = List.of(
      signature(List.of(param("a", false))),
      signature(List.of(param("a", false), param("b", false)))
    );
    // activeParameter = 0 — оба покрывают, побеждает меньший (первый)
    assertThat(SignatureSelection.pickIndexByActiveParameter(sigs, 0)).isEqualTo(0);
    // activeParameter = 1 — покрывает только второй
    assertThat(SignatureSelection.pickIndexByActiveParameter(sigs, 1)).isEqualTo(1);
    // activeParameter = 5 — ни один не покрывает, фолбэк к largest
    assertThat(SignatureSelection.pickIndexByActiveParameter(sigs, 5)).isEqualTo(1);
  }

  // === pickIndexByTypes ===

  @Test
  void pickIndexByTypesPicksMatchingSignature() {
    // ТЗ.Скопировать(Строки: Массив, ...) vs ТЗ.Скопировать(ПараметрыОтбора: Структура, ...)
    var sigArray = signature(List.of(
      param("Строки", true, TypeSet.of(ARRAY)),
      param("Колонки", true, TypeSet.of(STRING))
    ));
    var sigStruct = signature(List.of(
      param("ПараметрыОтбора", true, TypeSet.of(STRUCTURE)),
      param("Колонки", true, TypeSet.of(STRING))
    ));
    var sigs = List.of(sigArray, sigStruct);

    // Первый аргумент Массив → первый вариант
    var pick1 = SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.of(ARRAY), TypeSet.of(STRING)));
    assertThat(pick1).isEqualTo(0);

    // Первый аргумент Структура → второй вариант
    var pick2 = SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.of(STRUCTURE), TypeSet.of(STRING)));
    assertThat(pick2).isEqualTo(1);
  }

  @Test
  void pickIndexByTypesUnknownArgTypeIsNeutral() {
    // Когда тип аргумента EMPTY, оба варианта получают одинаковый score
    // (по типу пустого аргумента); побеждает первый (наименьший индекс).
    var sigArray = signature(List.of(param("Строки", true, TypeSet.of(ARRAY))));
    var sigStruct = signature(List.of(param("ПараметрыОтбора", true, TypeSet.of(STRUCTURE))));
    var sigs = List.of(sigArray, sigStruct);
    assertThat(SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.EMPTY)))
      .isEqualTo(0);
  }

  @Test
  void pickIndexByTypesVariadicLastParameter() {
    // Новый Массив(<КоличествоЭлементов1,...,КоличествоЭлементовN>): Число variadic.
    var variadic = signature(List.of(
      param("КоличествоЭлементов1,...,КоличествоЭлементовN", true, TypeSet.of(NUMBER))
    ));
    var byFixedArray = signature(List.of(
      param("Массив", true, TypeSet.of(FIXED_ARRAY))
    ));
    var sigs = List.of(byFixedArray, variadic);

    // 1 аргумент Число → variadic; 3 аргумента Число → variadic (тоже).
    assertThat(SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.of(NUMBER))))
      .isEqualTo(1);
    assertThat(SignatureSelection.pickIndexByTypes(sigs, List.of(
      TypeSet.of(NUMBER), TypeSet.of(NUMBER), TypeSet.of(NUMBER))))
      .isEqualTo(1);
    // 1 аргумент ФиксированныйМассив → конкретный вариант
    assertThat(SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.of(FIXED_ARRAY))))
      .isEqualTo(0);
  }

  @Test
  void pickIndexByTypesFallsBackToArityWhenNoMatch() {
    var sigs = List.of(
      signature(List.of(param("a", false, TypeSet.of(ARRAY)))),
      signature(List.of(param("a", false, TypeSet.of(STRUCTURE)), param("b", false, TypeSet.of(STRING))))
    );
    // 2 arg → попадает по arity на вторую сигнатуру (несмотря на типы).
    assertThat(SignatureSelection.pickIndexByTypes(sigs, List.of(TypeSet.of(NUMBER), TypeSet.of(NUMBER))))
      .isEqualTo(1);
  }

  @Test
  void pickIndexByTypesEmptySignatures() {
    assertThat(SignatureSelection.pickIndexByTypes(List.of(), List.of())).isEqualTo(-1);
  }

  // === helpers ===

  private static ParameterDescriptor param(String name, boolean optional) {
    return new ParameterDescriptor(name, TypeSet.EMPTY, optional, "");
  }

  private static ParameterDescriptor param(String name, boolean optional, TypeSet types) {
    return new ParameterDescriptor(name, types, optional, "");
  }

  private static SignatureDescriptor signature(List<ParameterDescriptor> params) {
    return new SignatureDescriptor(params, TypeSet.EMPTY, "");
  }
}
