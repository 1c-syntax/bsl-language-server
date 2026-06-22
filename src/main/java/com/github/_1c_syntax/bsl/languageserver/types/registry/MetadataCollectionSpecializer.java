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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.MD;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Специализирует платформенные generic-коллекции метаданных
 * ({@code КоллекцияОбъектовМетаданных}, {@code ОписанияСтандартныхРеквизитов})
 * под конкретные element-types и владельцев из {@code mdclasses}, чтобы
 * инференс цепочек {@code Метаданные.Документы.Покупатели.Реквизиты.Контрагент},
 * {@code .ТабличныеЧасти.Товары.Реквизиты.Цена},
 * {@code .СтандартныеРеквизиты.Ссылка} и т.п. давал конкретный
 * {@code ОбъектМетаданных: <тип>} вместо общего union'а.
 * <p>
 * Источник истины для element-type:
 * <ol>
 *   <li>HBK-маркер «Элементами коллекции являются объекты типа …», извлечённый
 *       bsl-context'ом ({@link ContextProperty#collectionElementTypes()}) — есть
 *       на {@code ОбъектМетаданныхКонфигурация.X};</li>
 *   <li>Fallback по имени property через {@link #COLLECTIONS} — нужен для
 *       вложенных коллекций ({@code ОбъектМетаданных: <X>.Реквизиты}/
 *       {@code .ТабличныеЧасти}/{@code .СтандартныеРеквизиты}), у которых в HBK
 *       маркера нет.</li>
 * </ol>
 * <p>
 * Регистрация трёхуровневая:
 * <ul>
 *   <li>top-level synthetic {@code КоллекцияОбъектовМетаданных.<Группа>}
 *       (Документы / Справочники / …) — override returnType property
 *       {@code ОбъектМетаданныхКонфигурация.<Группа>};</li>
 *   <li>per-MDO synthetic {@code <element>.<имя>}
 *       ({@code ОбъектМетаданных: Документ.Покупатели}) — выбирается как returnType
 *       материализованного child member, носит override для своих коллекций;</li>
 *   <li>per-MDO/per-collection synthetic
 *       {@code <baseCollection>.<коллекция>.<имя владельца>}
 *       ({@code КоллекцияОбъектовМетаданных.Реквизиты.Покупатели}) — разворачивает
 *       имена детей конкретного MDObject'а из mdclasses.</li>
 * </ul>
 * Для табличных частей выстраивается ещё один уровень рекурсии: child member
 * {@code Товары} указывает на per-TS {@code ОбъектМетаданных: ТабличнаяЧасть.Покупатели.Товары}
 * с собственной коллекцией {@code Реквизиты} (имена колонок ТЧ).
 */
@Slf4j
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class MetadataCollectionSpecializer {

  private static final String BASE_COLLECTION_METADATA = "КоллекцияОбъектовМетаданных";
  private static final String BASE_COLLECTION_STD_ATTR = "ОписанияСтандартныхРеквизитов";
  private static final String BASE_COLLECTION_PROPERTY_VALUE = "КоллекцияЗначенийСвойстваОбъектаМетаданных";
  private static final String BASE_COLLECTION_FIELD_LIST = "СписокПолей";
  private static final String BASE_COLLECTION_ADDITIONAL_INDEXES = "ДополнительныеИндексы";
  private static final String BASE_COLLECTION_CHARACTERISTICS = "ОписанияХарактеристик";
  private static final Set<String> BASE_COLLECTIONS = Set.of(
    BASE_COLLECTION_METADATA, BASE_COLLECTION_STD_ATTR,
    BASE_COLLECTION_PROPERTY_VALUE, BASE_COLLECTION_FIELD_LIST,
    BASE_COLLECTION_ADDITIONAL_INDEXES, BASE_COLLECTION_CHARACTERISTICS);
  private static final Set<String> ELEMENT_RETURNING_METHODS = Set.of(
    "получить", "get",
    "найти", "find"
  );

  /**
   * Имя дочернего элемента коллекции из mdclasses — двуязычное (для стандартных
   * реквизитов) либо одно-локалиное (для пользовательских реквизитов / форм /
   * макетов / …).
   */
  record ChildName(BilingualString name, @Nullable MD child, @Nullable String returnTypeOverride) {

    static @Nullable ChildName of(String name) {
      return name.isBlank() ? null : new ChildName(BilingualString.of(name), null, null);
    }

    static @Nullable ChildName of(String name, MD child) {
      return name.isBlank() ? null : new ChildName(BilingualString.of(name), child, null);
    }

    /**
     * Child с явным override returnType: имя ребёнка ссылается на чужой
     * (за пределами текущего spec'а) per-MDO synthetic-тип. Используется
     * для коллекций-«ссылок» типа {@code Документ.Движения}, где имя
     * регистра в коллекции должно резолвиться в
     * {@code ОбъектМетаданных: РегистрНакопления.<имя>}, а не в общий
     * {@code ЗначениеСвойстваОбъектаМетаданных}.
     */
    static @Nullable ChildName withReturnType(String name, String returnTypeOverride) {
      if (name.isBlank()) {
        return null;
      }
      return new ChildName(BilingualString.of(name), null, returnTypeOverride);
    }

    static @Nullable ChildName bilingual(String ru, String en) {
      if (ru.isBlank() && en.isBlank()) {
        return null;
      }
      if (ru.isBlank()) {
        return new ChildName(BilingualString.of("", en), null, null);
      }
      if (en.isBlank()) {
        return new ChildName(BilingualString.of(ru), null, null);
      }
      return new ChildName(BilingualString.of(ru, en), null, null);
    }
  }

  /**
   * Описание известной коллекции на типе {@code ОбъектМетаданных: <X>} или
   * {@code ОбъектМетаданныхКонфигурация}.
   *
   * @param ru                  имя property на ru-стороне
   * @param en                  имя property на en-стороне
   * @param baseCollectionName  qualifiedName базового платформенного типа
   *                            коллекции ({@code КоллекцияОбъектовМетаданных}
   *                            или {@code ОписанияСтандартныхРеквизитов}) —
   *                            используется как источник базовых members
   *                            (Получить/Найти/Количество и generic-property)
   * @param elementTypeName     qualifiedName типа элемента коллекции
   * @param childExtractor      извлекает имена детей конкретного MDObject'а
   *                            из mdclasses; пустой список — если MDObject не
   *                            поддерживает эту коллекцию (тогда returnType всё
   *                            равно специализирован, но имена не разворачиваются)
   */
  record CollectionSpec(String ru, String en,
                                String baseCollectionName,
                                String elementTypeName,
                                Predicate<MD> appliesTo,
                                Function<MD, List<ChildName>> childExtractor) {
  }

  /**
   * Известные коллекции метаданных. Источник — обход HBK 8.3.27 (property
   * с returnType {@code КоллекцияОбъектовМетаданных} или
   * {@code ОписанияСтандартныхРеквизитов} на типах {@code ОбъектМетаданных: *}).
   * Member-expansion имён реализован для коллекций, у которых mdclasses-API даёт
   * прямой доступ к детям.
   */
  private static final Predicate<MD> ANY = md -> true;

  static final List<CollectionSpec> COLLECTIONS = List.of(
    new CollectionSpec("Реквизиты", "Attributes",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Реквизит",
      MetadataMdoPredicates::isAttributeOwner,
      MetadataChildrenExtractor::attributesFor),
    new CollectionSpec("СтандартныеРеквизиты", "StandardAttributes",
      BASE_COLLECTION_STD_ATTR, "ОписаниеСтандартногоРеквизита",
      StandardAttributesResolver::hasStandardAttributes,
      StandardAttributesResolver::standardAttributesFor),
    new CollectionSpec("ТабличныеЧасти", "TabularSections",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ТабличнаяЧасть",
      MetadataMdoPredicates::isTabularSectionOwner,
      MetadataChildrenExtractor::tabularSectionsFor),
    new CollectionSpec("Формы", "Forms",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Форма",
      MetadataMdoPredicates::isFormOwner,
      MetadataChildrenExtractor::formsFor),
    new CollectionSpec("Макеты", "Templates",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Макет",
      MetadataMdoPredicates::isTemplateOwner,
      MetadataChildrenExtractor::templatesFor),
    new CollectionSpec("Команды", "Commands",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Команда",
      MetadataMdoPredicates::isCommandOwner,
      MetadataChildrenExtractor::commandsFor),
    new CollectionSpec("Измерения", "Dimensions",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Измерение",
      MetadataMdoPredicates::isRegister,
      md -> MetadataChildrenExtractor.singleLingualMdNames(RegisterChildrenExtractor.registerDimensions(md))),
    new CollectionSpec("Ресурсы", "Resources",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Ресурс",
      MetadataMdoPredicates::isRegister,
      md -> MetadataChildrenExtractor.singleLingualMdNames(RegisterChildrenExtractor.registerResources(md))),
    new CollectionSpec("Перерасчеты", "Recalculations",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Перерасчет",
      MetadataMdoPredicates::isCalculationRegister,
      MetadataChildrenExtractor::recalculationsFor),
    new CollectionSpec("Графы", "Columns",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Графа",
      MetadataMdoPredicates::isDocumentJournal,
      MetadataChildrenExtractor::journalColumnsFor),
    new CollectionSpec("ЗначенияПеречисления", "EnumValues",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ЗначениеПеречисления",
      MetadataMdoPredicates::isEnum,
      MetadataChildrenExtractor::enumValuesFor),
    new CollectionSpec("ПризнакиУчета", "AccountingFlags",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ПризнакУчетаПланаСчетов",
      MetadataMdoPredicates::isChartOfAccounts,
      MetadataChildrenExtractor::accountingFlagsFor),
    new CollectionSpec("ПризнакиУчетаСубконто", "ExtDimensionAccountingFlags",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ПризнакУчетаСубконтоПланаСчетов",
      MetadataMdoPredicates::isChartOfAccounts,
      MetadataChildrenExtractor::extDimensionAccountingFlagsFor),
    new CollectionSpec("РеквизитыАдресации", "AddressingAttributes",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: РеквизитАдресации",
      MetadataMdoPredicates::isTask,
      MetadataChildrenExtractor::addressingAttributesFor),
    new CollectionSpec("Реквизиты адресации", "AddressingAttributes",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: РеквизитАдресации",
      MetadataMdoPredicates::isTask,
      MetadataChildrenExtractor::addressingAttributesFor),
    // Коллекции, для которых mdclasses-API нет — returnType chain специализируется
    // (Получить/Найти возвращают конкретный element-type), но имена не разворачиваются.
    new CollectionSpec("Подсистемы", "Subsystems",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Подсистема", ANY, md -> List.of()),
    new CollectionSpec("Кубы", "Cubes",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Куб", ANY, md -> List.of()),
    new CollectionSpec("Таблицы", "Tables",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Таблица", ANY, md -> List.of()),
    new CollectionSpec("ТаблицыИзмерений", "DimensionTables",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ТаблицаИзмерения", ANY, md -> List.of()),
    new CollectionSpec("Поля", "Fields",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Поле", ANY, md -> List.of()),
    new CollectionSpec("Каналы", "Channels",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: КаналСервисаИнтеграции", ANY, md -> List.of()),
    new CollectionSpec("Операции", "Operations",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ОперацияWebСервиса", ANY, md -> List.of()),
    new CollectionSpec("Параметры", "Parameters",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ПараметрWebСервиса", ANY, md -> List.of()),
    new CollectionSpec("Функции", "Functions",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Функция", ANY, md -> List.of()),
    new CollectionSpec("Методы", "Methods",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: Метод", ANY, md -> List.of()),
    new CollectionSpec("ШаблоныURL", "URLTemplates",
      BASE_COLLECTION_METADATA, "ОбъектМетаданных: ШаблонURLHTTPСервиса", ANY, md -> List.of()),

    // Свойства на других базовых типах коллекций (НЕ КоллекцияОбъектовМетаданных):
    // returnType chain специализируется, имена детей разворачиваются где есть mdclasses-API.

    // Движения у документа — типы регистров, в которые делаются записи.
    // Имена приходят через Document.getRegisterRecords() (MdoReference, по нему берётся имя).
    new CollectionSpec("Движения", "RegisterRecords",
      BASE_COLLECTION_PROPERTY_VALUE, "ЗначениеСвойстваОбъектаМетаданных",
      MetadataMdoPredicates::isDocument,
      MetadataChildrenExtractor::registerRecordsFor),

    // ВводитсяНаОсновании — типы, на основании которых вводится объект.
    // В mdclasses нет удобного getter'а — оставляем только returnType chain.
    new CollectionSpec("ВводитсяНаОсновании", "BasedOn",
      BASE_COLLECTION_PROPERTY_VALUE, "ЗначениеСвойстваОбъектаМетаданных",
      ANY, md -> List.of()),

    // Поля ввода по строке / блокировки данных — СписокПолей с элементами «Поле».
    new CollectionSpec("ВводПоСтроке", "InputByString",
      BASE_COLLECTION_FIELD_LIST, "Поле", ANY, md -> List.of()),
    new CollectionSpec("ПоляБлокировкиДанных", "DataLockFields",
      BASE_COLLECTION_FIELD_LIST, "Поле", ANY, md -> List.of()),

    // Дополнительные индексы — элементы «ДополнительныйИндекс».
    new CollectionSpec(BASE_COLLECTION_ADDITIONAL_INDEXES, "AdditionalIndexes",
      BASE_COLLECTION_ADDITIONAL_INDEXES, "ДополнительныйИндекс", ANY, md -> List.of()),

    // Характеристики плана видов характеристик — элементы «ОписаниеХарактеристик».
    new CollectionSpec("Характеристики", "Characteristics",
      BASE_COLLECTION_CHARACTERISTICS, "ОписаниеХарактеристик", ANY, md -> List.of())
  );

  private static final Map<String, CollectionSpec> COLLECTION_BY_NAME = buildCollectionIndex();

  private final TypeRegistry typeRegistry;
  private final BslContextHolder bslContextHolder;
  private final ServerContextProvider serverContextProvider;

  /**
   * Уже обработанные per-owner synthetic-типы в рамках одного {@link #specialize()}.
   * Обход дерева метаданных приходит к одному и тому же synthetic-типу многократно
   * (общие имена табличных частей, общий element-type), а
   * {@link TypeRegistry#registerMemberSource} добавляет источник без дедупликации.
   * Защита гарантирует ровно одну регистрацию источников на тип.
   */
  private final Set<TypeRef> registeredOwners = new HashSet<>();

  private static Map<String, CollectionSpec> buildCollectionIndex() {
    var m = new HashMap<String, CollectionSpec>();
    for (var spec : COLLECTIONS) {
      m.putIfAbsent(spec.ru().toLowerCase(Locale.ROOT), spec);
      m.putIfAbsent(spec.en().toLowerCase(Locale.ROOT), spec);
    }
    return Map.copyOf(m);
  }

  /**
   * Пользовательские реквизиты для коллекции {@code Реквизиты}:
   * {@link com.github._1c_syntax.bsl.mdo.AttributeOwner#getAllAttributes()} включает и стандартные реквизиты
   * с {@link com.github._1c_syntax.bsl.mdo.support.AttributeKind#STANDARD} — они лежат в отдельной коллекции
   * {@code СтандартныеРеквизиты} и не должны попадать в {@code Реквизиты}.
   */
  public void specialize() {
    var providerOpt = bslContextHolder.get();
    if (providerOpt.isEmpty()) {
      return;
    }
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      return;
    }
    var serverContext = serverContextProvider.getAllContexts().get(workspaceUri);
    if (serverContext == null) {
      return;
    }
    var configuration = serverContext.getConfiguration();
    if (configuration.isEmpty()) {
      return;
    }

    registeredOwners.clear();
    var mdosByGroup = collectMdosByGroup(configuration.getChildrenByMdoRef().values());
    var counters = new SpecializationCounters();
    for (var context : providerOpt.get().getContexts()) {
      if (context instanceof ContextType type) {
        processOwnerContext(type, mdosByGroup, counters);
      }
    }
    LOGGER.debug("MetadataCollectionSpecializer: top-level={} nested={}",
      counters.topLevel, counters.nested);
  }

  private static final class SpecializationCounters {
    int topLevel;
    int nested;
  }

  private void processOwnerContext(ContextType type, Map<String, List<MD>> mdosByGroup,
                                   SpecializationCounters counters) {
    var ownerName = type.name().getName();
    var ownerRef = typeRegistry.resolve(ownerName).orElse(null);
    if (ownerRef == null) {
      return;
    }
    var overrides = new LinkedHashMap<String, MemberDescriptor>();
    for (var property : type.properties()) {
      processProperty(property, mdosByGroup, overrides, ownerName, counters);
    }
    if (!overrides.isEmpty()) {
      var captured = List.copyOf(overrides.values());
      typeRegistry.registerMemberOverride(ownerRef, () -> captured, FileType.BSL);
    }
  }

  private void processProperty(ContextProperty property, Map<String, List<MD>> mdosByGroup,
                               Map<String, MemberDescriptor> overrides,
                               String ownerName, SpecializationCounters counters) {
    var propertyName = property.name().getName();
    if (propertyName.isBlank()) {
      return;
    }
    var resolved = resolveCollection(property, propertyName);
    if (resolved == null) {
      return;
    }
    var baseCollectionRef = typeRegistry.resolve(resolved.baseCollectionName()).orElse(null);
    if (baseCollectionRef == null) {
      return;
    }
    var elementTypeRef = typeRegistry.resolve(resolved.elementTypeName())
      .orElseGet(() -> new TypeRef(TypeKind.PLATFORM, resolved.elementTypeName()));
    var mdosForGroup = mdosByGroup.getOrDefault(propertyName, List.of());
    var specRef = registerGroupCollection(baseCollectionRef, propertyName,
      elementTypeRef, mdosForGroup);
    overrides.put(propertyName.toLowerCase(Locale.ROOT),
      buildOverrideProperty(property, specRef));
    if (isMetadataConfiguration(ownerName)) {
      counters.topLevel++;
    } else {
      counters.nested++;
    }
  }

  /**
   * Резолв базы коллекции + element-type'а для property: сначала HBK-маркер,
   * потом fallback по имени property через {@link #COLLECTION_BY_NAME}.
   */
  record ResolvedCollection(String baseCollectionName, String elementTypeName) {
  }

  static @Nullable ResolvedCollection resolveCollection(ContextProperty property, String propertyName) {
    var baseName = baseCollectionName(property);
    if (baseName == null) {
      return null;
    }
    var fromHbk = property.collectionElementTypes();
    if (!fromHbk.isEmpty()) {
      var name = fromHbk.get(0).name().getName();
      if (!name.isBlank()) {
        return new ResolvedCollection(baseName, name);
      }
    }
    var fallback = COLLECTION_BY_NAME.get(propertyName.toLowerCase(Locale.ROOT));
    if (fallback == null) {
      var enName = property.name().getAlias();
      if (!enName.isBlank()) {
        fallback = COLLECTION_BY_NAME.get(enName.toLowerCase(Locale.ROOT));
      }
    }
    if (fallback == null) {
      return null;
    }
    return new ResolvedCollection(fallback.baseCollectionName(), fallback.elementTypeName());
  }

  /**
   * Имя базового платформенного типа коллекции у property (один из
   * {@link #BASE_COLLECTIONS}), либо {@code null} если returnType не коллекция
   * из known-набора.
   */
  private static @Nullable String baseCollectionName(ContextProperty property) {
    for (var type : property.types()) {
      var name = type.name().getName();
      if (BASE_COLLECTIONS.contains(name)) {
        return name;
      }
    }
    return null;
  }

  /**
   * Top-level synthetic для коллекции на {@code ОбъектМетаданныхКонфигурация}
   * (e.g. {@code КоллекцияОбъектовМетаданных.Документы}). Имена детей — это
   * имена MDObject'ов конфигурации (Покупатели/Поставщики/…), каждое
   * указывает на per-MDO {@code ОбъектМетаданных: Документ.<имя>}.
   * <p>
   * Per-MDO TypeRef'ы и их per-collection synthetic-типы регистрируются
   * <b>eagerly</b> прямо здесь, чтобы прямой запрос {@code getMembers(perMdoRef)}
   * (минуя {@code getMembers(groupRef)} — например при completion'е на цепочке
   * с уже выведенным {@code perMdoRef}) находил все материализованные members.
   * Lazy-регистрация через source-лямбду не покрывает этот случай.
   */
  private TypeRef registerGroupCollection(TypeRef baseRef,
                                          String groupName,
                                          TypeRef elementTypeRef,
                                          List<MD> mdos) {
    var specName = baseRef.qualifiedName() + "." + groupName;
    var specRef = typeRegistry.intern(TypeKind.PLATFORM, specName);
    var capturedMdos = List.copyOf(mdos);
    for (var mdo : capturedMdos) {
      var mdoName = mdo.getName();
      if (!mdoName.isBlank()) {
        registerPerOwner(elementTypeRef, mdoName, mdo);
      }
    }
    MemberSource source = () ->
      buildGroupCollectionMembers(typeRegistry, baseRef, elementTypeRef, capturedMdos);
    typeRegistry.registerMemberSource(specRef, source, FileType.BSL);
    return specRef;
  }

  static List<MemberDescriptor> buildGroupCollectionMembers(TypeRegistry typeRegistry,
                                                             TypeRef baseRef,
                                                             TypeRef elementTypeRef,
                                                             List<MD> mdos) {
    var raw = typeRegistry.getMembers(baseRef, FileType.BSL);
    if (raw.isEmpty()) {
      return List.of();
    }
    var elementTypeSet = TypeSet.of(elementTypeRef);
    var result = new ArrayList<MemberDescriptor>(raw.size() + mdos.size());
    for (var member : raw) {
      if (member.generic()) {
        materializeForMdos(typeRegistry, member, mdos, elementTypeRef, result);
      } else if (isElementReturningMethod(member)) {
        result.add(withElementReturnType(member, elementTypeSet));
      } else {
        result.add(member);
      }
    }
    return result;
  }

  private static void materializeForMdos(TypeRegistry typeRegistry, MemberDescriptor member,
                                          List<MD> mdos, TypeRef elementTypeRef,
                                          List<MemberDescriptor> sink) {
    for (var mdo : mdos) {
      var mdoName = mdo.getName();
      if (mdoName.isBlank()) {
        continue;
      }
      var perMdoRef = typeRegistry.intern(TypeKind.PLATFORM,
        elementTypeRef.qualifiedName() + "." + mdoName);
      sink.add(materializeChildMember(member, BilingualString.of(mdoName), TypeSet.of(perMdoRef)));
    }
  }

  /**
   * Per-owner synthetic ({@code ОбъектМетаданных: Документ.Покупатели},
   * {@code ОбъектМетаданных: ТабличнаяЧасть.Покупатели.Товары}). На нём навешан
   * override для known-коллекций; базовые members проксируются от общего
   * element-type'а из bsl-context.
   */
  private TypeRef registerPerOwner(TypeRef elementTypeRef, String ownerSuffix, MD owner) {
    var perOwnerName = elementTypeRef.qualifiedName() + "." + ownerSuffix;
    var perOwnerRef = typeRegistry.intern(TypeKind.PLATFORM, perOwnerName);
    if (!registeredOwners.add(perOwnerRef)) {
      return perOwnerRef;
    }
    var overrides = buildPerOwnerOverrides(perOwnerName, owner);
    var capturedElement = elementTypeRef;
    typeRegistry.registerMemberSource(perOwnerRef,
      () -> nonGenericMembers(typeRegistry.getMembers(capturedElement, FileType.BSL)),
      FileType.BSL);
    if (!overrides.isEmpty()) {
      typeRegistry.registerMemberOverride(perOwnerRef, () -> overrides, FileType.BSL);
    }
    return perOwnerRef;
  }

  /**
   * Отфильтровать generic-template'ы при копировании members из базового типа
   * в per-owner synthetic. Иначе completion на {@code Метаданные.Документы.X.}
   * показывает имена вида {@code <Имя ...>} (placeholder'ы платформенного
   * generic'а), которые имеют смысл только в самом generic'е и не должны
   * утекать в специализацию.
   */
  static List<MemberDescriptor> nonGenericMembers(Collection<MemberDescriptor> source) {
    var result = new ArrayList<MemberDescriptor>(source.size());
    for (var m : source) {
      if (!m.generic()) {
        result.add(m);
      }
    }
    return result;
  }

  /**
   * Override-members для коллекций конкретного owner'а: каждое property
   * (Реквизиты/ТабличныеЧасти/Формы/…) указывает на per-owner/per-collection
   * synthetic-тип, у которого generic-members развёрнуты в имена детей.
   * Override регистрируется даже для коллекций с пустым списком детей — это
   * специализирует returnType (Получить/Найти отдают конкретный element-type),
   * хотя имена детей не разворачиваются. Так пользователь видит, что
   * {@code Метаданные.Документы.Покупатели.Команды} — это
   * {@code КоллекцияОбъектовМетаданных.Команды.Покупатели}, а не общий
   * {@code КоллекцияОбъектовМетаданных}, даже если у документа нет команд.
   */
  private List<MemberDescriptor> buildPerOwnerOverrides(String ownerQualifiedName, MD owner) {
    var overrides = new ArrayList<MemberDescriptor>();
    for (var spec : COLLECTIONS) {
      processOwnerSpec(spec, owner, ownerQualifiedName, overrides);
    }
    return overrides;
  }

  private void processOwnerSpec(CollectionSpec spec, MD owner, String ownerQualifiedName,
                                List<MemberDescriptor> overrides) {
    if (!spec.appliesTo().test(owner)) {
      return;
    }
    var baseRef = typeRegistry.resolve(spec.baseCollectionName()).orElse(null);
    if (baseRef == null) {
      return;
    }
    var children = spec.childExtractor().apply(owner);
    var elementRef = typeRegistry.resolve(spec.elementTypeName())
      .orElseGet(() -> new TypeRef(TypeKind.PLATFORM, spec.elementTypeName()));
    var ownerSuffix = suffixFor(ownerQualifiedName);
    var perCollName = spec.baseCollectionName() + "." + spec.ru() + "." + ownerSuffix;
    var perCollRef = typeRegistry.intern(TypeKind.PLATFORM, perCollName);
    registerSubChildOwners(children, elementRef, ownerSuffix);
    var capturedBase = baseRef;
    var capturedElement = elementRef;
    var capturedChildren = children;
    typeRegistry.registerMemberSource(perCollRef,
      () -> buildPerOwnerCollectionMembers(typeRegistry, capturedBase, capturedElement,
        capturedChildren, ownerSuffix),
      FileType.BSL);
    overrides.add(new MemberDescriptor(
      BilingualString.of(spec.ru(), spec.en()),
      MemberKind.PROPERTY,
      BilingualString.EMPTY,
      TypeSet.of(perCollRef),
      List.of(),
      null,
      false,
      PlatformMetadata.EMPTY,
      false
    ));
  }

  private void registerSubChildOwners(List<ChildName> children, TypeRef elementRef, String ownerSuffix) {
    for (var child : children) {
      var subChild = child.child();
      if (subChild == null) {
        continue;
      }
      var childName = subChild.getName();
      if (!childName.isBlank()) {
        registerPerOwner(elementRef, ownerSuffix + "." + childName, subChild);
      }
    }
  }

  /**
   * Извлекает суффикс владельца из его qualifiedName для composition в имена
   * synthetic-типов: {@code "ОбъектМетаданных: Документ.Покупатели"} →
   * {@code "Покупатели"}; {@code "ОбъектМетаданных: ТабличнаяЧасть.Покупатели.Товары"} →
   * {@code "Покупатели.Товары"}.
   */
  static String suffixFor(String ownerQualifiedName) {
    var colonIdx = ownerQualifiedName.indexOf(':');
    if (colonIdx < 0) {
      return ownerQualifiedName;
    }
    var afterColon = ownerQualifiedName.substring(colonIdx + 1).trim();
    var dotIdx = afterColon.indexOf('.');
    return dotIdx < 0 ? afterColon : afterColon.substring(dotIdx + 1);
  }

  /**
   * Members per-owner/per-collection synthetic-типа: generic-property базы
   * материализуется в имена реальных детей конкретного owner'а; для children
   * с непустым {@link ChildName#child()} (например, TabularSection) returnType
   * указывает на per-child synthetic-тип, чтобы рекурсивно развернуть его
   * коллекции (реквизиты табчасти и т.п.).
   */
  static List<MemberDescriptor> buildPerOwnerCollectionMembers(TypeRegistry typeRegistry,
                                                                TypeRef baseRef,
                                                                TypeRef elementRef,
                                                                List<ChildName> children,
                                                                String ownerSuffix) {
    var raw = typeRegistry.getMembers(baseRef, FileType.BSL);
    var elementTypeSet = TypeSet.of(elementRef);
    var result = new ArrayList<MemberDescriptor>(raw.size() + children.size());
    var hasGenericTemplate = false;
    for (var member : raw) {
      if (member.generic()) {
        hasGenericTemplate = true;
        for (var child : children) {
          result.add(materializeChildMember(member, child.name(),
            childReturnType(typeRegistry, child, elementRef, elementTypeSet, ownerSuffix)));
        }
      } else if (isElementReturningMethod(member)) {
        result.add(withElementReturnType(member, elementTypeSet));
      } else {
        result.add(member);
      }
    }
    // Fallback: у некоторых базовых коллекций (например, ОписанияСтандартныхРеквизитов)
    // нет generic-template member'а в HBK — материализуем имена детей напрямую.
    if (!hasGenericTemplate) {
      for (var child : children) {
        result.add(new MemberDescriptor(
          child.name(),
          MemberKind.PROPERTY,
          BilingualString.EMPTY,
          childReturnType(typeRegistry, child, elementRef, elementTypeSet, ownerSuffix),
          List.of(),
          null,
          false,
          PlatformMetadata.EMPTY,
          false
        ));
      }
    }
    return result;
  }

  static TypeSet childReturnType(TypeRegistry typeRegistry, ChildName child, TypeRef elementRef,
                                 TypeSet defaultSet, String ownerSuffix) {
    var override = child.returnTypeOverride();
    if (override != null) {
      var overrideRef = typeRegistry.resolve(override)
        .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, override));
      return TypeSet.of(overrideRef);
    }
    var subChild = child.child();
    if (subChild == null) {
      return defaultSet;
    }
    var childName = subChild.getName();
    if (childName.isBlank()) {
      return defaultSet;
    }
    var childPerOwnerRef = typeRegistry.intern(TypeKind.PLATFORM,
      elementRef.qualifiedName() + "." + ownerSuffix + "." + childName);
    return TypeSet.of(childPerOwnerRef);
  }

  static MemberDescriptor materializeChildMember(MemberDescriptor template,
                                                 BilingualString name,
                                                 TypeSet returnTypeSet) {
    return new MemberDescriptor(
      name,
      MemberKind.PROPERTY,
      template.bilingualDescription(),
      returnTypeSet,
      List.of(),
      null,
      false,
      template.metadata(),
      false,
      template.standardLibrary()
    );
  }

  static MemberDescriptor withElementReturnType(MemberDescriptor template,
                                                TypeSet elementTypeSet) {
    var rebuiltSignatures = template.signatures();
    if (!rebuiltSignatures.isEmpty()) {
      var newSignatures = new ArrayList<SignatureDescriptor>(rebuiltSignatures.size());
      for (var sig : rebuiltSignatures) {
        newSignatures.add(new SignatureDescriptor(sig.parameters(), elementTypeSet,
          sig.bilingualDescription()));
      }
      rebuiltSignatures = newSignatures;
    }
    return new MemberDescriptor(
      template.bilingualName(),
      template.kind(),
      template.bilingualDescription(),
      elementTypeSet,
      rebuiltSignatures,
      template.sourceSymbol(),
      false,
      template.metadata(),
      template.async(),
      template.standardLibrary()
    );
  }

  static boolean isElementReturningMethod(MemberDescriptor member) {
    if (member.kind() != MemberKind.METHOD) {
      return false;
    }
    var ru = member.bilingualName().ru().toLowerCase(Locale.ROOT);
    var en = member.bilingualName().en().toLowerCase(Locale.ROOT);
    return ELEMENT_RETURNING_METHODS.contains(ru) || ELEMENT_RETURNING_METHODS.contains(en);
  }

  private static MemberDescriptor buildOverrideProperty(ContextProperty property, TypeRef specRef) {
    var ruName = safe(property.name().getName());
    var enName = safe(property.name().getAlias());
    return new MemberDescriptor(
      BilingualString.of(ruName, enName),
      MemberKind.PROPERTY,
      BilingualString.of(safe(property.description())),
      TypeSet.of(specRef),
      List.of(),
      null,
      false,
      PlatformMetadata.EMPTY,
      false
    );
  }

  private static Map<String, List<MD>> collectMdosByGroup(Collection<MD> children) {
    var byGroup = new HashMap<String, List<MD>>();
    for (var md : children) {
      var groupRu = md.getMdoType().fullGroupName().getRu();
      var name = md.getName();
      if (!groupRu.isBlank() && !name.isBlank()) {
        byGroup.computeIfAbsent(groupRu, k -> new ArrayList<>()).add(md);
      }
    }
    return byGroup;
  }

  static boolean isMetadataConfiguration(String name) {
    return "ОбъектМетаданныхКонфигурация".equals(name) || "ConfigurationMetadataObject".equals(name);
  }

  static String safe(@Nullable String s) {
    return s == null ? "" : s;
  }
}
