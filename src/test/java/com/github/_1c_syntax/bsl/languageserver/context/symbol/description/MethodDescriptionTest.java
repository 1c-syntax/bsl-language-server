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
package com.github._1c_syntax.bsl.languageserver.context.symbol.description;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.description.HyperlinkTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.support.Hyperlink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MethodDescriptionTest {

  private List<MethodDescription> methodsWithDescription;

  @BeforeEach
  void prepare() {
    if (methodsWithDescription == null) {
      var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/MethodDescription.bsl");
      var methods = documentContext.getSymbolTree().getMethods();

      assertThat(methods).hasSize(16);

      methodsWithDescription = methods.stream()
        .map(MethodSymbol::getDescription)
        .flatMap(Optional::stream)
        .toList();

      assertThat(methodsWithDescription.size()).isEqualTo(15);
    }
  }

  @Test
  void testMethodWithAnnotationBeforeDescription() {
    var method = methodsWithDescription.get(14);
    assertThat(method.getDescription()).isEqualTo("// Описание процедуры");
  }

  @Test
  void testMethodWithAnnotation() {
    var method = methodsWithDescription.get(13);
    assertThat(method.getDescription()).isEqualTo("// Описание процедуры");
  }

  @Test
  void testMethod13() {
    var method = methodsWithDescription.get(12);
    assertThat(method.getPurposeDescription()).isEqualTo("""
      Значения реквизитов, прочитанные из информационной базы для нескольких объектов.
      
      Если необходимо зачитать реквизит независимо от прав текущего пользователя,
      то следует использовать предварительный переход в привилегированный режим.""");
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).hasSize(3);
    assertThat(method.getReturnedValue()).hasSize(1);
    assertThat(method.getLinks()).isEmpty();

    var param = method.getParameters().get(0);
    assertThat(param.name()).isEqualTo("Ссылки");
    assertThat(param.types()).hasSize(1);
    assertThat(param.types().get(0).name()).isEqualTo("Массив");
    assertThat(param.types().get(0).description()).isEqualTo("""
      массив ссылок на объекты одного типа.
      Значения массива должны быть ссылками на объекты одного типа.
      если массив пуст, то результатом будет пустое соответствие.""");
    assertThat(param.isHyperlink()).isFalse();

    param = method.getParameters().get(1);
    assertThat(param.name()).isEqualTo("Реквизиты");
    assertThat(param.types()).hasSize(1);
    assertThat(param.types().get(0).name()).isEqualTo("Строка");
    assertThat(param.types().get(0).description()).isEqualTo(
      """
      имена реквизитов перечисленные через запятую, в формате требований к свойствам
      структуры. Например, "Код, Наименование, Родитель".""");
    assertThat(param.isHyperlink()).isFalse();

    param = method.getParameters().get(2);
    assertThat(param.name()).isEqualTo("ВыбратьРазрешенные");
    assertThat(param.types()).hasSize(1);
    assertThat(param.types().get(0).name()).isEqualTo("Булево");
    assertThat(param.types().get(0).description()).isEqualTo(
      """
        если Истина, то запрос к объектам выполняется с учетом прав пользователя, и в случае,
        - если какой-либо объект будет исключен из выборки по правам, то этот объект
        будет исключен и из результата;
        - если Ложь, то возникнет исключение при отсутствии прав на таблицу
        или любой из реквизитов.""");
    assertThat(param.isHyperlink()).isFalse();

    var type = method.getReturnedValue().get(0);
    assertThat(type.name()).isEqualTo("Соответствие");
    assertThat(type.description()).isEqualTo("список объектов и значений их реквизитов:");
    assertThat(type.fields()).hasSize(2);

  }

  @Test
  void testMethod12() {
    var method = methodsWithDescription.get(11);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).hasSize(1);
    assertThat(method.getLinks()).hasSize(1);

    var type = method.getReturnedValue().get(0);
    assertThat(type.name()).isEqualTo("ОбщийМодуль.Метод");
    assertThat(type.description()).isEmpty();
    assertThat(type.fields()).isEmpty();
    assertThat(type).isInstanceOf(HyperlinkTypeDescription.class);
    assertThat(((HyperlinkTypeDescription) type).hyperlink().link()).isEqualTo("ОбщийМодуль.Метод");
  }

  @Test
  void testMethod11() {
    var method = methodsWithDescription.get(10);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).hasSize(1);
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).hasSize(1);

    var param = method.getParameters().get(0);
    assertThat(param.name()).isEqualTo("ОбщийМодуль.Метод");
    assertThat(param.types()).hasSize(1);
    assertThat(param.isHyperlink()).isTrue();
    assertThat(param.link().link()).isEqualTo("ОбщийМодуль.Метод");
  }

  @Test
  void testMethod10() {
    var method = methodsWithDescription.get(9);
    assertThat(method.getPurposeDescription())
      .isEqualTo("См. ОбщийМодуль.Метод()");
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).extracting(Hyperlink::link).contains("ОбщийМодуль.Метод");
  }

  @Test
  void testMethod9() {
    var method = methodsWithDescription.get(8);
    assertThat(method.getPurposeDescription())
      .isEqualTo("Описание метода\nНесколько строк");
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).hasSize(1);
    assertThat(method.getLinks()).isEmpty();
  }

  @Test
  void testMethod8() {
    var method = methodsWithDescription.get(7);
    assertThat(methodsWithDescription.get(7).getPurposeDescription())
      .isEqualTo("Описание метода\nНесколько строк");
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).hasSize(3);
    assertThat(method.getReturnedValue()).hasSize(1);
    assertThat(method.getLinks()).isEmpty();
  }

  @Test
  void testMethod7() {
    var method = methodsWithDescription.get(6);
    assertThat(method.getPurposeDescription())
      .isEqualTo("Описание метода\nНесколько строк");
    assertThat(method.isDeprecated()).isTrue();
    assertThat(method.getDeprecationInfo())
      .isEqualTo("Следует использовать новую см. ОбщегоНазначения.Метод()");
    assertThat(method.getExamples()).hasSize(73);
    assertThat(method.getExamples()).startsWith("Пример0(Тип11, Тип21) - описание ...");
    assertThat(method.getCallOptions()).hasSize(103);
    assertThat(method.getCallOptions()).startsWith("УниверсальнаяПроцедура(Тип11, Тип21) - описание ...");
    assertThat(method.getParameters()).hasSize(3);
    assertThat(method.getReturnedValue()).hasSize(2);
    assertThat(method.getLinks()).hasSize(1);
  }

  @Test
  void testMethod6() {
    var method = methodsWithDescription.get(5);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isTrue();
    assertThat(method.getDeprecationInfo())
      .isEqualTo("Следует использовать новую см. ОбщегоНазначения.Метод()");
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).hasSize(1);
  }

  @Test
  void testMethod5() {
    var method = methodsWithDescription.get(4);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).hasSize(104);
    assertThat(method.getExamples())
      .startsWith("УниверсальнаяПроцедура(Тип11, Тип21) - описание ...");
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).isEmpty();
  }

  @Test
  void testMethod4() {
    var method = methodsWithDescription.get(3);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).hasSize(2);
    assertThat(method.getLinks()).isEmpty();
    var type = method.getReturnedValue().get(0);
    assertThat(type.description()).isEqualTo("ссылка на предопределенный элемент.");
    assertThat(type.name()).isEqualTo("ЛюбаяСсылка");
    assertThat(type.fields()).isEmpty();
    type = method.getReturnedValue().get(1);
    assertThat(type.description()).isEqualTo("если предопределенный элемент есть в метаданных, но не создан в ИБ.");
    assertThat(type.name()).isEqualTo("Неопределено");
    assertThat(type.fields()).isEmpty();
  }

  @Test
  void testMethod3() {
    var method = methodsWithDescription.get(2);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).hasSize(155);
    assertThat(method.getCallOptions())
      .startsWith("УниверсальнаяПроцедура(Тип11, Тип21) - описание ...");
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).isEmpty();
  }

  @Test
  void testMethod2() {
    var method = methodsWithDescription.get(1);
    assertThat(method.getPurposeDescription()).isEmpty();
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).hasSize(8);
    assertThat(method.getLinks()).hasSize(3);
    var param = method.getParameters().get(0);
    assertThat(param.name()).isEqualTo("ПараметрБезТипаИОписания");
    assertThat(param.types()).isEmpty();

    param = method.getParameters().get(1);
    assertThat(param.name()).isEqualTo("ПараметрСТипом");
    assertThat(param.types()).hasSize(1);
    var type = param.types().get(0);
    assertThat(type.description()).isEmpty();
    assertThat(type.name()).isEqualTo("Произвольный");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(2);
    assertThat(param.name()).isEqualTo("ПараметрСОписаниемСсылкой");
    assertThat(param.types()).hasSize(1);
    type = param.types().get(0);
    assertThat(type.description()).isEmpty();
    assertThat(type.name()).isEqualTo("ПодключаемыеКомандыПереопределяемый.ПриОпределенииКомандПодключенныхКОбъекту.НастройкиФормы");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(3);
    assertThat(param.name()).isEqualTo("ПараметрСТипомИОписанием");
    assertThat(param.types()).hasSize(1);
    type = param.types().get(0);
    assertThat(type.description()).isEqualTo("описание параметра см. Справочник.Контрагенты.");
    assertThat(type.name()).isEqualTo("Произвольный");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(4);
    assertThat(param.name()).isEqualTo("ПараметрСТипамиИОписанием");
    assertThat(param.types()).hasSize(2);
    type = param.types().get(0);
    assertThat(type.description()).isEqualTo("простое описание параметра.");
    assertThat(type.name()).isEqualTo("Произвольный");
    assertThat(type.fields()).isEmpty();
    type = param.types().get(1);
    assertThat(type.description()).isEmpty();
    assertThat(type.name()).isEqualTo("ДокументСсылка");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(5);
    assertThat(param.name()).isEqualTo("ПараметрСТипамиИОписанием2");
    assertThat(param.types()).hasSize(2);
    type = param.types().get(0);
    assertThat(type.description()).isEqualTo("многострочное");
    assertThat(type.name()).isEqualTo("Произвольный");
    assertThat(type.fields()).isEmpty();
    type = param.types().get(1);
    assertThat(type.description()).isEqualTo("описание параметра");
    assertThat(type.name()).isEqualTo("ДокументСсылка");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(6);
    assertThat(param.name()).isEqualTo("ПараметрСТипамиИОписанием3");
    assertThat(param.types()).hasSize(4);
    type = param.types().get(0);
    assertThat(type.description()).isEqualTo("описание произвольного типа");
    assertThat(type.name()).isEqualTo("Произвольный");
    assertThat(type.fields()).isEmpty();
    type = param.types().get(1);
    assertThat(type.description()).isEqualTo("какой-то документ\nименно\nссылка");
    assertThat(type.name()).isEqualTo("ДокументСсылка");
    assertThat(type.fields()).isEmpty();
    type = param.types().get(2);
    assertThat(type.description()).isEqualTo("многострочное\nописание числа");
    assertThat(type.name()).isEqualTo("Число");
    assertThat(type.fields()).isEmpty();
    type = param.types().get(3);
    assertThat(type.description()).isEmpty();
    assertThat(type.name()).isEqualTo("Строка");
    assertThat(type.fields()).isEmpty();

    param = method.getParameters().get(7);
    assertThat(param.name()).isEqualTo("ПараметрМассив");
    assertThat(param.types()).hasSize(1);
    type = param.types().get(0);
    assertThat(type.description()).isEmpty();
    assertThat(type.name()).isEqualTo("Массив<Структура>");
    assertThat(type.fields()).hasSize(4);
    param = type.fields().get(0);
    assertThat(param.name()).isEqualTo("Элемент1");
    assertThat(param.types()).hasSize(1);
    var subtype = param.types().get(0);
    assertThat(subtype.description()).isEmpty();
    assertThat(subtype.name()).isEqualTo("Структура");
    assertThat(subtype.fields()).hasSize(3);
    var subparam = subtype.fields().get(0);
    assertThat(subparam.name()).isEqualTo("СубЭлемент1");
    assertThat(subparam.types()).hasSize(1);
    subparam = subtype.fields().get(1);
    assertThat(subparam.name()).isEqualTo("Субэлемент2");
    assertThat(subparam.types()).hasSize(1);
    subparam = subtype.fields().get(2);
    assertThat(subparam.name()).isEqualTo("СубЭлемент3");
    assertThat(subparam.types()).hasSize(1);
    param = type.fields().get(1);
    assertThat(param.name()).isEqualTo("Элемент2");
    assertThat(param.types()).hasSize(1);
    param = type.fields().get(2);
    assertThat(param.name()).isEqualTo("Элемент3");
    assertThat(param.types()).hasSize(1);
    param = type.fields().get(3);
    assertThat(param.name()).isEqualTo("Жесть");
    assertThat(param.types()).hasSize(1);
    subtype = param.types().get(0);
    assertThat(subtype.description()).isEmpty();
    assertThat(subtype.name()).isEqualTo("Структура");
    assertThat(subtype.fields()).hasSize(1);
    subparam = subtype.fields().get(0);
    assertThat(subparam.name()).isEqualTo("Массив");
    assertThat(subparam.types()).hasSize(1);
    var subsubtype = subparam.types().get(0);
    assertThat(subsubtype.description()).isEmpty();
    assertThat(subsubtype.name()).isEqualTo("Массив<Структура>");
    assertThat(subsubtype.fields()).hasSize(2);
    var subsubparam = subsubtype.fields().get(0);
    assertThat(subsubparam.name()).isEqualTo("Элемент");
    assertThat(subsubparam.types()).hasSize(1);
    subsubparam = subsubtype.fields().get(1);
    assertThat(subsubparam.name()).isEqualTo("Элемент4");
    assertThat(subsubparam.types()).hasSize(1);
    subsubtype = subsubparam.types().get(0);
    assertThat(subsubtype.description()).isEmpty();
    assertThat(subsubtype.name()).isEqualTo("строка");
    assertThat(subsubtype.fields()).hasSize(1);
    subsubparam = subsubtype.fields().get(0);
    assertThat(subsubparam.name()).isEqualTo("Элемент5");
    assertThat(subsubparam.types()).hasSize(1);
    assertThat(method.getReturnedValue()).isEmpty();
  }

  @Test
  void testMethod1() {
    var method = methodsWithDescription.get(0);
    assertThat(method.getPurposeDescription())
      .isNotEmpty()
      .isEqualTo("Описание метода");
    assertThat(method.isDeprecated()).isFalse();
    assertThat(method.getDeprecationInfo()).isEmpty();
    assertThat(method.getExamples()).isEmpty();
    assertThat(method.getCallOptions()).isEmpty();
    assertThat(method.getParameters()).isEmpty();
    assertThat(method.getReturnedValue()).isEmpty();
    assertThat(method.getLinks()).isEmpty();
  }
}
