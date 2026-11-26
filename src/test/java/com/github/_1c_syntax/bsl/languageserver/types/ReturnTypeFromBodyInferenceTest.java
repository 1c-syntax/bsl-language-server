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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип возвращаемого значения функции, рассчитанный по её телу: типы всех точек выхода
 * объединяются, а объявленный в документирующем комментарии тип их дополняет.
 */
@CleanupContextBeforeClassAndAfterClass
class ReturnTypeFromBodyInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  void branchesGiveUnionOfReturnedTypes() {
    // given: функция без описания возвращает массив в одной ветке и число в другой.
    var documentContext = TestUtils.getDocumentContext("""
      Функция РазныеТипы(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	Иначе
      		Возврат 10;
      	КонецЕсли;
      КонецФункции

      Процедура Вызов()
      	Результат = РазныеТипы(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = РазныеТипы(Ложь)", "Результат = ".length());

    // then
    assertThat(names(types)).containsExactlyInAnyOrder("Массив", "Число");
  }

  @Test
  void declaredTypeExtendsComputedOnes() {
    // given: у той же функции в комментарии объявлен третий тип.
    var documentContext = TestUtils.getDocumentContext("""
      // Возвращаемое значение:
      //  Булево -
      Функция РазныеТипы(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	Иначе
      		Возврат 10;
      	КонецЕсли;
      КонецФункции

      Процедура Вызов()
      	Результат = РазныеТипы(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = РазныеТипы(Ложь)", "Результат = ".length());

    // then: объявленный тип не заменяет расчётные, а добавляется к ним.
    assertThat(names(types)).containsExactlyInAnyOrder("Массив", "Число", "Булево");
  }

  @Test
  void reachableBodyEndAddsUndefined() {
    // given: возврат значения есть только в одной ветке, конец тела достижим.
    var documentContext = TestUtils.getDocumentContext("""
      Функция ВозвратНеНаВсехПутях(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	КонецЕсли;
      КонецФункции

      Процедура Вызов()
      	Результат = ВозвратНеНаВсехПутях(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = ВозвратНеНаВсехПутях(Ложь)", "Результат = ".length());

    // then: неявный выход из функции возвращает Неопределено.
    assertThat(names(types)).containsExactlyInAnyOrder("Массив", "Неопределено");
  }

  @Test
  void everyPathReturningValueDoesNotAddUndefined() {
    // given: значение возвращается на всех путях.
    var documentContext = TestUtils.getDocumentContext("""
      Функция ВозвратНаВсехПутях(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	Иначе
      		Возврат Новый Структура;
      	КонецЕсли;
      КонецФункции

      Процедура Вызов()
      	Результат = ВозвратНаВсехПутях(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = ВозвратНаВсехПутях(Ложь)", "Результат = ".length());

    // then
    assertThat(names(types)).containsExactlyInAnyOrder("Массив", "Структура");
  }

  @Test
  void raiseIsNotAReturnedValue() {
    // given: одна ветка возвращает значение, другая выбрасывает исключение.
    var documentContext = TestUtils.getDocumentContext("""
      Функция ВозвратИлиИсключение(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	Иначе
      		ВызватьИсключение "Ошибка";
      	КонецЕсли;
      КонецФункции

      Процедура Вызов()
      	Результат = ВозвратИлиИсключение(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = ВозвратИлиИсключение(Ложь)", "Результат = ".length());

    // then: исключение значения не возвращает и Неопределено в набор не приносит.
    assertThat(names(types)).containsExactly("Массив");
  }

  @Test
  void recursiveFunctionDoesNotLoop() {
    // given: функция возвращает результат собственного вызова.
    var documentContext = TestUtils.getDocumentContext("""
      Функция Рекурсия(Флаг)
      	Если Флаг Тогда
      		Возврат Новый Массив;
      	КонецЕсли;
      	Возврат Рекурсия(Ложь);
      КонецФункции

      Процедура Вызов()
      	Результат = Рекурсия(Ложь);
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = Рекурсия(Ложь)", "Результат = ".length());

    // then: расчёт обрывается на самоссылке и отдаёт то, что нашёл.
    assertThat(names(types)).contains("Массив");
  }

  @Test
  void procedureHasNoReturnedValue() {
    // given: процедура значения не возвращает.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура БезВозврата()
      	Возврат;
      КонецПроцедуры

      Процедура Вызов()
      	Результат = БезВозврата();
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = БезВозврата()", "Результат = ".length());

    // then
    assertThat(names(types)).isEmpty();
  }

  @Test
  void bodyOfAnotherModuleMethodIsRead() {
    // given: у функции общего модуля объявлен тип «ОбщийМодуль», а тело возвращает
    // Неопределено.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Вызов()
      	Результат = ОбщегоНазначения.ОбщийМодуль("Имя");
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "Результат = ОбщегоНазначения", "Результат = ".length());

    // then: тело чужого модуля прочитано и дополняет объявленный тип.
    assertThat(names(types)).containsExactlyInAnyOrder("ОбщийМодуль", "Неопределено");
  }

  private TypeSet at(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static List<String> names(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
