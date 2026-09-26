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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.LocalField;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAdditionalColumnsAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Типы данных управляемой формы: во что превращается реквизит, объявленный в
 * {@code Form.xml}, и какие типы стоят за табличными частями объекта.
 * <p>
 * На управляемой форме за реквизитом стоит не сам прикладной объект, а его данные:
 * объектные типы становятся {@code ДанныеФормыСтруктура}, наборы записей —
 * {@code ДанныеФормыСтруктураСКоллекцией}, таблица и дерево значений — коллекцией и
 * деревом (см. {@link FormPlatformTypes#formDataKindOf}). Регистратор помнит уже
 * заведённые типы: у всех форм одного документа данные устроены одинаково, поэтому
 * тип заводится на прикладной тип, а не на форму.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class FormDataTypesRegistrar {

  /**
   * Строка табличной коллекции: {@code коллекция → её строка}. Колонки такой коллекции
   * ложатся полями строки, а не самой коллекции, — то же представление, в котором их
   * видит таблица значений, собранная в коде.
   */
  private static final Map<String, String> COLLECTION_ROWS = Map.of(
    "ТаблицаЗначений", CollectionReturnsSpecializer.VALUE_TABLE_ROW,
    "ДеревоЗначений", "СтрокаДереваЗначений");

  /** Хвосты имён типов одного семейства: {@code ДокументОбъект} / {@code ДокументТабличнаяЧасть}. */
  private static final String OBJECT_SUFFIX_RU = "Объект";
  private static final String TABULAR_SECTION_SUFFIX_RU = "ТабличнаяЧасть";

  private final TypeRegistry typeRegistry;

  /**
   * Тип данных формы по объявленному типу реквизита (см. {@link #registerFormData}).
   * Общий на всю рабочую область: данные одного прикладного типа устроены одинаково
   * во всех формах, где он встречается.
   */
  private final Map<TypeRef, TypeRef> formDataTypes = new ConcurrentHashMap<>();

  /**
   * Зеркало табличной части в данных формы: {@code ДокументТабличнаяЧасть.X.Y} →
   * {@code ДанныеФормыКоллекция.…} (см. {@link #registerTabularSectionData}).
   */
  private final Map<TypeRef, TypeRef> tabularSectionData = new ConcurrentHashMap<>();

  /**
   * Строка коллекции данных формы: {@code ДанныеФормыКоллекция.…} →
   * {@code ДанныеФормыЭлементКоллекции.…} с её колонками. Её отдают
   * {@code ТекущиеДанные} и {@code ДанныеСтроки} таблицы над этой коллекцией.
   */
  private final Map<TypeRef, TypeRef> rowByCollection = new ConcurrentHashMap<>();

  /**
   * Колонки зеркала табличной части: {@code ДанныеФормыКоллекция.… → источник колонок}.
   * Нужны, когда форма достраивает табличной части свои колонки: у её коллекции колонки
   * складываются из колонок объекта и добавленных формой.
   */
  private final Map<TypeRef, MemberSource> columnsByCollection = new ConcurrentHashMap<>();

  /**
   * Табличные части, которым конкретная форма добавила свои колонки:
   * {@code реквизит формы → (табличная часть объекта → коллекция этого реквизита)}
   * (ключ — {@link #ownSectionsKey}).
   * <p>
   * Общего зеркала здесь мало: дополнительные колонки объявлены в одной форме и
   * существуют только в её данных, поэтому у такой табличной части своя коллекция
   * и своя строка на каждый реквизит формы, к которому колонки добавлены. Ключ именно
   * реквизит, а не форма: два реквизита одного вида могут добавить одной и той же
   * табличной части разные колонки.
   */
  private final Map<String, Map<TypeRef, TypeRef>> formTabularSectionData = new ConcurrentHashMap<>();

  /**
   * Реквизиты формы как свойства её типа. Тип — результат преобразования объявленного
   * в {@code Form.xml} типа в тип данных формы (см. {@link #prepareAttributeTypes});
   * нерезолвящиеся типы дают свойство без типа — имя в автодополнении всё равно нужно.
   */
  List<MemberDescriptor> buildAttributeMembers(List<FormAttribute> attributes,
                                                       Map<String, TypeSet> attributeTypes) {
    if (attributes.isEmpty()) {
      return List.of();
    }
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var key = name.toLowerCase(Locale.ROOT);
      var types = attributeTypes.getOrDefault(key, TypeSet.EMPTY);
      var descriptor = MemberDescriptor.property(name, types, "")
        .withBilingualName(FormPlatformTypes.neutral(name))
        .withBilingualDescription(FormPlatformTypes.bilingual(attribute.getTitle()));
      byName.putIfAbsent(key, descriptor);
    }
    return List.copyOf(byName.values());
  }

  /**
   * Типы реквизитов формы: {@code имя реквизита (lower) → тип}. Объявленный в
   * {@code Form.xml} прикладной тип заменяется типом данных формы — на управляемой
   * форме за реквизитом стоит не сам объект, а его данные
   * ({@code ДокументОбъект.X} → {@code ДанныеФормыСтруктура}, набор записей →
   * {@code ДанныеФормыСтруктураСКоллекцией}, {@code ТаблицаЗначений} →
   * {@code ДанныеФормыКоллекция}, {@code ДеревоЗначений} → {@code ДанныеФормыДерево}).
   * Прочие типы — ссылки, примитивы, {@code СписокЗначений}, {@code ДинамическийСписок} —
   * переносятся как есть.
   * <p>
   * У обычной формы преобразования нет: там реквизит — сам прикладной объект.
   */
  Map<String, TypeSet> prepareAttributeTypes(List<FormAttribute> attributes, FormKind kind,
                                                     String suffixRu) {
    if (attributes.isEmpty()) {
      return Map.of();
    }
    var byName = LinkedHashMap.<String, TypeSet>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var declared = ValueTypes.resolve(typeRegistry, attribute.getValueType());
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
        kind == FormKind.MANAGED ? formDataTypes(declared, attribute, kind, suffixRu) : declared);
    }
    return Map.copyOf(byName);
  }

  /**
   * Типы реквизитов формы <b>до</b> перевода в данные формы: {@code имя реквизита
   * (lower) → объявленный в {@code Form.xml} тип}. Ровно их отдаёт обратное
   * преобразование {@code РеквизитФормыВЗначение} (см. {@link FormAttributeTypeIndex}).
   *
   * @param attributes реквизиты формы.
   * @return объявленные типы; пусто, если реквизитов нет.
   */
  Map<String, TypeSet> declaredAttributeTypes(List<FormAttribute> attributes) {
    if (attributes.isEmpty()) {
      return Map.of();
    }
    var byName = LinkedHashMap.<String, TypeSet>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (!name.isBlank()) {
        byName.putIfAbsent(name.toLowerCase(Locale.ROOT), declaredAttributeType(attribute));
      }
    }
    return Map.copyOf(byName);
  }

  /**
   * Объявленный тип одного реквизита — вместе с колонками, если это таблица или дерево
   * значений. Колонки такого реквизита объявлены в самой форме, и обратное
   * преобразование возвращает коллекцию с ними: без этого на сервере получалась бы
   * таблица значений без единой колонки.
   */
  private TypeSet declaredAttributeType(FormAttribute attribute) {
    return withColumns(ValueTypes.resolve(typeRegistry, attribute.getValueType()), attribute.getColumns());
  }

  /**
   * Тип табличной коллекции с колонками, приписанными её строке.
   * <p>
   * Представление то же, что у таблицы значений, собранной в коде ({@code Новый
   * ТаблицаЗначений} + {@code Колонки.Добавить}): колонки — поля строки, строка — тип
   * элемента коллекции. Его и читают обход коллекции, {@code Колонки},
   * {@code Выгрузить} и {@code ВыгрузитьКолонку}.
   *
   * @param declared объявленный тип реквизита.
   * @param columns  колонки, объявленные в форме.
   * @return тип с колонками; исходный, если колонок нет либо тип не табличная коллекция.
   */
  private TypeSet withColumns(TypeSet declared, List<FormAttribute> columns) {
    if (columns.isEmpty() || declared.isEmpty()) {
      return declared;
    }
    var result = declared;
    for (var collectionRef : declared.refs()) {
      var rowName = COLLECTION_ROWS.get(collectionRef.qualifiedName());
      var rowRef = rowName == null ? null : typeRegistry.resolve(rowName).orElse(null);
      if (rowRef != null) {
        result = result.withElement(collectionRef, rowWithColumns(rowRef, columns));
      }
    }
    return result;
  }

  /** Строка коллекции, несущая её колонки полями. */
  private TypeSet rowWithColumns(TypeRef rowRef, List<FormAttribute> columns) {
    var fields = LinkedHashMap.<String, LocalField>newLinkedHashMap(columns.size());
    // Через членов, а не по самим колонкам: имя, описание и дедуп одноимённых у колонки
    // ровно те же, что у реквизита, — правило одно и живёт в одном месте.
    for (var column : buildAttributeMembers(columns, declaredAttributeTypes(columns))) {
      fields.put(column.name(), new LocalField(column.returnTypes(), column.description()));
    }
    return TypeSet.of(rowRef).withFields(rowRef, fields);
  }

  /** Объявленные типы реквизита, переведённые в типы данных формы. */
  private TypeSet formDataTypes(TypeSet declared, FormAttribute attribute, FormKind kind, String suffixRu) {
    if (declared.isEmpty()) {
      return declared;
    }
    var converted = new ArrayList<TypeRef>(declared.refs().size());
    var changed = false;
    for (var ref : declared.refs()) {
      var formDataRef = formDataTypeRef(ref, attribute, kind, suffixRu);
      changed |= formDataRef != null;
      converted.add(formDataRef == null ? ref : formDataRef);
    }
    return changed ? TypeSet.of(converted) : declared;
  }

  /**
   * Тип данных формы для объявленного типа реквизита.
   *
   * @return тип данных формы; {@code null}, если тип переносится на форму как есть.
   */
  private @Nullable TypeRef formDataTypeRef(TypeRef declaredRef, FormAttribute attribute, FormKind kind,
                                            String suffixRu) {
    var dataKind = FormPlatformTypes.formDataKindOf(declaredRef.qualifiedName());
    if (dataKind == null) {
      return null;
    }
    if (dataKind.itemTypeRu() != null) {
      return registerAttributeCollection(attribute, dataKind, kind, suffixRu);
    }
    if (!dataKind.specializable()) {
      return typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    }
    var extended = registerAdditionalColumns(declaredRef, attribute, suffixRu);
    if (!extended.isEmpty()) {
      return registerFormData(declaredRef, dataKind, suffixRu + "." + attribute.getName(), extended);
    }
    return formDataTypes.computeIfAbsent(declaredRef, ref -> registerFormData(ref, dataKind));
  }

  /**
   * Регистрирует данные формы под реквизит-таблицу или дерево значений: колонки такого
   * реквизита объявлены в самой форме (блок {@code <Columns>}) и ложатся свойствами
   * строки — так же, как колонки табличной части ложатся в строку её зеркала.
   * <p>
   * Тип заводится на реквизит конкретной формы, а не на прикладной тип: две формы с
   * реквизитом-{@code ТаблицаЗначений} — это две разные таблицы с разными колонками.
   *
   * @return тип коллекции; базовый {@code ДанныеФормыКоллекция}/{@code ДанныеФормыДерево},
   *   если колонок нет — специализировать тогда нечем.
   */
  private @Nullable TypeRef registerAttributeCollection(FormAttribute attribute,
                                                        FormDataKind dataKind,
                                                        FormKind kind, String suffixRu) {
    var itemTypeRu = Objects.requireNonNullElse(dataKind.itemTypeRu(), "");
    var collectionBase = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    var itemBase = typeRegistry.resolve(itemTypeRu).orElse(null);
    var columns = attribute.getColumns();
    if (columns.isEmpty() || collectionBase == null || itemBase == null) {
      return collectionBase;
    }
    var itemTypeEn = Objects.requireNonNullElse(dataKind.itemTypeEn(), "");
    var suffix = suffixRu + "." + attribute.getName();
    var itemRef = registerFormDataMirror(itemTypeRu, itemTypeEn, suffix, "", itemBase);
    var collectionRef = registerFormDataMirror(
      dataKind.baseTypeRu(), dataKind.baseTypeEn(), suffix, "", collectionBase);
    // Порядок важен: явный тип элемента должен быть задан до наследования, иначе
    // выиграет обобщённая строка базового типа (см. registerTabularSectionData).
    typeRegistry.registerDefaultElementTypes(collectionRef, List.of(itemRef));
    typeRegistry.inheritCollectionTraits(collectionRef, collectionBase, FileType.BSL);

    // Колонки — это те же реквизиты формы, только вложенные: у них есть и свои типы,
    // и заголовки, и собственные колонки, если колонка сама таблица.
    var columnTypes = prepareAttributeTypes(columns, kind, suffix);
    MemberSource columnMembers = () -> buildAttributeMembers(columns, columnTypes);
    typeRegistry.registerMemberSource(itemRef, columnMembers, FileType.BSL);
    // Тем же помощником, что и у табличной части: типы элементов задают только обход
    // коллекции, а `НайтиПоИдентификатору`, `Добавить`, `Получить`, `НайтиСтроки` и
    // `Выгрузить` объявлены платформой через обобщённую строку и без замены отдают её.
    specializeCollectionReturns(collectionRef, collectionBase, itemBase, itemRef, columnMembers);
    rowByCollection.put(collectionRef, itemRef);
    return collectionRef;
  }

  /**
   * Регистрирует тип данных формы под конкретный прикладной тип
   * ({@code ДанныеФормыСтруктура.ДокументОбъект.Документ1}): платформенные методы
   * наследуются от базового типа данных формы, а свойства берутся у самого
   * прикладного типа.
   * <p>
   * Тип заводится на прикладной тип, а не на форму: у всех форм одного документа
   * данные устроены одинаково, и общий тип экономит и регистрацию, и память.
   * Отображаемое имя остаётся базовым ({@code ДанныеФормыСтруктура}) — синтетическое
   * имя нужно реестру, а пользователю показывать надо реальный тип значения.
   */
  private TypeRef registerFormData(TypeRef declaredRef, FormDataKind dataKind) {
    return registerFormData(declaredRef, dataKind, null, Map.of());
  }

  /**
   * Регистрирует тип данных формы, при необходимости — свой у конкретной формы.
   *
   * @param suffixRu    суффикс имени типа; {@code null} — тип общий на прикладной тип
   *                    и назван по нему.
   * @param ownSections коллекции табличных частей, заведённые под эту форму
   *                    ({@code табличная часть объекта → коллекция формы}).
   */
  private TypeRef registerFormData(TypeRef declaredRef, FormDataKind dataKind,
                                   @Nullable String suffixRu, Map<TypeRef, TypeRef> ownSections) {
    var baseRef = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    // Общий тип назван по прикладному типу (и потому двуязычен), тип конкретной формы —
    // по самой форме: её суффикс уже уникален, второго написания у него нет.
    var mirrorRu = suffixRu == null ? typeRegistry.displayName(declaredRef, Language.RU) : suffixRu;
    var mirrorEn = suffixRu == null ? typeRegistry.displayName(declaredRef, Language.EN) : "";
    var dataRef = registerFormDataMirror(
      dataKind.baseTypeRu(), dataKind.baseTypeEn(), mirrorRu, mirrorEn, baseRef);
    if (baseRef != null) {
      typeRegistry.inheritCollectionTraits(dataRef, baseRef, FileType.BSL);
    }
    typeRegistry.registerMemberSource(dataRef, () -> dataProperties(declaredRef, ownSections), FileType.BSL);
    return dataRef;
  }

  /**
   * Заводит коллекции табличных частей, которым форма добавила свои колонки
   * (блок {@code <AdditionalColumns table="Объект.Товары">} среди колонок реквизита).
   * Таких колонок нет в самом объекте — они существуют только в данных этой формы,
   * поэтому общего зеркала табличной части здесь недостаточно.
   *
   * @param declaredRef объявленный тип реквизита ({@code ДокументОбъект.Документ1}).
   * @param attribute   реквизит формы.
   * @param suffixRu    суффикс имени типов этой формы.
   * @return {@code табличная часть объекта → коллекция этой формы}; пусто, если
   *   дополнительных колонок нет либо ни одну табличную часть не удалось опознать.
   */
  private Map<TypeRef, TypeRef> registerAdditionalColumns(TypeRef declaredRef, FormAttribute attribute,
                                                          String suffixRu) {
    var byTabularSection = new LinkedHashMap<TypeRef, TypeRef>();
    var attributeSuffix = suffixRu + "." + attribute.getName();
    for (var column : attribute.getColumns()) {
      if (column instanceof FormAdditionalColumnsAttribute additional && !additional.getColumns().isEmpty()) {
        registerAdditionalColumnsOf(declaredRef, additional, attributeSuffix, byTabularSection);
      }
    }
    if (!byTabularSection.isEmpty()) {
      formTabularSectionData.put(ownSectionsKey(suffixRu, attribute.getName()), Map.copyOf(byTabularSection));
    }
    return byTabularSection;
  }

  /**
   * Заводит коллекцию под одну табличную часть, к которой добавлены колонки, и кладёт её
   * в {@code sink}; табличную часть, которую не удалось опознать, пропускает.
   */
  private void registerAdditionalColumnsOf(TypeRef declaredRef, FormAdditionalColumnsAttribute additional,
                                           String attributeSuffix, Map<TypeRef, TypeRef> sink) {
    var sectionName = shortName(additional.getName());
    var sectionRef = tabularSectionRefOf(declaredRef, sectionName);
    if (sectionRef == null) {
      return;
    }
    var collectionRef = registerExtendedTabularSection(sectionRef,
      attributeSuffix + "." + sectionName, additional.getColumns());
    if (collectionRef != null) {
      sink.put(sectionRef, collectionRef);
    }
  }

  /** Ключ собственных коллекций реквизита формы: имя реквизита — без учёта регистра. */
  private static String ownSectionsKey(String formSuffixRu, String attributeName) {
    return formSuffixRu + "." + attributeName.toLowerCase(Locale.ROOT);
  }

  /**
   * Коллекция данных формы под табличную часть, к колонкам которой форма добавила свои.
   * Строка наследует колонки объекта от строки общего зеркала, а добавленные ложатся
   * на неё сверху — так же, как ложатся колонки самой табличной части.
   *
   * @return коллекция этой формы; {@code null}, если общего зеркала табличной части
   *   нет (без него неоткуда взять колонки объекта) либо базовых типов данных формы
   *   нет в реестре.
   */
  private @Nullable TypeRef registerExtendedTabularSection(TypeRef sectionRef, String suffix,
                                                           List<FormAttribute> extraColumns) {
    var mirrorRef = tabularSectionData.get(sectionRef);
    var mirrorRow = mirrorRef == null ? null : rowByCollection.get(mirrorRef);
    var mirrorColumns = mirrorRef == null ? null : columnsByCollection.get(mirrorRef);
    var collectionBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_RU).orElse(null);
    var itemBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU).orElse(null);
    if (mirrorRow == null || mirrorColumns == null || collectionBase == null || itemBase == null) {
      return null;
    }
    var itemRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU, FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_EN,
      suffix, "", mirrorRow);
    MemberSource extraMembers = () -> buildAttributeMembers(extraColumns, declaredAttributeTypes(extraColumns));
    typeRegistry.registerMemberSource(itemRef, extraMembers, FileType.BSL);

    var collectionRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_RU, FormPlatformTypes.FORM_DATA_COLLECTION_EN,
      suffix, "", collectionBase);
    typeRegistry.registerDefaultElementTypes(collectionRef, List.of(itemRef));
    typeRegistry.inheritCollectionTraits(collectionRef, collectionBase, FileType.BSL);
    MemberSource allColumns = () -> {
      var columns = new ArrayList<>(mirrorColumns.getMembers());
      columns.addAll(extraMembers.getMembers());
      return columns;
    };
    specializeCollectionReturns(collectionRef, collectionBase, itemBase, itemRef, allColumns);
    rowByCollection.put(collectionRef, itemRef);
    columnsByCollection.put(collectionRef, allColumns);
    return collectionRef;
  }

  /**
   * Тип табличной части объекта по её имени. Имена типов регулярны и собираются из
   * имени объектного типа ({@code ДокументОбъект.Документ1} →
   * {@code ДокументТабличнаяЧасть.Документ1.Товары}), поэтому читать члены объектного
   * типа на регистрации формы не приходится.
   *
   * @return тип табличной части; {@code null}, если объектный тип назван не по схеме
   *   либо такой табличной части нет.
   */
  private @Nullable TypeRef tabularSectionRefOf(TypeRef declaredRef, String sectionName) {
    var qualifiedName = declaredRef.qualifiedName();
    var dot = qualifiedName.indexOf('.');
    if (dot < 0 || sectionName.isBlank()) {
      return null;
    }
    var family = qualifiedName.substring(0, dot);
    if (!family.endsWith(OBJECT_SUFFIX_RU)) {
      return null;
    }
    var sectionType = family.substring(0, family.length() - OBJECT_SUFFIX_RU.length())
      + TABULAR_SECTION_SUFFIX_RU + qualifiedName.substring(dot) + "." + sectionName;
    return typeRegistry.resolve(sectionType).orElse(null);
  }

  /** Последний сегмент имени-пути ({@code Объект.Товары} → {@code Товары}). */
  private static String shortName(String path) {
    return path.substring(path.lastIndexOf('.') + 1);
  }

  /**
   * Регистрирует типы данных формы под табличную часть: коллекцию
   * ({@code ДанныеФормыКоллекция}) и её строку ({@code ДанныеФормыЭлементКоллекции}).
   * Колонки у них те же, что у самой табличной части, поэтому источник членов
   * переиспользуется, а не собирается заново.
   * <p>
   * Регистрация идёт при обходе метаданных, а не при регистрации формы: форма узнаёт
   * о табличных частях объекта только из членов его типа, а читать их на регистрации
   * нельзя — это преждевременно материализовало бы тип.
   * <p>
   * Зеркало одно на прикладной тип: у всех форм одного документа табличная часть
   * устроена одинаково. Исключение — форма, добавившая табличной части свои колонки
   * (см. {@link #registerAdditionalColumns}): у неё коллекция и строка свои.
   *
   * @param tabularSectionRef тип табличной части ({@code ДокументТабличнаяЧасть.X.Y}).
   * @param columns           источник колонок табличной части.
   */
  public void registerTabularSectionData(TypeRef tabularSectionRef, MemberSource columns) {
    var collectionBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_RU).orElse(null);
    var itemBase = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU).orElse(null);
    if (collectionBase == null || itemBase == null) {
      // Нет ни синтакс-помощника, ни фолбэка — табличная часть останется со своим типом.
      return;
    }
    var suffix = tabularSectionRef.qualifiedName();
    var itemRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU, FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_EN,
      suffix, "", itemBase);
    var collectionRef = registerFormDataMirror(
      FormPlatformTypes.FORM_DATA_COLLECTION_RU, FormPlatformTypes.FORM_DATA_COLLECTION_EN,
      suffix, "", collectionBase);
    // Явные типы элементов должны быть заданы до наследования от базового типа:
    // иначе выиграет унаследованный обобщённый ДанныеФормыЭлементКоллекции и
    // `Для Каждого Строка Из Объект.Товары` потеряет колонки.
    typeRegistry.registerDefaultElementTypes(collectionRef, List.of(itemRef));
    typeRegistry.inheritCollectionTraits(collectionRef, collectionBase, FileType.BSL);

    // Колонки — только у строки: у самой ДанныеФормыКоллекция их нет, обращение
    // `Объект.Товары.Цена` на форме не работает. (Тип табличной части объекта
    // показывает колонки и на коллекции — там это осознанное послабление, здесь оно
    // ввело бы в заблуждение: у платформенного типа таких свойств действительно нет.)
    typeRegistry.registerMemberSource(itemRef, columns, FileType.BSL);
    // Типы возврата уточняются тем же помощником, что и у табличной части объекта:
    // задача одна — заменить обобщённую строку на строку этой коллекции и доопределить
    // «массив чего» у `НайтиСтроки` и «таблицу с какими колонками» у `Выгрузить`.
    specializeCollectionReturns(collectionRef, collectionBase, itemBase, itemRef, columns);
    tabularSectionData.put(tabularSectionRef, collectionRef);
    rowByCollection.put(collectionRef, itemRef);
    columnsByCollection.put(collectionRef, columns);
  }

  /**
   * Уточняет типы возврата у членов коллекции данных формы под её строку.
   * <p>
   * Нужно всякой коллекции данных формы, откуда бы её колонки ни пришли: и зеркалу
   * табличной части, и реквизиту-таблице (дереву) значений. Явные типы элементов
   * ({@code registerDefaultElementTypes}) закрывают только обход коллекции — обращение
   * же {@code Товары.НайтиПоИдентификатору(Ид).Цена} идёт через объявление члена, а там
   * платформа называет обобщённую строку.
   *
   * @param collectionRef  тип этой коллекции.
   * @param collectionBase базовый платформенный тип коллекции — источник объявлений.
   * @param itemBase       обобщённый тип строки, который надо заменить.
   * @param itemRef        строка этой коллекции.
   * @param columns        колонки — они же колонки выгруженной таблицы значений.
   */
  private void specializeCollectionReturns(TypeRef collectionRef, TypeRef collectionBase,
                                           TypeRef itemBase, TypeRef itemRef, MemberSource columns) {
    var valueTableRow = typeRegistry.resolve(CollectionReturnsSpecializer.VALUE_TABLE_ROW).orElse(null);
    typeRegistry.registerMemberOverride(collectionRef,
      () -> CollectionReturnsSpecializer.specialize(
        typeRegistry.getMembers(collectionBase, FileType.BSL), itemBase, itemRef,
        CollectionReturnsSpecializer.unloadedRow(valueTableRow, columns)), FileType.BSL);
  }

  /**
   * Тип данных формы под конкретный прикладной тип: специализация базового типа
   * данных формы с отображаемым именем самого базового типа.
   */
  TypeRef registerFormDataMirror(String baseRu, String baseEn, String suffixRu, String suffixEn,
                                         @Nullable TypeRef baseRef) {
    var ref = typeRegistry.registerConfigurationType(baseRu + "." + suffixRu);
    var qualifiedEn = baseEn + "." + suffixEn;
    if (!suffixEn.isBlank() && !qualifiedEn.equals(baseRu + "." + suffixRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    // Отображаемое имя — базовое: синтетический суффикс нужен реестру, чтобы различать
    // специализации, а пользователю показывать надо реальный тип значения.
    typeRegistry.registerDisplayName(ref, BilingualString.of(baseRu, baseEn));
    if (baseRef != null && !baseRef.equals(ref)) {
      typeRegistry.registerExtension(ref, baseRef, FileType.BSL);
    }
    return ref;
  }

  /**
   * Свойства прикладного типа, видимые в данных формы. Методы и события отбрасываются:
   * на клиенте за реквизитом стоят только данные, вызвать {@code Записать()} у них нельзя.
   * Инфраструктурные свойства самого объекта ({@code ЭтотОбъект}, {@code Движения},
   * {@code ОбменДанными} …) тоже не переносятся — см.
   * {@link FormPlatformTypes#isTransferredToFormData}. Табличные части перецепляются на
   * свои зеркала: на форме за ними стоит {@code ДанныеФормыКоллекция}, а не табличная
   * часть объекта.
   *
   * @param ownSections коллекции табличных частей, заведённые под конкретную форму
   *                    (у неё к ним добавлены свои колонки); для них зеркало берётся
   *                    не общее, а её собственное.
   */
  private List<MemberDescriptor> dataProperties(TypeRef declaredRef, Map<TypeRef, TypeRef> ownSections) {
    var members = typeRegistry.getMembers(declaredRef, FileType.BSL);
    var properties = new ArrayList<MemberDescriptor>(members.size());
    for (var member : members) {
      if (member.kind() != MemberKind.PROPERTY
        || !FormPlatformTypes.isTransferredToFormData(member)) {
        continue;
      }
      properties.add(withTabularSectionData(member, ownSections));
    }
    return List.copyOf(properties);
  }

  /** Свойство с типами табличных частей, заменёнными на их зеркала в данных формы. */
  private MemberDescriptor withTabularSectionData(MemberDescriptor property, Map<TypeRef, TypeRef> ownSections) {
    if (tabularSectionData.isEmpty()) {
      return property;
    }
    var refs = property.returnTypes().refs();
    var converted = new ArrayList<TypeRef>(refs.size());
    var changed = false;
    for (var ref : refs) {
      var dataRef = ownSections.getOrDefault(ref, tabularSectionData.get(ref));
      changed |= dataRef != null;
      converted.add(dataRef == null ? ref : dataRef);
    }
    return changed ? property.withReturnTypes(TypeSet.of(converted)) : property;
  }

  /**
   * Строка коллекции данных формы: её отдают {@code ТекущиеДанные} и {@code ДанныеСтроки}
   * таблицы над этой коллекцией.
   *
   * @param collectionRef тип коллекции данных формы.
   * @return тип строки; {@code null}, если у коллекции колонок нет.
   */
  @Nullable TypeRef rowOfCollection(TypeRef collectionRef) {
    return rowByCollection.get(collectionRef);
  }

  /**
   * Зеркало табличной части объекта в данных реквизита конкретной формы: собственное,
   * если форма добавила этой табличной части реквизита свои колонки, иначе — общее.
   *
   * @param tabularSectionRef тип табличной части ({@code ДокументТабличнаяЧасть.X.Y}).
   * @param formSuffixRu      суффикс имени типов формы.
   * @param attributeName     имя реквизита формы, через который путь данных ведёт к
   *                          табличной части ({@code Объект} у {@code Объект.Товары}).
   * @return тип коллекции данных формы; {@code null}, если зеркала нет.
   */
  @Nullable TypeRef mirrorOfTabularSection(TypeRef tabularSectionRef, String formSuffixRu, String attributeName) {
    var own = formTabularSectionData.get(ownSectionsKey(formSuffixRu, attributeName));
    var ownRef = own == null ? null : own.get(tabularSectionRef);
    return ownRef == null ? tabularSectionData.get(tabularSectionRef) : ownRef;
  }
}
