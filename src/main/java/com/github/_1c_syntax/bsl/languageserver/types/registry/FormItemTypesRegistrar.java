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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.storage.FormData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDataPathOwner;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElement;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.storage.form.FormTable;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Типы элементов управляемой формы: рантайм-тип по виду элемента, коллекция
 * {@code Элементы} и таблицы формы вместе с правилами их членов.
 * <p>
 * Тип элемента — «база + расширение вида» ({@code ПолеВвода} → {@code ПолеФормы} +
 * «Расширение поля формы для поля ввода»): почти вся прикладная специфика лежит в
 * расширении. Такие типы от конфигурации не зависят и регистрируются по одному на вид.
 * Особняком таблица: расширение ей выбирается не по виду элемента, а по типу
 * отображаемых данных (см. {@link TableDataKind}), а {@code ТекущиеДанные} и
 * {@code ДанныеСтроки} — по конкретной таблице: колонки у каждой свои.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class FormItemTypesRegistrar {

  private final TypeRegistry typeRegistry;
  private final FormDataTypesRegistrar formDataTypes;
  private final DynamicListTypesRegistrar dynamicListTypes;
  private final TableDataMembers tableDataMembers;
  private final FormTypeFactory typeFactory;

  /**
   * Тип на вид элемента формы: база + расширение вида (см. {@link #registerItemKindTypes}).
   * Заполняется на регистрации конфигурации, читается позже из ленивых источников членов
   * на потоках запросов, поэтому карта конкурентная — как и у соседей по пакету.
   */
  private final Map<FormElementType, TypeRef> itemKindTypes = new ConcurrentHashMap<>();

  /** Тип на вид данных таблицы формы (см. {@link #registerTableDataKindTypes}). */
  private final Map<TableDataKind, TypeRef> tableDataKindTypes = new ConcurrentHashMap<>();

  /**
   * Регистрирует по одному типу на вид элемента формы — «базовый рантайм-тип плюс
   * расширение вида» ({@code ГруппаФормы.ОбычнаяГруппа} = {@code ГруппаФормы} +
   * {@code Расширение группы формы для обычной группы}). Без расширения элемент видит
   * лишь общую часть: {@code ЦветФона} у обычной группы объявлен именно в расширении.
   * <p>
   * Типов ровно столько, сколько видов элементов, — они не зависят от конфигурации,
   * поэтому регистрируются один раз на workspace и заранее: {@link #buildItemMembers}
   * вызывается лениво изнутри {@code getMembers}, а регистрация источников там сбивала бы
   * epoch кэша членов прямо во время его пересчёта.
   * <p>
   * Отображаемое имя остаётся базовым ({@code ГруппаФормы}) — синтетическое имя нужно
   * реестру для различения видов, а пользователю показывать надо реальный тип значения.
   */
  void registerItemKindTypes() {
    registerTypelessMemberTypes();
    for (var elementType : FormElementType.values()) {
      var baseName = FormPlatformTypes.itemTypeName(elementType);
      var extensionName = FormPlatformTypes.itemExtensionTypeName(elementType);
      if (baseName == null || extensionName == null) {
        continue;
      }
      var baseRef = typeRegistry.resolve(baseName).orElse(null);
      var extensionRef = typeRegistry.resolve(extensionName).orElse(null);
      if (baseRef == null || extensionRef == null) {
        // Нет синтакс-помощника — расширений в JSON-фолбэке нет; элемент останется
        // с базовым типом, без свойств вида.
        continue;
      }
      var kindRef = typeRegistry.registerConfigurationType(
        baseName + "." + FormPlatformTypes.itemKindSuffix(elementType));
      typeRegistry.registerExtension(kindRef, baseRef, FileType.BSL);
      typeRegistry.registerExtension(kindRef, extensionRef, FileType.BSL);
      typeRegistry.registerDisplayName(kindRef, BilingualString.of(
        typeRegistry.displayName(baseRef, Language.RU),
        typeRegistry.displayName(baseRef, Language.EN)));
      itemKindTypes.put(elementType, kindRef);
    }
    registerTableDataKindTypes();
  }

  /**
   * Проставляет тип свойствам элементов формы, которые платформа объявила без него
   * (см. {@link FormPlatformTypes#TYPELESS_MEMBER_TYPES}). Всё остальное — описание,
   * двуязычное имя, режим доступа, версии — берётся у самого платформенного объявления:
   * доопределяется ровно тип, а не член целиком.
   * <p>
   * Члены владельца читаются здесь же, на регистрации, а в источник уходит уже готовый
   * список: источник живёт на том же типе, чьи члены читает, и ленивое чтение зациклило
   * бы {@code getMembers}. Читать можно: платформенные типы зарегистрированы
   * {@code TypeRegistry.bootstrap()} задолго до регистрации форм.
   */
  private void registerTypelessMemberTypes() {
    FormPlatformTypes.TYPELESS_MEMBER_TYPES.forEach((String ownerName, Map<String, String> typeByMember) -> {
      var ownerRef = typeRegistry.resolve(ownerName).orElse(null);
      if (ownerRef == null) {
        // Нет синтакс-помощника: в JSON-фолбэке этих свойств нет вовсе.
        return;
      }
      var retyped = new ArrayList<MemberDescriptor>(typeByMember.size());
      for (var member : typeRegistry.getMembers(ownerRef, FileType.BSL)) {
        var typeName = memberTypeName(typeByMember, member);
        var typeRef = typeName == null ? null : typeRegistry.resolve(typeName).orElse(null);
        if (typeRef != null) {
          retyped.add(member.withReturnTypes(TypeSet.of(typeRef)));
        }
      }
      if (!retyped.isEmpty()) {
        typeRegistry.registerMemberOverride(ownerRef, () -> retyped, FileType.BSL);
      }
    });
  }

  /**
   * Имя типа, которым надо доопределить свойство; {@code null} — свойства в словаре нет
   * либо платформа тип у него всё-таки объявила (тогда её объявление и остаётся).
   * <p>
   * Имя сопоставляется двуязычным {@link MemberDescriptor#matches}, а не поиском по карте:
   * у члена написаний два, и совпасть должно любое из них — как у остальных словарей
   * {@link FormPlatformTypes}. Перебором, а не вторым ключом в словаре: записей на
   * владельца единицы, а знать оба написания словарю тогда не нужно.
   */
  static @Nullable String memberTypeName(Map<String, String> typeByMember, MemberDescriptor member) {
    if (member.kind() != MemberKind.PROPERTY || !member.returnTypes().isEmpty()) {
      return null;
    }
    for (var entry : typeByMember.entrySet()) {
      if (member.matches(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Регистрирует по типу на вид данных таблицы формы ({@code ТаблицаФормы.ДинамическийСписок}).
   * Устроено как {@link #registerItemKindTypes}, но ось другая: у таблицы расширение
   * определяется не видом элемента, а тем, что она отображает.
   */
  private void registerTableDataKindTypes() {
    var baseName = FormPlatformTypes.tableTypeName();
    var baseRef = typeRegistry.resolve(baseName).orElse(null);
    if (baseRef == null) {
      return;
    }
    for (var dataKind : TableDataKind.values()) {
      var extensionRef = typeRegistry.resolve(dataKind.extensionName()).orElse(null);
      if (extensionRef == null) {
        continue;
      }
      var kindRef = typeRegistry.registerConfigurationType(baseName + "." + dataKind.suffix());
      typeRegistry.registerExtension(kindRef, baseRef, FileType.BSL);
      typeRegistry.registerExtension(kindRef, extensionRef, FileType.BSL);
      typeRegistry.registerDisplayName(kindRef, BilingualString.of(
        typeRegistry.displayName(baseRef, Language.RU),
        typeRegistry.displayName(baseRef, Language.EN)));
      tableDataKindTypes.put(dataKind, kindRef);
      registerRowDataKindType(dataKind, kindRef);
      registerTableDataRules(dataKind, kindRef);
    }
  }

  /**
   * Уточняет члены таблицы формы по виду отображаемых данных. Синтакс-помощник
   * описывает эти правила текстом в описании расширения, а не объявлением типов:
   * «в качестве значений для свойств {@code ТекущаяСтрока}, {@code ТекущийРодитель} …
   * используется идентификатор типа {@code ИдентификаторКомпоновкиДанных}»,
   * «{@code ТекущиеДанные} и метод {@code ДанныеСтроки} возвращают структуру,
   * заполненную копией данных». Сами члены объявлены как {@code Произвольный}, поэтому
   * без такой подстановки цепочка от них обрывается.
   * <p>
   * Описание — не истина в последней инстанции: тип {@code Структура} таблица формы
   * не отдаёт нигде — там либо строка коллекции, либо {@code ДанныеФормыСтруктура}
   * (см. {@link TableDataKind#currentDataTypeName}), — а тип
   * идентификатора описание не называет вовсе, хотя он известен из самих данных формы.
   */
  private void registerTableDataRules(TableDataKind dataKind, TypeRef tableRef) {
    var members = new ArrayList<MemberDescriptor>();
    var rowIdRef = resolveOrNull(dataKind.rowIdTypeName());
    if (rowIdRef != null) {
      members.addAll(tableDataMembers.rowIdentifier(rowIdRef));
    }
    var currentDataRef = resolveOrNull(dataKind.currentDataTypeName());
    if (currentDataRef != null) {
      members.addAll(tableDataMembers.currentData(currentDataRef));
    }
    if (!members.isEmpty()) {
      typeRegistry.registerMemberOverride(tableRef, () -> members, FileType.BSL);
    }
  }

  private @Nullable TypeRef resolveOrNull(@Nullable String typeName) {
    return typeName == null ? null : typeRegistry.resolve(typeName).orElse(null);
  }

  /**
   * Тип строки данных таблицы формы, если платформа даёт для него расширение. Пока
   * такое расширение одно — у динамического списка («когда в списке задано условное
   * оформление»), и на управляемой форме динамический список — единственный источник
   * строк, у которого своя специфика.
   * <p>
   * Тип заводится и тогда, когда расширение пустое: он нужен как якорь для
   * {@code ТекущиеДанные} — вместо обобщённого {@code ДанныеФормыЭлементКоллекции}
   * там оказывается строка именно этого вида данных. Колонки на нём не лежат: они
   * зависят от основной таблицы конкретного списка и лежат на его специализации
   * (см. {@link DynamicListTypesRegistrar}), которая этот тип и расширяет.
   */
  private void registerRowDataKindType(TableDataKind dataKind, TypeRef tableRef) {
    var extensionName = FormPlatformTypes.rowDataExtensionName(dataKind);
    if (extensionName == null) {
      return;
    }
    var rowBaseRef = typeRegistry.resolve(FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU).orElse(null);
    var extensionRef = typeRegistry.resolve(extensionName).orElse(null);
    if (rowBaseRef == null || extensionRef == null) {
      return;
    }
    var rowRef = typeRegistry.registerConfigurationType(
      FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU + "." + dataKind.suffix());
    typeRegistry.registerExtension(rowRef, rowBaseRef, FileType.BSL);
    typeRegistry.registerExtension(rowRef, extensionRef, FileType.BSL);
    typeRegistry.registerDisplayName(rowRef, BilingualString.of(
      typeRegistry.displayName(rowBaseRef, Language.RU),
      typeRegistry.displayName(rowBaseRef, Language.EN)));
    typeRegistry.registerMemberOverride(tableRef,
      () -> List.of(FormPlatformTypes.platformProperty(
        BilingualString.of(FormPlatformTypes.CURRENT_DATA_RU, FormPlatformTypes.CURRENT_DATA_EN),
        rowRef, BilingualString.EMPTY)),
      FileType.BSL);
  }

  /**
   * Типы таблиц формы, у которых {@code ТекущиеДанные} — строка отображаемой коллекции
   * с её колонками (табличная часть, реквизит-таблица или дерево значений). Такой тип
   * заводится на <b>конкретную таблицу</b>, а не на вид данных: колонки у каждой свои.
   * <p>
   * Считается на регистрации формы, а не в ленивом источнике членов: здесь
   * регистрируются типы, а регистрация изнутри {@code getMembers} сбивала бы epoch
   * кэша во время его же пересчёта.
   *
   * @param data           содержимое формы.
   * @param kind           вид формы.
   * @param suffixRu       суффикс имени типов этой формы.
   * @param attributeTypes типы реквизитов формы (уже переведённые в данные формы).
   * @return {@code имя элемента (lower) → тип таблицы}; пусто, если таких таблиц нет.
   */
  Map<String, TypeRef> prepareTableTypes(FormData data, FormKind kind, String suffixRu,
                                                 Map<String, TypeSet> attributeTypes) {
    if (kind != FormKind.MANAGED) {
      return Map.of();
    }
    var declaredTypes = attributeTypesByName(data.getAttributes());
    var dynamicLists = dynamicListTypes.prepareRows(data, suffixRu);
    var result = new HashMap<String, TypeRef>();
    for (var element : data.getPlainElements()) {
      if (!(element instanceof FormTable) || element.getName().isBlank()) {
        continue;
      }
      var dynamicList = dynamicListOf(element, dynamicLists);
      var rowRef = dynamicList == null
        ? rowOf(element, attributeTypes, declaredTypes)
        : dynamicList.rowRef();
      var kindRef = tableTypeRef(element, declaredTypes);
      if (rowRef == null || kindRef == null) {
        continue;
      }
      var tableRef = typeRegistry.registerConfigurationType(
        FormPlatformTypes.tableTypeName() + "." + suffixRu + "." + element.getName());
      typeRegistry.registerExtension(tableRef, kindRef, FileType.BSL);
      typeRegistry.registerDisplayName(tableRef, BilingualString.of(
        typeRegistry.displayName(kindRef, Language.RU),
        typeRegistry.displayName(kindRef, Language.EN)));
      // Типы идентификатора строки считаются здесь, а не в источнике членов: под
      // `ВыделенныеСтроки` регистрируется специализация массива, а регистрация изнутри
      // `getMembers` сбивала бы epoch кэша членов во время его же пересчёта.
      var members = tableMembers(rowRef, dynamicList);
      typeRegistry.registerMemberOverride(tableRef, () -> members, FileType.BSL);
      result.put(element.getName().toLowerCase(Locale.ROOT), tableRef);
    }
    return Map.copyOf(result);
  }

  /**
   * Динамический список, на который смотрит таблица: {@code ПутьКДанным} у неё — имя
   * реквизита-списка целиком, вглубь списка таблица не смотрит.
   *
   * @return разобранный список; {@code null}, если таблица показывает не список либо
   *   источник данных списка неизвестен.
   */
  private static DynamicListTypesRegistrar.@Nullable DynamicList dynamicListOf(
    FormElement element, Map<String, DynamicListTypesRegistrar.DynamicList> dynamicLists) {
    if (dynamicLists.isEmpty()) {
      return null;
    }
    var dataPath = element instanceof FormDataPathOwner owner ? owner.getDataPath() : "";
    if (dataPath.isBlank() || dataPath.indexOf('.') >= 0) {
      return null;
    }
    return dynamicLists.get(dataPath.toLowerCase(Locale.ROOT));
  }

  /**
   * Члены конкретной таблицы формы: строка её данных, а у динамического списка ещё и
   * идентификатор строки. Строку списка платформа адресует ключевым полем его основной
   * таблицы — для ссылочной таблицы это ссылка, — а у прочих видов данных тип
   * идентификатора от конкретной таблицы не зависит и стоит на типе вида данных
   * (см. {@link #registerTableDataRules}).
   */
  private List<MemberDescriptor> tableMembers(TypeRef rowRef,
                                              DynamicListTypesRegistrar.@Nullable DynamicList dynamicList) {
    var currentData = tableDataMembers.currentData(rowRef);
    if (dynamicList == null || dynamicList.rowIdRef() == null) {
      return currentData;
    }
    return FormPlatformTypes.concat(currentData, tableDataMembers.rowIdentifier(dynamicList.rowIdRef()));
  }

  /**
   * Строка отображаемой таблицей коллекции: путь к данным ведёт либо прямо в
   * реквизит-таблицу формы, либо вглубь основного реквизита — в табличную часть объекта.
   *
   * @return тип строки; {@code null}, если у данных таблицы колонок нет.
   */
  private @Nullable TypeRef rowOf(FormElement element, Map<String, TypeSet> attributeTypes,
                                  Map<String, String> declaredTypes) {
    var dataPath = element instanceof FormDataPathOwner owner ? owner.getDataPath() : "";
    if (dataPath.isBlank()) {
      return null;
    }
    var separator = dataPath.indexOf('.');
    if (separator < 0) {
      return attributeTypes.getOrDefault(dataPath.toLowerCase(Locale.ROOT), TypeSet.EMPTY)
        .refs().stream()
        .map(formDataTypes::rowOfCollection)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    }
    var rootName = dataPath.substring(0, separator).toLowerCase(Locale.ROOT);
    var rootType = declaredTypes.getOrDefault(rootName, "");
    var current = rootType.isBlank() ? null : typeRegistry.resolve(rootType).orElse(null);
    for (var segment : dataPath.substring(separator + 1).split("\\.")) {
      if (current == null) {
        return null;
      }
      current = propertyTypeRef(current, segment);
    }
    // Путь идёт по прикладным типам, а строка данных формы живёт у зеркала табличной части.
    var mirror = current == null ? null : formDataTypes.mirrorOfTabularSection(current);
    return mirror == null ? null : formDataTypes.rowOfCollection(mirror);
  }

  /**
   * Тип коллекции элементов формы ({@code ВсеЭлементыФормы.<mdoRef>}): платформенные
   * методы коллекции наследуются от базового типа, а generic-слот
   * {@code <Имя элемента управления>} заменяется реальными элементами формы с их
   * рантайм-типами.
   */
  TypeRef registerItemsCollection(Form form, FormKind kind, String suffixRu,
                                          Map<String, TypeRef> tableTypes) {
    var itemsRef = typeFactory.registerWithAlias(
      kind.itemsTypeRu() + "." + suffixRu,
      kind.itemsTypeEn() + "." + FormPlatformTypes.mdoSuffixEn(form));
    var itemsBaseRef = typeRegistry.resolve(kind.itemsTypeRu()).orElse(null);
    if (itemsBaseRef != null) {
      typeRegistry.registerExtension(itemsRef, itemsBaseRef, FileType.BSL);
      typeRegistry.inheritCollectionTraits(itemsRef, itemsBaseRef, FileType.BSL);
    }
    typeRegistry.registerMemberSource(itemsRef,
      () -> buildItemMembers(form.getData().getPlainElements(), form.getData().getAttributes(), kind, tableTypes),
      FileType.BSL);
    return itemsRef;
  }

  /**
   * Элементы формы как свойства коллекции. {@code getPlainElements()} уже плоский —
   * в управляемой форме коллекция {@code Элементы} тоже плоская независимо от
   * вложенности групп.
   */
  private List<MemberDescriptor> buildItemMembers(List<FormElement> items, List<FormAttribute> attributes,
                                                  FormKind formKind, Map<String, TypeRef> tableTypes) {
    if (items.isEmpty()) {
      return List.of();
    }
    var attributeTypes = attributeTypesByName(attributes);
    var byName = LinkedHashMap.<String, MemberDescriptor>newLinkedHashMap(items.size());
    for (var item : items) {
      var name = item.getName();
      if (name.isBlank()) {
        continue;
      }
      var types = itemTypes(item, attributeTypes, formKind, tableTypes);
      var descriptor = MemberDescriptor.property(name, types, "")
        .withBilingualName(FormPlatformTypes.neutral(name))
        .withBilingualDescription(FormPlatformTypes.bilingual(item.getTitle()));
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT), descriptor);
    }
    return List.copyOf(byName.values());
  }

  /**
   * Тип элемента формы: если у вида есть расширение — специализация «база + расширение»
   * из {@link #registerItemKindTypes}, иначе просто базовый рантайм-тип. Таблица —
   * особый случай: её расширение выбирается по данным, а не по виду.
   */
  TypeSet itemTypes(FormElement item, Map<String, String> attributeTypesByName, FormKind formKind,
                            Map<String, TypeRef> tableTypes) {
    if (formKind == FormKind.ORDINARY) {
      // У обычной формы своя иерархия элементов — `ПолеВвода`, `ТабличноеПоле`,
      // `Надпись`, `Флажок` — со своими расширениями. Она здесь не моделируется, а
      // подмешать элементу обычной формы тип управляемой (`ПолеФормы`) было бы прямой
      // ошибкой: это разные типы с разным составом. Лучше не типизировать вовсе.
      return TypeSet.EMPTY;
    }
    var elementType = item.getType();
    if (elementType == FormElementType.TABLE) {
      // Тип на конкретную таблицу заводится, только когда у её строки есть колонки
      // (см. prepareTableTypes); иначе хватает общего типа по виду данных.
      var ownRef = tableTypes.get(item.getName().toLowerCase(Locale.ROOT));
      if (ownRef != null) {
        return TypeSet.of(ownRef);
      }
      var tableRef = tableTypeRef(item, attributeTypesByName);
      if (tableRef != null) {
        return TypeSet.of(tableRef);
      }
    }
    var kindRef = itemKindTypes.get(elementType);
    if (kindRef != null) {
      return TypeSet.of(kindRef);
    }
    var baseName = FormPlatformTypes.itemTypeName(elementType);
    return baseName == null ? TypeSet.EMPTY : TypeSet.of(typeFactory.resolveOrIntern(baseName));
  }

  /**
   * Тип таблицы формы по её {@code ПутьКДанным}: корневой сегмент пути — это реквизит
   * формы, его тип и определяет расширение ({@code ДинамическийСписок} → расширение
   * динамического списка, {@code Объект.Товары} → расширение табличных частей).
   *
   * @return специализированный тип таблицы; {@code null}, если путь пуст, реквизит не
   *   найден или вид данных не опознан — тогда останется базовая {@code ТаблицаФормы}.
   */
  private @Nullable TypeRef tableTypeRef(FormElement item, Map<String, String> attributeTypesByName) {
    var dataPath = item instanceof FormDataPathOwner owner ? owner.getDataPath() : "";
    if (dataPath.isBlank()) {
      return null;
    }
    var separator = dataPath.indexOf('.');
    var rootName = separator < 0 ? dataPath : dataPath.substring(0, separator);
    var rootType = attributeTypesByName.getOrDefault(rootName.toLowerCase(Locale.ROOT), "");

    // Сначала пройти путь целиком: у частей компоновщика настроек значимый тип стоит в
    // конце (`Отчет.КомпоновщикНастроек.Настройки.Выбор`), а не в корне.
    var walked = walkedDataKind(dataPath, separator, rootType);
    var dataKind = walked != null
      ? walked
      : TableDataKind.of(rootType, separator >= 0);
    return dataKind == null ? null : tableDataKindTypes.get(dataKind);
  }

  /**
   * Вид данных по типу <b>последнего</b> сегмента {@code ПутьКДанным}. Путь идёт по
   * членам: корень — реквизит формы, дальше свойства их типов.
   * <p>
   * Опознаются только виды с известным типом данных, поэтому «не дошли» и «дошли до
   * неинтересного типа» дают один результат — {@code null}, и вызывающий откатывается
   * на разбор по корню пути (там табличная часть опознаётся по вложенности).
   *
   * @return вид данных; {@code null}, если путь односегментный, не резолвится или
   *   тип последнего сегмента не опознан.
   */
  private @Nullable TableDataKind walkedDataKind(String dataPath, int separator,
                                                                  String rootType) {
    if (separator < 0 || rootType.isBlank()) {
      return null;
    }
    var current = typeRegistry.resolve(rootType).orElse(null);
    if (current == null) {
      return null;
    }
    for (var segment : dataPath.substring(separator + 1).split("\\.")) {
      current = propertyTypeRef(current, segment);
      if (current == null) {
        return null;
      }
    }
    return TableDataKind.byTypeName(current.qualifiedName());
  }

  /** Тип свойства с таким именем у типа; {@code null}, если свойства нет или он без типа. */
  private @Nullable TypeRef propertyTypeRef(TypeRef ownerRef, String propertyName) {
    for (var member : typeRegistry.getMembers(ownerRef, FileType.BSL)) {
      if (member.kind() == MemberKind.PROPERTY && !member.generic() && member.matches(propertyName)) {
        return member.returnTypes().refs().stream().findFirst().orElse(null);
      }
    }
    return null;
  }

  /**
   * Карта {@code имя реквизита (lower) → ru-имя его типа} для резолва
   * {@code ПутьКДанным} элементов. Строится по реквизитам формы, а не по реестру:
   * нужно исходное объявление типа из {@code Form.xml}.
   */
  static Map<String, String> attributeTypesByName(List<FormAttribute> attributes) {
    if (attributes.isEmpty()) {
      return Map.of();
    }
    var byName = LinkedHashMap.<String, String>newLinkedHashMap(attributes.size());
    for (var attribute : attributes) {
      var name = attribute.getName();
      if (name.isBlank()) {
        continue;
      }
      var types = attribute.getValueType().getTypes();
      byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
        types.isEmpty() ? "" : types.get(0).fullName().getRu());
    }
    return byName;
  }

}
