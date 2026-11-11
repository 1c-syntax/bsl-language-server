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
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Метод {@code Свойство(Ключ, Приёмник)} кладёт значение ключа во <b>второй параметр</b>,
 * а возвращает признак «нашлось ли». Обычным выводом типа выражения такое присваивание
 * не видно: справа от знака равенства переменная-приёмник не стоит ни разу.
 */
@CleanupContextBeforeClassAndAfterClass
class PropertyMethodInferenceTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String DOCUMENT_FORM_MODULE =
    "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";
  private static final String LIST_FORM_MODULE =
    "Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  @Autowired
  private ReferenceResolver referenceResolver;

  @Test
  void keyReadIntoOutParameterTypesIt() {
    // Самый частый способ записи: вызов стоит в условии, а не отдельным оператором.
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура;
        Структура.Вставить("Количество", 1);
        Значение = Неопределено;
        Если Структура.Свойство("Количество", Значение) Тогда
          Итог = Значение;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог"))
      .as("внутри условия ключ уже нашёлся — лежит его значение")
      .containsExactly("Число");
  }

  @Test
  void branchesSeeTheValueAndTheAbsence() {
    var documentContext = moduleWith("""
      Процедура Тест()

          Структура = Новый Структура("Ключ", 1);

          Приемник = "А";

          Если Структура.Свойство("Ключ", Приемник) Тогда
              А = Приемник;
          Иначе
              Б = Приемник;
          КонецЕсли;

          В = Приемник;

      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "А")).as("истинная ветка — значение ключа")
      .containsExactly("Число");
    assertThat(typesOf(documentContext, "Б")).as("ложная ветка — ключа нет")
      .containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "В")).as("после слияния — обе возможности")
      .containsExactlyInAnyOrder("Число", "Неопределено");

    // Путь hover'а: он спрашивает не выражение, а ссылку на переменную.
    assertThat(typesAtReference(documentContext, "А")).as("hover, истинная ветка")
      .containsExactly("Число");
    assertThat(typesAtReference(documentContext, "Б")).as("hover, ложная ветка")
      .containsExactly("Неопределено");
    assertThat(typesAtReference(documentContext, "В")).as("hover, после условия")
      .containsExactlyInAnyOrder("Число", "Неопределено");
  }

  /** Типы правой части так, как их видит hover: через ссылку, а не через выражение. */
  private List<String> typesAtReference(DocumentContext documentContext, String assignedVar) {
    var position = caretAt(documentContext, assignedVar);
    var reference = referenceResolver.findReference(documentContext.getUri(), position).orElseThrow(
      () -> new AssertionError("ссылка не найдена в " + position));
    return typeService.typesAt(reference).refs().stream().map(TypeRef::qualifiedName).toList();
  }

  /**
   * Вложенная проверка той же переменной внутри ветки не должна ломать слияние за внешним
   * {@code КонецЕсли}. Правая часть каждого присваивания считается изнутри расчёта по
   * потоку, и пока он не сошёлся, тип в точке слияния — приближение; оседая в кэше типов
   * выражений, оно подменяло собой посчитанный ответ.
   */
  @Test
  void nestedCheckInsideTheTrueBranchDoesNotLeakPastTheOuterIf() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Ключ", 1);
        Приемник = "А";
        Если Структура.Свойство("Ключ", Приемник) Тогда
          А = Приемник;
          Если Приемник = Неопределено Тогда
            Г = Приемник;
          КонецЕсли;
          Е = Приемник;
        Иначе
          Б = Приемник;
        КонецЕсли;
        В = Приемник;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "А")).containsExactly("Число");
    assertThat(typesOf(documentContext, "Г")).containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "Е")).as("после вложенного КонецЕсли")
      .containsExactlyInAnyOrder("Число", "Неопределено");
    assertThat(typesOf(documentContext, "Б")).containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "В")).containsExactlyInAnyOrder("Число", "Неопределено");
  }

  @Test
  void nestedCheckInsideTheFalseBranchDoesNotLeakPastTheOuterIf() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Ключ", 1);
        Приемник = "А";
        Если Структура.Свойство("Ключ", Приемник) Тогда
          А = Приемник;
        Иначе
          Б = Приемник;
          Если Приемник <> Неопределено Тогда
            Д = Приемник;
          КонецЕсли;
        КонецЕсли;
        В = Приемник;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "А")).containsExactly("Число");
    assertThat(typesOf(documentContext, "Б")).containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "В")).containsExactlyInAnyOrder("Число", "Неопределено");
  }

  @Test
  void sameNamedReceiversInDifferentMethodsDoNotCrossMatch() {
    // Индекс вызовов ищет по имени переменной, а имена в разных методах повторяются
    // сплошь и рядом. Каждый метод должен видеть только свои вызовы.
    var documentContext = moduleWith("""
      Процедура Первая() Экспорт
        Структура = Новый Структура("Ключ", 1);
        Приемник = "А";
        Структура.Свойство("Ключ", Приемник);
        ВПервой = Приемник;
      КонецПроцедуры

      Процедура Вторая() Экспорт
        Структура = Новый Структура("Ключ", Истина);
        Приемник = "Б";
        ВоВторойДоВызова = Приемник;
        Структура.Свойство("Ключ", Приемник);
        ВоВторой = Приемник;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "ВПервой")).as("первый метод — свой ключ")
      .containsExactly("Число");
    assertThat(typesOf(documentContext, "ВоВторойДоВызова"))
      .as("вызов из чужого метода не должен типизировать здесь")
      .containsExactly("Строка");
    assertThat(typesOf(documentContext, "ВоВторой")).as("второй метод — свой ключ")
      .containsExactly("Булево");
  }

  @Test
  void keyBuiltInAVariableIsUnknownOnBothEnds() {
    // Имя ключа, собранное в переменной, статически неизвестно — и конструктору структуры,
    // и чтению. Состав не сложился, читать нечего: приёмник остаётся собой.
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Приемник = "А";
        Ключ = "Ключ";
        Структура = Новый Структура(Ключ, 1);
        Если Структура.Свойство(Ключ, Приемник) Тогда
          НаИстиннойВетке = Приемник;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "НаИстиннойВетке")).containsExactly("Строка");
  }

  @Test
  void keyHoldingUndefinedStillReturnsTrue() {
    // Ключ, положенный со значением Неопределено, находится: `Свойство` вернёт Истина, а в
    // приёмнике будет Неопределено. Поэтому убирать его с истинной ветки нельзя.
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура;
        Структура.Вставить("Пустой", Неопределено);
        Значение = 0;
        Если Структура.Свойство("Пустой", Значение) Тогда
          НаИстиннойВетке = Значение;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "НаИстиннойВетке"))
      .as("значение ключа — Неопределено, и на истинной ветке оно остаётся")
      .containsExactly("Неопределено");
  }

  @Test
  void negatedConditionSwapsTheBranches() {
    // Ранний выход — самая частая запись: `Если НЕ Свойство(…) Тогда Возврат; КонецЕсли;`
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Количество", 1);
        Значение = Неопределено;
        Если НЕ Структура.Свойство("Количество", Значение) Тогда
          ВОхране = Значение;
          Возврат;
        КонецЕсли;
        ПослеОхраны = Значение;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "ВОхране"))
      .as("под отрицанием информативна уже истинная ветка")
      .containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "ПослеОхраны"))
      .as("до сюда доходит только путь, где ключ нашёлся")
      .containsExactly("Число");
  }

  @Test
  void keysFromLiteralConstructorAreSeenToo() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Параметры = Новый Структура("Ссылка", Документы.Документ1.ПустаяСсылка());
        Ссылка = Неопределено;
        Параметры.Свойство("Ссылка", Ссылка);
        Итог = Ссылка;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог"))
      .containsExactly("ДокументСсылка.Документ1");
  }

  @Test
  void typingStartsAtTheCallAndNotBefore() {
    // Изменение на месте: до вызова переменная своего нового типа ещё не имеет.
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Количество", 1);
        Значение = Неопределено;
        ДоВызова = Значение;
        Структура.Свойство("Количество", Значение);
        ПослеВызова = Значение;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "ДоВызова"))
      .as("до вызова приёмник — то, чем его объявили")
      .containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "ПослеВызова")).containsExactly("Число");
  }

  @Test
  void englishSpellingWorksTheSame() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Количество", 1);
        Значение = Неопределено;
        Структура.Property("Количество", Значение);
        Итог = Значение;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог")).containsExactly("Число");
  }

  @Test
  void unknownKeyAndComputedArgumentsLeaveTheTypeAlone() {
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Структура = Новый Структура("Количество", 1);
        ИмяКлюча = "Количество";
        НетТакогоКлюча = Неопределено;
        Структура.Свойство("НетТакого", НетТакогоКлюча);
        ИтогНетКлюча = НетТакогоКлюча;
        КлючИзПеременной = Неопределено;
        Структура.Свойство(ИмяКлюча, КлючИзПеременной);
        ИтогКлючИзПеременной = КлючИзПеременной;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "ИтогНетКлюча"))
      .as("выдумывать тип по несуществующему ключу нельзя")
      .containsExactly("Неопределено");
    assertThat(typesOf(documentContext, "ИтогКлючИзПеременной"))
      .as("ключ собран в переменной — статически неизвестен")
      .containsExactly("Неопределено");
  }

  @Test
  void memberOfUnsupportedPartOfMixedReceiverIsNotRead() {
    // Получатель — union структуры и документа. `Реквизит1` объявлен только у документа,
    // а у него метода `Свойство` нет вовсе: класть его тип в приёмник нельзя. У структуры
    // же про такой ключ ничего не известно — значит, приёмник остаётся собой.
    var documentContext = moduleWith("""
      Процедура Тест(Условие) Экспорт
        Если Условие Тогда
          Получатель = Новый Структура;
        Иначе
          Получатель = Документы.Документ1.СоздатьДокумент();
        КонецЕсли;
        Приемник = 0;
        Если Получатель.Свойство("Реквизит1", Приемник) Тогда
          НаИстиннойВетке = Приемник;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "НаИстиннойВетке")).containsExactly("Число");
  }

  @Test
  void receiverThatIsNotStructureLikeIsLeftAlone() {
    // `Свойство` есть и у других типов, но именованный ключ читают только структуроподобные.
    var documentContext = moduleWith("""
      Процедура Тест() Экспорт
        Массив = Новый Массив;
        Значение = Неопределено;
        Массив.Свойство("Количество", Значение);
        Итог = Значение;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог")).containsExactly("Неопределено");
  }

  @Test
  void formDataStructureGivesItsAttributeTypes() {
    // У данных формы состав объявлен типом, а не накоплен по коду: ключ читается
    // из членов `ДанныеФормыСтруктура.ДокументОбъект.Документ1`.
    var documentContext = formModuleWith("""
      &НаКлиенте
      Процедура Тест()
        Значение = Неопределено;
        Если Объект.Свойство("Реквизит1", Значение) Тогда
          Итог = Значение;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог"))
      .as("тип реквизита документа, объявленный в метаданных")
      .containsExactly("Строка");
  }

  @Test
  void dynamicListRowGivesDereferencedColumnByItsCompositeKey() {
    // Разыменованную колонку строки списка через точку не прочитать — имя у неё
    // составное. Читают её строковым ключом, и тип он даёт такой же.
    var documentContext = documentWith(LIST_FORM_MODULE, """
      &НаКлиенте
      Процедура Тест()
        Значение = Неопределено;
        ТекущиеДанные = Элементы.Список.ТекущиеДанные;
        Если ТекущиеДанные.Свойство("Ссылка.Код", Значение) Тогда
          Итог = Значение;
        КонецЕсли;
      КонецПроцедуры
      """);

    assertThat(typesOf(documentContext, "Итог"))
      .as("тип последнего звена разыменования")
      .containsExactly("Строка");
  }

  private DocumentContext moduleWith(String content) {
    return documentWith(MODULE, content);
  }

  private DocumentContext formModuleWith(String content) {
    return documentWith(DOCUMENT_FORM_MODULE, content);
  }

  private DocumentContext documentWith(String path, String content) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, path));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }

  /** Имена типов правой части присваивания {@code <var> = <переменная>;}. */
  private List<String> typesOf(DocumentContext documentContext, String assignedVar) {
    return typeService.expressionTypesAt(documentContext, caretAt(documentContext, assignedVar))
      .refs().stream().map(TypeRef::qualifiedName).toList();
  }

  /** Позиция начала правой части присваивания {@code <var> = <переменная>;}. */
  private static Position caretAt(DocumentContext documentContext, String assignedVar) {
    var content = documentContext.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx).as("маркер `%s` не найден", marker).isGreaterThanOrEqualTo(0);
    var caret = markerIdx + marker.length();
    var lineStart = content.lastIndexOf('\n', caret) + 1;
    var line = content.substring(0, caret).split("\n").length - 1;
    return new Position(line, caret - lineStart);
  }
}
