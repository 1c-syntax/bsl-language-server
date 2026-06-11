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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import org.eclipse.lsp4j.Location;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вывод типа внедряемых через {@code &Пластилин} зависимостей фреймворка «ОСень».
 */
@CleanupContextBeforeClassAndAfterClass
class AutumnDependencyInjectionInferenceTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/autumn-di").toAbsolutePath();

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private TypeService typeService;

  private DocumentContext consumer;

  @BeforeEach
  void setup() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
    var uri = Absolute.uri(FIXTURE_ROOT.resolve("src/Приложение.os").toUri());
    consumer = context.getDocument(uri);
  }

  @Test
  void infersBeanTypeForFieldWithExplicitName() {
    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ВнедренныйПоИмени")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeForFieldByVariableName() {
    // given
    // поле объявлено как `&Пластилин Перем Логгер;` — имя желудя берётся из имени поля

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("Логгер")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeForConstructorParameter() {
    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ЛоггерИзКонструктора")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeByQualifier() {
    // given
    // желудь Василий объявлен с &Прозвище("Васян")

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ЧерезПрозвище")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Василий");
  }

  @Test
  void prefersPrimaryBeanOnQualifierConflict() {
    // given
    // прозвище "Панк" есть у ДжонниРоттен и СидВишес; СидВишес помечен &Верховный

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ПриоритетныйПанк")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("СидВишес");
  }

  @Test
  void infersBeanTypeForRenamedComponent() {
    // given
    // РеальныйКласс объявлен как &Желудь("ЛогическоеИмя")

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("Переименованный")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("РеальныйКласс");
  }

  @Test
  void infersBeanTypeForFactoryMethod() {
    // given
    // &Завязь(Значение = "ФабричныйЖелудь", Тип = "Логгер")

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ИзФабрики")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeThroughMetaAnnotations() {
    // given
    // поле помечено &Внедряемое (= &Пластилин через &Аннотация),
    // а желудь СовременныйЛоггер объявлен через &Компонент (= &Желудь)

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ЧерезМетаАннотацию")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("СовременныйЛоггер");
  }

  @Test
  void infersLogBeanTypeForFieldMarkedWithLog() {
    // given
    // поле помечено &Лог("Префикс") (= &Пластилин(Значение="Лог") через &Аннотация);
    // имя желудя зашито в мета-аннотации ("Лог"), а Значение самой &Лог ("Префикс") —
    // это префикс лога, а не имя желудя. Желудь "Лог" даёт фабрика Лог() в дубе
    // ФабрикаЛогов, его тип — класс Лог.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("ЧерезЛог")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Лог");
  }

  @Test
  void infersControllerBeanByClassName() {
    // given
    // МойКонтроллер помечен &Контроллер("/users") (= &Желудь + &Прозвище("Контроллер")).
    // У &Контроллер нет обработчика разворачивания, поэтому имя желудя — имя класса,
    // а "/users" это маршрут, не имя желудя.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КонтроллерПоИмени")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("МойКонтроллер");
  }

  @Test
  void infersControllerBeanByQualifier() {
    // given
    // прозвище "Контроллер" зашито в определении &Контроллер через &Прозвище("Контроллер")

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КонтроллерПоПрозвищу")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("МойКонтроллер");
  }

  @Test
  void doesNotRegisterControllerUnderItsRoute() {
    // given
    // "/users" — маршрут (&Контроллер.Значение), а не имя желудя

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КонтроллерПоМаршруту")));

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  @Test
  void infersCollectionTypeFromGetterDescription() {
    // given: ПрилепляемаяКоллекцияМойСписок объявлена как &ПрилепляемаяКоллекция("МойСписок"),
    // её Получить() в bsldoc возвращает ФиксированныйМассив. &Пластилин(Тип = "МойСписок")
    // должен дать именно ФиксированныйМассив (а не «МойСписок» как имя типа).

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КоллекцияИзОписания")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("ФиксированныйМассив");
  }

  @Test
  void fallsBackToTypeRegistryWhenCollectionIsNotInIndex() {
    // given: реализации прилепляемой коллекции «Массив» в фикстуре нет — индекс
    // её не знает. Имя коллекции совпадает с именем платформенного типа Массив,
    // поэтому срабатывает фоллбэк через TypeRegistry (поведение из issue #3959).

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КоллекцияФоллбэкомПоИмени")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Массив");
  }

  @Test
  void returnsEmptyForUnknownCollectionName() {
    // given: имя коллекции не встречается ни как зарегистрированная реализация,
    // ни как имя типа.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("НеизвестнаяКоллекция")));

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  @Test
  void infersBeanTypeForCompositeAliasWithExplicitValue() {
    // given: &ВнедрениеКомпозит("Логгер") — композит-алиас &Пластилин, чей параметр
    // помечен &ПсевдонимДля(Аннотация="Пластилин", Параметр="Значение"). Переданное
    // "Логгер" декларативно переносится в имя желудя.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КомпозитПоИмени")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void infersBeanTypeForCompositeAliasTransferringDefaultValue() {
    // given: &ВнедрениеПоУмолчанию без значения; псевдоним помечен
    // ПереноситьЗначениеПоУмолчанию = Истина, значение по умолчанию "Логгер".

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КомпозитПоУмолчанию")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Логгер");
  }

  @Test
  void doesNotTransferAliasDefaultValueWithoutOptIn() {
    // given: &ВнедрениеБезПереноса без значения; ПереноситьЗначениеПоУмолчанию не задан
    // (Ложь), значение по умолчанию "Логгер" в &Пластилин НЕ переносится — имя желудя
    // падает на имя переменной "КомпозитБезПереноса", желудя с таким именем нет.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КомпозитБезПереноса")));

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  @Test
  void infersCollectionTypeFixedInMetaAnnotation() {
    // given: &КоллекцияМассив = &Пластилин(Тип = "Массив") — параметр Тип зафиксирован в
    // мета-аннотации (разворачивание работает не только для Значение).

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КоллекцияЧерезФиксированныйТип")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Массив");
  }

  @Test
  void infersCollectionTypeForwardedViaAliasFor() {
    // given: &ВнедрениеКоллекции(ТипКоллекции = "Массив") — ТипКоллекции проброшен в
    // &Пластилин.Тип через &ПсевдонимДля; имя параметра отличается от Тип, поэтому
    // значение доходит именно через механизм псевдонимов.

    // when
    var types = typeService.typesAt(referenceOf(consumer, variable("КоллекцияЧерезПсевдоним")));

    // then
    assertThat(qualifiedNames(types)).containsExactly("Массив");
  }

  @Test
  void injectedParameterResolvesAtUsageAndFlowsIntoField() {
    // given: потребитель внедряет желудь через параметр конструктора и присваивает его полю.
    // Документ грузится со ссылками (как при открытии в редакторе) — без ручного рефилла.
    var path = FIXTURE_ROOT.resolve("src/ПотребительВКонструкторе.os").toString();
    var doc = TestUtils.getDocumentContextFromFile(path, context);
    var lines = doc.getContentList();

    int usageLine = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains("= _Логгер")) {
        usageLine = i;
      }
    }
    assertThat(usageLine).as("строка с использованием параметра найдена").isNotNegative();
    var usagePosition = new Position(usageLine, lines[usageLine].indexOf("_Логгер") + 1);

    // when
    var atUsage = typeService.expressionTypesAt(doc, usagePosition);
    var field = doc.getSymbolTree().getVariables().stream()
      .filter(v -> "Сохранённый".equals(v.getName()))
      .findFirst()
      .orElseThrow();
    var fieldTypes = typeService.typesAt(referenceOf(doc, field));

    // then
    assertThat(qualifiedNames(atUsage))
      .as("использование параметра-желудя в теле конструктора резолвится в тип")
      .containsExactly("Логгер");
    assertThat(qualifiedNames(fieldTypes))
      .as("тип внедряемого желудя протекает в поле через присваивание")
      .containsExactly("Логгер");
  }

  private VariableSymbol variable(String name) {
    return consumer.getSymbolTree().getVariables().stream()
      .filter(v -> name.equals(v.getName()))
      .findFirst()
      .orElseThrow();
  }

  private static List<String> qualifiedNames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }

  private static Reference referenceOf(DocumentContext documentContext, VariableSymbol variableSymbol) {
    return Reference.of(documentContext.getSymbolTree().getModule(), variableSymbol,
      new Location(documentContext.getUri().toString(), variableSymbol.getSelectionRange()), OccurrenceType.DEFINITION);
  }
}
