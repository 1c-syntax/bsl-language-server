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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.fieldNames;
import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.names;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода», раздел «Лучшие практики»:
 * пункты, которые проверяются только в модуле формы (4.56, 4.57, 4.59, 4.60).
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в имени теста, а само требование —
 * в javadoc теста: подраздел рекомендации, её формулировка и приведённая там запись. Нумерация
 * пунктов — наша, в тексте рекомендации её нет.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecFormModuleTest extends AbstractServerContextAwareTest {

  private static final String FORM_MODULE =
    "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  /**
   * «Использование строковых литералов в качестве имен», исключение: «обращение к элементам
   * и свойствам создаваемым программно и в момент статического анализа их еще не существует. При
   * этом следует указывать тип локальной переменной полученного значения и уже потом обращаться
   * к ее свойства».
   * <pre>
   * Элемент = Элементы["Наименование"]; // ПолеФормы -
   * Элемент.Видимость = Ложь;
   * Элемент.Доступность = Истина;
   * </pre>
   */
  @Test
  @DisplayName("4.56 Программно созданный элемент: строковый индекс и строчный тип переменной")
  void formItemByStringIndexWithInlineType() {
    // given
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        Проба_4_56 = Элементы["СозданныйПрограммно"]; // ПолеФормы -
        Использование(Проба_4_56);
      КонецПроцедуры

      Процедура Использование(Значение)
        Возврат;
      КонецПроцедуры
      """);

    // when
    var types = SpecProbes.typeOfVariable(typeService, documentContext, "Проба_4_56");

    // then: совпадает с рекомендацией — строчный тип задаёт тип переменной.
    assertThat(names(types)).containsExactly("ПолеФормы");
  }

  /**
   * «Использование строковых литералов в качестве имен», исключение: «наличие таких элементов
   * в коллекции может быть опциональным, при этом выполняется проверка наличия элемента».
   * <pre>
   * Элемент = Элементы.Найти("Наименование");
   * Если Элемент &lt;&gt; Неопределено Тогда
   *     Элемент.Видимость = Ложь;
   * </pre>
   */
  @Test
  @DisplayName("4.57 «Элементы.Найти(\"Имя\")» с проверкой на Неопределено")
  void formItemByFindWithUndefinedCheck() {
    // given
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        Элемент = Элементы.Найти("Номер");
        Если Элемент <> Неопределено Тогда
          Проба_4_57 = Элемент;
          Использование(Проба_4_57);
          Проба_4_57_Видимость = Элемент.Видимость;
          Использование(Проба_4_57_Видимость);
        КонецЕсли;
      КонецПроцедуры

      Процедура Использование(Значение)
        Возврат;
      КонецПроцедуры
      """);

    // when
    var types = SpecProbes.typeOfVariable(typeService, documentContext, "Проба_4_57");
    var visibility = SpecProbes.typeOfVariable(typeService, documentContext, "Проба_4_57_Видимость");

    // then: поведение из рекомендации (строки 1702-1704) — после проверки на Неопределено
    // у элемента читают «Видимость».
    assertThat(names(types))
      .as("рекомендация: «Элементы.Найти» даёт элемент формы")
      .containsExactly("ПолеФормы");
    assertThat(names(visibility))
      .as("рекомендация: «Элемент.Видимость = Ложь»")
      .containsExactly("Булево");
  }

  /**
   * «Ограничение на использование реквизитов формы с типом "Произвольный"»: «Если заменить
   * реквизит с типом {@code Произвольный} нет возможности, следует использовать
   * функцию-конструктор для инициализации значения и дальнейших ссылок на типы».
   * <pre>
   * // Возвращаемое значение:
   * // см. НовыйСложныйОбъектДанных
   * Функция РеквизитПроизвольный()
   *     Возврат РеквизитПроизвольный;
   * КонецФункции
   * </pre>
   */
  @Test
  @DisplayName("4.59 Реквизит формы «Произвольный» типизируется функцией-получателем")
  void arbitraryFormAttributeThroughGetter() {
    // given
    var documentContext = formModuleWith("""
      // Возвращаемое значение:
      //  Структура:
      //  * Ссылка - СправочникСсылка.Справочник1
      &НаКлиенте
      Функция НовыйСложныйОбъектДанных()
        Возврат Новый Структура;
      КонецФункции

      // Возвращаемое значение:
      //  см. НовыйСложныйОбъектДанных
      &НаКлиенте
      Функция РеквизитПроизвольный()
        Возврат РеквизитПроизвольный;
      КонецФункции

      &НаКлиенте
      Процедура Тест()
        РеквизитПроизвольный = НовыйСложныйОбъектДанных();
        Проба_4_59 = РеквизитПроизвольный();
        Использование(Проба_4_59);
      КонецПроцедуры

      Процедура Использование(Значение)
        Возврат;
      КонецПроцедуры
      """);

    // when
    var types = SpecProbes.typeOfVariable(typeService, documentContext, "Проба_4_59");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).containsExactly("Ссылка");
  }

  /**
   * «Ограничение на использование реквизитов формы с типом "Произвольный"»: «не следует напрямую
   * обращаться к реквизиту — для получения значения следует использовать функцию-получатель».
   * <pre>
   * // Инициализация реквизита через функцию-конструктор
   * РеквизитПроизвольный = НовыйСложныйОбъектДанных();
   * // Обращение к значению в реквизите с произвольным типом
   * Ссылка = РеквизитПроизвольный().Ссылка;
   * </pre>
   */
  @Test
  @DisplayName("4.60 Обращение к произвольному реквизиту идёт через функцию-получатель")
  void arbitraryFormAttributeFieldThroughGetter() {
    // given
    var documentContext = formModuleWith("""
      // Возвращаемое значение:
      //  Структура:
      //  * Ссылка - СправочникСсылка.Справочник1
      &НаКлиенте
      Функция НовыйСложныйОбъектДанных()
        Возврат Новый Структура;
      КонецФункции

      // Возвращаемое значение:
      //  см. НовыйСложныйОбъектДанных
      &НаКлиенте
      Функция РеквизитПроизвольный()
        Возврат РеквизитПроизвольный;
      КонецФункции

      &НаКлиенте
      Процедура Тест()
        РеквизитПроизвольный = НовыйСложныйОбъектДанных();
        Проба_4_60 = РеквизитПроизвольный().Ссылка;
        Использование(Проба_4_60);
      КонецПроцедуры

      Процедура Использование(Значение)
        Возврат;
      КонецПроцедуры
      """);

    // when
    var types = SpecProbes.typeOfVariable(typeService, documentContext, "Проба_4_60");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  private DocumentContext formModuleWith(String content) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, FORM_MODULE));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }
}
