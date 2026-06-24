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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * #4194: типы вложенных элементов коллекций и полей структур, указанные через
 * {@code см. Метод}-ссылку, должны разрешаться так же, как и на верхнем уровне.
 */
@CleanupContextBeforeClassAndAfterClass
class NestedSeeRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void collectionElementTypeViaSeeRef() {
    // // Возвращаемое значение: Массив из см. ОшибкаВалидации
    var declared = typeService.getDeclaredReturnTypes(method("ВалидироватьДанные"));

    assertThat(declared.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");

    var arrayRef = declared.refs().iterator().next();
    var element = declared.getElementTypes(arrayRef);

    assertThat(element.refs())
      .as("элемент `Массив из см. ОшибкаВалидации` разрешается в Структуру")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(element.getAllFieldNames())
      .as("поля структуры-элемента переносятся через См.-ссылку")
      .contains("ИмяПоля", "ОписаниеОшибки");
    assertThat(element.getFieldTypes("ИмяПоля").refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Строка");
  }

  @Test
  void structureFieldTypeViaSeeRef() {
    // поле `* ДанныеТокена - см. ДанныеТокена`
    var declared = typeService.getDeclaredReturnTypes(method("КонтекстЗапроса"));

    assertThat(declared.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(declared.getAllFieldNames())
      .as("поле, типизированное через См.-ссылку, не пропадает из описания")
      .contains("ТипАвторизации", "ДанныеТокена");

    var tokenData = declared.getFieldTypes("ДанныеТокена");
    assertThat(tokenData.refs())
      .as("поле `ДанныеТокена - см. ДанныеТокена` разрешается в Структуру")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(tokenData.getAllFieldNames())
      .as("вложенные поля целевой структуры переносятся через См.-ссылку")
      .contains("Токен", "СрокДействия");

    var structRef = declared.refs().iterator().next();
    assertThat(declared.getLocalFields(structRef).get("ДанныеТокена").description())
      .as("текстовое описание поля с См.-ссылкой не теряется")
      .isEqualTo("Данные токена.");
  }

  @Test
  void parameterCollectionElementTypeViaSeeRef() {
    // // Параметры: ВходныеОшибки - Массив из см. ОшибкаВалидации
    var paramTypes = typeService.getParameterTypes(method("ОбработатьОшибки"));

    assertThat(paramTypes).hasSize(1);
    var inputErrors = paramTypes.get(0);
    assertThat(inputErrors.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");

    var arrayRef = inputErrors.refs().iterator().next();
    var element = inputErrors.getElementTypes(arrayRef);
    assertThat(element.refs())
      .as("элемент типа параметра `Массив из см. ОшибкаВалидации` разрешается в Структуру")
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    assertThat(element.getAllFieldNames())
      .contains("ИмяПоля", "ОписаниеОшибки");
  }

  @Test
  void cyclicSeeRefDoesNotLoop() {
    // ПервыйУзел -> см. ВторойУзел -> см. ПервыйУзел: индексация не должна зациклиться.
    // Без защиты от цикла мьютуальная рекурсия упала бы со StackOverflowError ещё
    // на этапе загрузки документа (внутри лямбды, в том же потоке — workspace-scope
    // активен).
    assertTimeout(Duration.ofSeconds(30), () -> {
      var dc = cyclicDoc();
      var first = method(dc, "ПервыйУзел");
      var declared = typeService.getDeclaredReturnTypes(first);
      assertThat(declared.refs())
        .as("головной тип разрешается, рекурсия по закольцованной ссылке оборвана")
        .extracting(TypeRef::qualifiedName)
        .containsExactly("Массив");
    });
  }

  @Test
  void topLevelCyclicSeeRefResolvesToEmptyWithoutLoop() {
    // ЭхоА -> см. ЭхоБ -> см. ЭхоА: верхнеуровневая закольцованная цепочка
    // (eager-путь) должна оборваться через visited, дав пустой тип, без зацикливания.
    assertTimeout(Duration.ofSeconds(30), () -> {
      var dc = cyclicDoc();
      var echoA = typeService.getDeclaredReturnTypes(method(dc, "ЭхоА"));
      assertThat(echoA.isEmpty())
        .as("чистая закольцованная см.-цепочка не образует типа и не зацикливается")
        .isTrue();
    });
  }

  private MethodSymbol method(String name) {
    return method(doc(), name);
  }

  private MethodSymbol method(DocumentContext dc, String name) {
    return dc.getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElseThrow();
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/NestedSeeRef.bsl");
  }

  private DocumentContext cyclicDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/NestedSeeRefCyclic.bsl");
  }
}
