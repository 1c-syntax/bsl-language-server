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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
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
        byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
          ValueTypes.resolve(typeRegistry, attribute.getValueType()));
      }
    }
    return Map.copyOf(byName);
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
    var itemTypeEn = Objects.requireNonNullElse(dataKind.itemTypeEn(), "");
    var collectionBase = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    var itemBase = typeRegistry.resolve(itemTypeRu).orElse(null);
    var columns = attribute.getColumns();
    if (columns.isEmpty() || collectionBase == null || itemBase == null) {
      return collectionBase;
    }
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
    var baseRef = typeRegistry.resolve(dataKind.baseTypeRu()).orElse(null);
    var dataRef = registerFormDataMirror(dataKind.baseTypeRu(), dataKind.baseTypeEn(),
      typeRegistry.displayName(declaredRef, Language.RU),
      typeRegistry.displayName(declaredRef, Language.EN), baseRef);
    if (baseRef != null) {
      typeRegistry.inheritCollectionTraits(dataRef, baseRef, FileType.BSL);
    }
    typeRegistry.registerMemberSource(dataRef, () -> dataProperties(declaredRef), FileType.BSL);
    return dataRef;
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
    var valueTableRow = typeRegistry.resolve(CollectionReturnsSpecializer.VALUE_TABLE_ROW).orElse(null);
    typeRegistry.registerMemberOverride(collectionRef,
      () -> CollectionReturnsSpecializer.specialize(
        typeRegistry.getMembers(collectionBase, FileType.BSL), itemBase, itemRef,
        CollectionReturnsSpecializer.unloadedRow(valueTableRow, columns)), FileType.BSL);
    tabularSectionData.put(tabularSectionRef, collectionRef);
    rowByCollection.put(collectionRef, itemRef);
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
   */
  private List<MemberDescriptor> dataProperties(TypeRef declaredRef) {
    var members = typeRegistry.getMembers(declaredRef, FileType.BSL);
    var properties = new ArrayList<MemberDescriptor>(members.size());
    for (var member : members) {
      if (member.kind() != MemberKind.PROPERTY
        || !FormPlatformTypes.isTransferredToFormData(member)) {
        continue;
      }
      properties.add(withTabularSectionData(member));
    }
    return List.copyOf(properties);
  }

  /** Свойство с типами табличных частей, заменёнными на их зеркала в данных формы. */
  private MemberDescriptor withTabularSectionData(MemberDescriptor property) {
    if (tabularSectionData.isEmpty()) {
      return property;
    }
    var refs = property.returnTypes().refs();
    var converted = new ArrayList<TypeRef>(refs.size());
    var changed = false;
    for (var ref : refs) {
      var dataRef = tabularSectionData.get(ref);
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
   * Зеркало табличной части объекта в данных формы.
   *
   * @param tabularSectionRef тип табличной части ({@code ДокументТабличнаяЧасть.X.Y}).
   * @return тип коллекции данных формы; {@code null}, если зеркала нет.
   */
  @Nullable TypeRef mirrorOfTabularSection(TypeRef tabularSectionRef) {
    return tabularSectionData.get(tabularSectionRef);
  }
}
