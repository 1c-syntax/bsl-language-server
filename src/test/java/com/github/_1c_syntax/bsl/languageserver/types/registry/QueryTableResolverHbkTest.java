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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListField;
import com.github._1c_syntax.bsl.mdo.support.DynamicListFieldKind;
import com.github._1c_syntax.bsl.mdo.support.DynamicListKeyType;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поля таблиц языка запросов складываются из двух половин: платформенную
 * ({@code Представление}, {@code МоментВремени}, поля виртуальных таблиц) знает
 * только синтакс-помощник, поэтому тест требует установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (платформенные поля таблиц берутся из bsl-context)")
class QueryTableResolverHbkTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private QueryTableResolver resolver;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private DynamicListTypesRegistrar dynamicListTypes;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_METADATA));
    context.getConfiguration();
    provider.tryRegister();
  }

  @Test
  void catalogTableHasPlatformFieldsAndOwnAttributes() {
    // when
    var fields = resolver.fields("Catalog.Справочник1");

    // then
    assertThat(names(fields))
      .as("стандартные реквизиты, псевдополя таблицы и собственные реквизиты справочника")
      .contains("Ссылка", "Код", "Наименование", "ПометкаУдаления", "Предопределенный",
        "ИмяПредопределенныхДанных", "ВерсияДанных", "Представление", "Реквизит1")
      .as("общий реквизит, у которого этот справочник исключён из состава, полем не стал")
      .doesNotContain("ОбщийРеквизит1")
      .as("методов и событий у таблицы нет — только поля")
      .doesNotContain("Метаданные", "ПолучитьОбъект");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .as("шаблонный тип поля материализуется именем объекта из имени таблицы")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void documentTableHasPointInTimeAndACommonAttribute() {
    // when
    var fields = resolver.fields("Document.Документ1");

    // then
    assertThat(names(fields))
      .as("МоментВремени объявляет только платформа: в метаданных документа его нет")
      .contains("МоментВремени", "Дата", "Номер", "Проведен", "Ссылка", "Представление")
      .as("общий реквизит, в состав которого документ включён, — тоже поле таблицы")
      .contains("ОбщийРеквизит1");
  }

  @Test
  void informationRegisterTableHasDimensionsAndPlatformFields() {
    // when
    var fields = resolver.fields("InformationRegister.РегистрСведений1");

    // then
    assertThat(names(fields))
      .as("измерения — из метаданных, Активность и МоментВремени — от платформы")
      .contains("Справочник1", "Активность", "МоментВремени", "Период", "Регистратор", "НомерСтроки");
  }

  @Test
  void externalDataSourceTableHasItsFieldsAndPlatformOnes() {
    // when
    var fields = resolver.fields("ExternalDataSource.ВнешнийИсточникДанных1.Table.Таблица1");

    // then
    assertThat(names(fields))
      .as("поля таблицы — из метаданных, Ссылка и Представление объявляет платформа")
      .contains("Поле1", "Ссылка", "Представление");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .as("ссылка на элемент таблицы внешнего источника")
      .containsExactly("ВнешнийИсточникДанныхТаблицаСсылка.ВнешнийИсточникДанных1.Таблица1");
  }

  @Test
  void externalDataSourceCubeTableHasDimensionsAndResources() {
    // when
    var fields = resolver.fields("ExternalDataSource.ВнешнийИсточникДанных1.Cube.Куб1");

    // then
    assertThat(names(fields))
      .as("поля таблицы куба — его измерения и ресурсы")
      .contains("Измерение1")
      .as("ни ссылки, ни представления у куба нет — таблица не объектная")
      .doesNotContain("Ссылка", "Представление");
  }

  @Test
  void nonobjectExternalTableHasNeitherRefNorPresentation() {
    // when
    var fields = resolver.fields("ExternalDataSource.ВнешнийИсточникДанных1.Table.Таблица2");

    // then
    assertThat(names(fields))
      .as("поля таблицы на месте")
      .contains("Поле1")
      .as("ссылку и представление платформа даёт только объектной таблице")
      .doesNotContain("Ссылка", "Представление");
  }

  @Test
  void externalDataSourceDimensionTableIsFoundByItsWholeName() {
    // when
    var fields = resolver.fields(
      "ExternalDataSource.ВнешнийИсточникДанных1.Cube.Куб1.DimensionTable.ТаблицаИзмерения1");

    // then
    assertThat(names(fields))
      .as("имя таблицы измерения состоит из трёх пар «вид.имя»")
      .contains("Поле1", "Ссылка", "Представление")
      .as("родитель есть только у иерархической таблицы измерения")
      .doesNotContain("Родитель");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .containsExactly(
        "ВнешнийИсточникДанныхКубТаблицаИзмеренияСсылка.ВнешнийИсточникДанных1.Куб1.ТаблицаИзмерения1");
  }

  @Test
  void hierarchicalDimensionTableHasAParent() {
    // when
    var fields = resolver.fields(
      "ExternalDataSource.ВнешнийИсточникДанных1.Cube.Куб3.DimensionTable.ТаблицаИзмерения2");

    // then
    assertThat(names(fields)).contains("Родитель");
    assertThat(qualifiedNames(field(fields, "Родитель")))
      .as("родитель — ссылка на элемент той же таблицы")
      .containsExactly(
        "ВнешнийИсточникДанныхКубТаблицаИзмеренияСсылка.ВнешнийИсточникДанных1.Куб3.ТаблицаИзмерения2");
  }

  @Test
  void sliceTableDropsFieldsThatTheSliceHasNot() {
    // when
    var fields = resolver.fields("InformationRegister.РегистрСведений1.SliceLast");

    // then
    assertThat(names(fields))
      .as("измерения у среза есть")
      .contains("Справочник1", "Период")
      .as("а момента времени у среза нет — в отличие от самого регистра")
      .doesNotContain("МоментВремени");
  }

  @Test
  void virtualTableIsFoundByTheNameTheConfiguratorWrites() {
    // when
    // Задачи по исполнителю платформа переименовала в TasksByPerformer, а
    // конфигуратор пишет прежнее имя.
    var fields = resolver.fields("Task.Задача1.TasksByExecutive");

    // then
    assertThat(names(fields))
      .as("таблица нашлась, и её платформенные поля на месте")
      .contains("Ссылка", "Выполнена", "Наименование", "Представление");
  }

  @Test
  void customQueryFieldsComeFromItsSelection() {
    // given
    var list = customQuery("""
      ВЫБРАТЬ
        Спр.Ссылка КАК Ссылка,
        Спр.Реквизит1,
        Спр.Наименование КАК Имя,
        ВЫБОР КОГДА Спр.ПометкаУдаления ТОГДА 1 ИНАЧЕ 0 КОНЕЦ КАК Пометка,
        ВЫБОР КОГДА Спр.ПометкаУдаления ТОГДА 1 ИНАЧЕ 0 КОНЕЦ
      ИЗ
        Справочник.Справочник1 КАК Спр""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields))
      .as("имя поля — из алиаса, а без него из имени колонки")
      .containsExactly("Ссылка", "Реквизит1", "Имя", "Пометка");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .as("тип поля — у таблицы из ИЗ")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(field(fields, "Пометка").returnTypes().isEmpty())
      .as("у вычисляемого поля типа нет")
      .isTrue();
  }

  @Test
  void fieldTypeIsResolvedThroughTheWholeChain() {
    // given
    // Каждое звено после источника — свойство типа предыдущего.
    var list = customQuery("""
      ВЫБРАТЬ
        Спр.Ссылка КАК Ссылка,
        Спр.Ссылка.Код КАК КодПоСсылке,
        Спр.Ссылка.НетТакогоПоля КАК Ерунда
      ИЗ
        Справочник.Справочник1 КАК Спр""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(qualifiedNames(field(fields, "КодПоСсылке")))
      .as("код берётся у типа предыдущего звена, а не у таблицы источника")
      .containsExactly("Строка");
    assertThat(field(fields, "Ерунда").returnTypes().isEmpty())
      .as("несуществующее звено обрывает цепочку")
      .isTrue();
  }

  @Test
  void chainWithoutAnAliasIsNamedByItsSegments() {
    // given
    // Псевдоним в полях выборки не обязателен: путь вглубь платформа называет
    // склейкой звеньев — Ссылка.Код становится колонкой СсылкаКод (RefCode).
    var list = customQuery("""
      ВЫБРАТЬ
        Спр.Наименование,
        Спр.Ссылка.Код
      ИЗ
        Справочник.Справочник1 КАК Спр""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields)).containsExactly("Наименование", "СсылкаКод");
    assertThat(field(fields, "СсылкаКод").bilingualName().en())
      .as("склейка двуязычна: у обоих звеньев есть английские имена")
      .isEqualTo("RefCode");
    assertThat(qualifiedNames(field(fields, "СсылкаКод")))
      .as("тип — у последнего звена")
      .containsExactly("Строка");
  }

  @Test
  void columnWithoutASourceBelongsToTheOnlyOneOfTheQuery() {
    // given
    var list = customQuery("ВЫБРАТЬ Ссылка, Наименование ИЗ Справочник.Справочник1");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields)).containsExactly("Ссылка", "Наименование");
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .as("источник у запроса один, и колонка принадлежит ему")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void sourceWithoutAnAliasIsAddressedByItsOwnName() {
    // given
    var list = customQuery("""
      ВЫБРАТЬ
        Справочник.Справочник1.Ссылка КАК Ссылка
      ИЗ
        Справочник.Справочник1""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(qualifiedNames(field(fields, "Ссылка")))
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void asteriskSelectsAllFieldsOfItsTable() {
    // given
    var list = customQuery("ВЫБРАТЬ Спр.* ИЗ Справочник.Справочник1 КАК Спр");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields))
      .contains("Ссылка", "Код", "Наименование", "Реквизит1", "Представление");
  }

  @Test
  void queryOverAJoinTypesFieldsOfBothSources() {
    // given
    var list = customQuery("""
      ВЫБРАТЬ
        Спр.Ссылка КАК Ссылка,
        Док.Дата КАК Дата
      ИЗ
        Справочник.Справочник1 КАК Спр
        ЛЕВОЕ СОЕДИНЕНИЕ Документ.Документ1 КАК Док
        ПО Спр.Ссылка = Док.Ссылка""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields)).containsExactly("Ссылка", "Дата");
    assertThat(qualifiedNames(field(fields, "Ссылка"))).containsExactly("СправочникСсылка.Справочник1");
    assertThat(qualifiedNames(field(fields, "Дата"))).containsExactly("Дата");
  }

  @Test
  void dataCompositionBracesDoNotBreakTheSelection() {
    // given
    // Запросы динамических списков набиты блоками компоновки в фигурных скобках.
    var list = customQuery("""
      ВЫБРАТЬ
        Спр.Ссылка КАК Ссылка,
        Спр.Наименование КАК Наименование
      ИЗ
        Справочник.Справочник1 КАК Спр
      {ГДЕ
        Спр.Наименование КАК Наименование}""");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields)).containsExactly("Ссылка", "Наименование");
  }

  @Test
  void fieldCompositionKeepsItsNameAndTakesTypeFromTheQuery() {
    // given
    // Состав полей называет поле, но тип у записи состава есть редко —
    // тип приходит от следующего источника, разобравшего запрос.
    var list = FormDynamicListAttribute.builder()
      .name("Список")
      .customQuery(true)
      .queryText("ВЫБРАТЬ Спр.Ссылка КАК Ссылка ИЗ Справочник.Справочник1 КАК Спр")
      .addFields(FormDynamicListField.builder().dataPath("Ссылка").name("Ссылка").build())
      .build();

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields)).containsExactly("Ссылка");
    assertThat(qualifiedNames(field(fields, "Ссылка"))).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  void unparsableQueryGivesNoFields() {
    // given
    var list = customQuery("ВЫБРАТЬ ИЗ ГДЕ");

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(fields).isEmpty();
  }

  @Test
  void pathFieldWithAFlatNameBecomesAColumnTypedByItsPath() {
    // given
    // Колонка называется именем поля состава. Если имя плоское, а путь ведёт
    // вглубь, тип берётся у последнего звена пути; если имя само записано
    // путём, плоской колонки нет — к такому полю обращаются через владельца.
    var list = FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable("Catalog.Справочник1")
      .addFields(FormDynamicListField.builder()
        .dataPath("Ссылка.Код").name("КодПоСсылке").build())
      .addFields(FormDynamicListField.builder()
        .dataPath("Ссылка.Наименование").name("Ссылка.Наименование").build())
      .build();

    // when
    var fields = resolver.fields("Catalog.Справочник1", list);

    // then
    assertThat(names(fields))
      .as("колонка называется именем поля; имя-путь плоской колонкой не становится")
      .contains("КодПоСсылке")
      .doesNotContain("Ссылка.Наименование");
    assertThat(qualifiedNames(field(fields, "КодПоСсылке"))).containsExactly("Строка");
  }

  @Test
  void nestedDataSetIsNotAColumn() {
    // given
    // Вложенный набор данных — группа, под которой лежат поля с составным
    // путём; колонкой строки не является ни он сам, ни они.
    var list = FormDynamicListAttribute.builder()
      .name("Список")
      .customQuery(true)
      .queryText("ВЫБРАТЬ Спр.Ссылка КАК Ссылка ИЗ Справочник.Справочник1 КАК Спр")
      .addFields(FormDynamicListField.builder()
        .kind(DynamicListFieldKind.NESTED_DATA_SET).dataPath("Планы").name("Планы").build())
      .addFields(FormDynamicListField.builder()
        .dataPath("Планы.ЦФО").name("Планы.ЦФО").build())
      .addFields(FormDynamicListField.builder()
        .kind(DynamicListFieldKind.FOLDER).dataPath("Папка").name("Папка").build())
      .build();

    // when
    var fields = resolver.fields("", list);

    // then
    assertThat(names(fields))
      .containsExactly("Ссылка")
      .doesNotContain("Планы", "ЦФО", "Папка");
  }

  @Test
  void rowIsIdentifiedByTheDeclaredKey() {
    // given
    // Чем список адресует строку, задаёт вид ключа; по умолчанию это ссылка
    // основной таблицы.
    var byRowNumber = listWithKey(DynamicListKeyType.ROW_NUMBER, List.of());
    var byRowKey = listWithKey(DynamicListKeyType.ROW_KEY, List.of("Ссылка", "Код"));
    var byFieldValue = listWithKey(DynamicListKeyType.FIELD_VALUE, List.of("Код"));
    var byTwoFields = listWithKey(DynamicListKeyType.FIELD_VALUE, List.of("Код", "Наименование"));
    var byDefault = listWithKey(DynamicListKeyType.AUTO, List.of());

    // when
    var rows = dynamicListTypes.prepareRows(
      List.of(byRowNumber, byRowKey, byFieldValue, byTwoFields, byDefault), "Тест");

    // then
    assertThat(rows).hasSize(5);
    assertThat(qualifiedName(rows, "поНомеруСтроки")).isEqualTo("Число");
    assertThat(qualifiedName(rows, "поКлючуСтроки")).isEqualTo("КлючСтрокиДинамическогоСписка");
    assertThat(qualifiedName(rows, "поЗначениюПоля"))
      .as("тип поля ключа — тот же, что у одноимённой колонки")
      .isEqualTo("Строка");
    assertThat(rows.get("подвумполям").rowIdRef())
      .as("чем платформа адресует строку по нескольким полям, не объявлено")
      .isNull();
    assertThat(qualifiedName(rows, "поумолчанию")).isEqualTo("СправочникСсылка.Справочник1");
  }

  @Test
  void unknownTableHasNoFields() {
    assertThat(resolver.fields("1:0d7c2c47-4b5e-4b0e-8d1a-000000000000")).isEmpty();
  }

  @Test
  void dynamicListRowGetsFieldsOfItsQueryTable() {
    // given
    // Колонки списка в форме не объявлены: за ним стоит основная таблица, и
    // колонками строки служат её поля — включая те, которых у ссылочного типа
    // объекта нет вовсе.
    var itemsType = typeRegistry.resolve("ВсеЭлементыФормы.Справочник.Справочник1.Форма.ФормаСписка")
      .orElseThrow();
    var tableRef = typeRegistry.getMembers(itemsType, FileType.BSL).stream()
      .filter(member -> member.matches("Список"))
      .findFirst()
      .orElseThrow()
      .returnTypes().refs().iterator().next();
    var currentData = typeRegistry.getMembers(tableRef, FileType.BSL).stream()
      .filter(member -> member.matches("ТекущиеДанные"))
      .findFirst()
      .orElseThrow();

    // when
    var rowRef = currentData.returnTypes().refs().iterator().next();

    // then
    assertThat(names(typeRegistry.getMembers(rowRef, FileType.BSL)))
      .contains("Ссылка", "Код", "Наименование", "Реквизит1")
      .as("псевдополя таблицы — то, чего у ссылочного типа справочника нет")
      .contains("Представление", "ВерсияДанных")
      .as("стандартный реквизит строки списка — картинка, на неё ссылается оформление строк")
      .contains("СтандартнаяКартинка");
    assertThat(typeRegistry.getMembers(rowRef, FileType.BSL))
      .as("картинка строки находится и по английскому написанию — им её пишет форма")
      .anyMatch(member -> member.matches("DefaultPicture"));

    // Обращение через точку (`ТекущиеДанные.Ссылка.Код`, элемент формы с путём
    // `Список.Ссылка.Код`) состава полей не требует: оно идёт по типу колонки.
    var refColumn = typeRegistry.getMembers(rowRef, FileType.BSL).stream()
      .filter(member -> member.matches("Ссылка"))
      .findFirst()
      .orElseThrow();
    var refType = refColumn.returnTypes().refs().iterator().next();
    assertThat(names(typeRegistry.getMembers(refType, FileType.BSL)))
      .as("у типа колонки есть свои члены, поэтому путь вглубь разрешается")
      .contains("Код", "Наименование");
  }

  private static FormDynamicListAttribute listWithKey(DynamicListKeyType keyType, List<String> keyFields) {
    var names = Map.of(
      DynamicListKeyType.ROW_NUMBER, "ПоНомеруСтроки",
      DynamicListKeyType.ROW_KEY, "ПоКлючуСтроки",
      DynamicListKeyType.FIELD_VALUE, keyFields.size() == 1 ? "ПоЗначениюПоля" : "ПоДвумПолям",
      DynamicListKeyType.AUTO, "ПоУмолчанию");
    return FormDynamicListAttribute.builder()
      .name(names.get(keyType))
      .mainTable("Catalog.Справочник1")
      .keyType(keyType)
      .keyFields(keyFields)
      .build();
  }

  private static String qualifiedName(Map<String, DynamicListTypesRegistrar.DynamicList> rows, String listName) {
    var rowIdRef = rows.get(listName.toLowerCase(Locale.ROOT)).rowIdRef();
    assertThat(rowIdRef).as("идентификатор строки списка %s", listName).isNotNull();
    return rowIdRef.qualifiedName();
  }

  private static FormDynamicListAttribute customQuery(String queryText) {
    return FormDynamicListAttribute.builder()
      .name("Список")
      .customQuery(true)
      .queryText(queryText)
      .build();
  }

  private static List<String> names(Collection<MemberDescriptor> members) {
    return members.stream().map(MemberDescriptor::name).toList();
  }

  private static MemberDescriptor field(List<MemberDescriptor> members, String name) {
    return members.stream()
      .filter(member -> member.name().equals(name))
      .findFirst()
      .orElseThrow(() -> new AssertionError("поле не найдено: " + name));
  }

  private static List<String> qualifiedNames(MemberDescriptor member) {
    return member.returnTypes().refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
