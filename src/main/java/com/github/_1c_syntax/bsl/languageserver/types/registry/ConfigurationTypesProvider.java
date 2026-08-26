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
import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.mdo.DefinedType;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.MDObject;
import com.github._1c_syntax.bsl.mdo.PredefinedDataOwner;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.children.PredefinedValue;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MultiName;
import com.github._1c_syntax.bsl.types.ValueTypeDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Регистрирует {@link com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType}
 * для каждого MDObject загруженной конфигурации.
 * <p>
 * Имена-ключи строятся из {@link MDOType#fullGroupName()} (например,
 * {@code "Справочники.Контрагенты"}) с алиасом для английского варианта
 * ({@code "Catalogs.Контрагенты"}). Имя самого MD-объекта — это его реальное
 * имя в метаданных (одно и то же на двух языках).
 * <p>
 * Расширение членов (реквизиты, табчасти, методы из ObjectModule/ManagerModule)
 * выполняется отдельным провайдером — {@code ConfigurationModuleMembersProvider}.
 *
 * <h2>Когда регистрируются типы</h2>
 *
 * Инвариант: типы должны быть зарегистрированы <b>до</b> построения первого дерева символов.
 * Всё, что при построении дерева опирается на типы, считается один раз и кэшируется в дереве,
 * поэтому запоздавшая регистрация результат уже не исправит — он останется неверным до
 * следующей перестройки документа.
 * <p>
 * Регистрация идемпотентна и подписана на {@link WorkspaceAddedEvent}: к этому моменту
 * {@code configurationRoot} у контекста уже установлен — {@code addWorkspace} подхватывает его
 * из workspace-scoped конфигурации (см. {@code ServerContextProvider#addWorkspace}), поэтому
 * вызывающему достаточно обновить конфигурацию до {@code addWorkspace}. Деревья символов на
 * этот момент ещё не строились: в LSP публикация {@code .bsl}-файлов идёт только после
 * клиентского {@code initialized}, в CLI/MCP наполнение контекста ({@code populateContext})
 * выполняется уже после {@code addWorkspace}.
 * <p>
 * Порядок среди слушателей {@link WorkspaceAddedEvent} задан явно
 * ({@link Ordered#HIGHEST_PRECEDENCE}): Spring вызывает их последовательно на одном потоке,
 * а {@code OScriptLibraryIndex} делает полный обход дерева workspace'а (на больших проектах
 * это секунды). От регистрации типов он не зависит, поэтому ждать его незачем.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class ConfigurationTypesProvider {

  /** Платформенный тип значения табличной части (в синтакс-помощнике имя с пробелом). */
  private static final String TABULAR_SECTION_TYPE = "Табличная часть";

  /** Платформенный тип строки табличной части. */
  private static final String TABULAR_SECTION_ROW_TYPE = "Строка табличной части";

  /** MDOType'ы, для которых имеет смысл регистрировать менеджер-тип. */
  private static final Set<MDOType> MANAGER_TYPES = Set.of(
    MDOType.CATALOG,
    MDOType.DOCUMENT,
    MDOType.DOCUMENT_JOURNAL,
    MDOType.ENUM,
    MDOType.CHART_OF_CHARACTERISTIC_TYPES,
    MDOType.CHART_OF_ACCOUNTS,
    MDOType.CHART_OF_CALCULATION_TYPES,
    MDOType.INFORMATION_REGISTER,
    MDOType.ACCUMULATION_REGISTER,
    MDOType.ACCOUNTING_REGISTER,
    MDOType.CALCULATION_REGISTER,
    MDOType.BUSINESS_PROCESS,
    MDOType.TASK,
    MDOType.REPORT,
    MDOType.DATA_PROCESSOR,
    MDOType.EXCHANGE_PLAN,
    MDOType.CONSTANT,
    MDOType.SEQUENCE,
    MDOType.FILTER_CRITERION,
    MDOType.SETTINGS_STORAGE,
    MDOType.WS_REFERENCE,
    MDOType.INTEGRATION_SERVICE,
    MDOType.INTEGRATION_SERVICE_CHANNEL,
    MDOType.PALETTE_COLOR
  );

  /**
   * MDOType'ы, у которых есть и «объектная», и «ссылочная» обёртки
   * ({@code СправочникОбъект.X} + {@code СправочникСсылка.X} / {@code ДокументОбъект.X} + ...).
   */
  private static final Set<MDOType> OBJECT_TYPES = EnumSet.of(
    MDOType.CATALOG,
    MDOType.DOCUMENT,
    MDOType.CHART_OF_CHARACTERISTIC_TYPES,
    MDOType.CHART_OF_ACCOUNTS,
    MDOType.CHART_OF_CALCULATION_TYPES,
    MDOType.BUSINESS_PROCESS,
    MDOType.TASK,
    MDOType.EXCHANGE_PLAN
  );

  /**
   * MDOType'ы, у которых есть «объектная» обёртка ({@code ОтчётОбъект.X},
   * {@code ОбработкаОбъект.X}), но НЕТ ссылочного типа (это не ссылочные объекты
   * конфигурации). Их реквизиты — такие же члены объектного типа, как у справочника
   * или документа, но ссылочный тип для них не регистрируем.
   */
  private static final Set<MDOType> OBJECT_ONLY_TYPES = EnumSet.of(
    MDOType.REPORT,
    MDOType.DATA_PROCESSOR
  );

  private final TypeRegistry typeRegistry;
  private final ServerContextProvider serverContextProvider;
  private final GlobalScopeProvider globalScopeProvider;
  private final LanguageServerConfiguration configuration;
  private final MetadataCollectionSpecializer metadataCollectionSpecializer;
  private final ConfigurationGenericExpander genericExpander;
  private final CatalogOwnerTypesRegistrar catalogOwnerTypes;
  private final ServiceModuleEventRegistrar serviceModuleEventRegistrar;
  private final FormTypesProvider formTypesProvider;
  private final ConfigurationModuleMembersProvider moduleMembersProvider;
  private final XdtoTypesProvider xdtoTypesProvider;
  private final FormDataTypesRegistrar formDataTypesRegistrar;
  private final RegisterTypesRegistrar registerTypesRegistrar;
  private final RecorderIndex recorderIndex;
  private final MdoMemberFactory mdoMembers;
  @Qualifier("platformTypesWarmupExecutor")
  private final AsyncTaskExecutor platformTypesWarmupExecutor;

  private final AtomicBoolean registered = new AtomicBoolean(false);

  /**
   * Регистрирует типы конфигурации текущего workspace'а (см. {@link #tryRegister()}).
   *
   * @param event событие добавления workspace'а.
   */
  @EventListener
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void handleEvent(WorkspaceAddedEvent event) {
    tryRegister();
  }

  /**
   * Регистрирует конфигурационные типы workspace'а, взятого из
   * {@link WorkspaceContextHolder}, и события его служебных модулей.
   * <p>
   * Идемпотентно: фактическая регистрация выполняется не более одного раза на экземпляр,
   * повторные вызовы — no-op. No-op также, если конфигурация workspace'а ещё пуста
   * (у голого «безворкспейсного» контекста {@code configurationRoot == null}).
   * <p>
   * Побочный эффект помимо регистрации: форсирует инициализацию платформенных generic-типов
   * ({@code TypeRegistry.bootstrap()}). Она нужна {@code registerFamilySpecializations},
   * который ищет generic'и по familyCore и без них молча пропускает специализации, и
   * выполняется на {@link #platformTypesWarmupExecutor} параллельно чтению конфигурации —
   * это независимые источники. Собственный executor обязателен: воркеры общего
   * {@code ForkJoinPool} в исполняемом fat-jar получают чужой {@code contextClassLoader},
   * из-за чего загрузка встроенных JSON-описаний падает с {@code FileNotFoundException}.
   *
   * @return контекст сервера, если регистрация действительно выполнена; иначе {@code null}.
   */
  public @Nullable ServerContext tryRegister() {
    if (registered.get()) {
      return null;
    }
    // workspaceUri установлен всегда: tryRegister зовётся только из обработчика
    // WorkspaceAddedEvent, а событие публикуется под workspace-контекстом.
    var workspaceUri = WorkspaceContextHolder.get();
    var serverContext = serverContextProvider.getAllContexts().get(workspaceUri);
    if (serverContext == null) {
      return null;
    }

    // Прогрев платформенных generic-типов запускаем параллельно чтению конфигурации
    // (независимые источники) на выделенном executor'е — не на общем ForkJoinPool.
    var platformTypesWarmup = CompletableFuture.runAsync(() -> {
      try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
        typeRegistry.ensureInitialized();
      }
    }, platformTypesWarmupExecutor).exceptionally((Throwable e) -> {
      LOGGER.warn("Failed to warm up platform types for workspace {}", workspaceUri, e);
      return null;
    });

    if (serverContext.getConfiguration().isEmpty() || !registered.compareAndSet(false, true)) {
      return null;
    }
    var children = serverContext.getConfiguration().getChildrenByMdoRef().values();
    LOGGER.debug("ConfigurationTypesProvider[{}]: registering {} MD objects",
      workspaceUri, children.size());
    platformTypesWarmup.join();
    register(children, serverContext.getScriptVariantLanguage());
    serviceModuleEventRegistrar.register(children);
    return serverContext;
  }

  private void register(Iterable<MD> children, Language projectLanguage) {
    Map<MDOType, List<MemberDescriptor>> collectionMembersByType = new HashMap<>();

    var commonAttributes = collectCommonAttributes(children);
    // «Регистр → его регистраторы» — только обходом документов: со стороны регистра
    // этих данных в метаданных нет. Нужно до регистрации типов регистров.
    recorderIndex.index(children);
    int count = 0;
    for (var md : children) {
      if (processMdoChild(md, commonAttributes, collectionMembersByType)) {
        count++;
      }
    }

    int collections = registerCollectionNamespaces(collectionMembersByType);

    // Внешние источники данных — multi-placeholder type-level специализация
    // по иерархии конфигурации: источник → куб/таблица → измерение/таблица
    // измерения.
    genericExpander.registerExternalDataSourceSpecializations(children);

    // Общие библиотеки (макеты СКД, стили) — global property с generic-property
    // `<Имя макета>`/`<Имя стиля>`, материализуются именами из Configuration.
    genericExpander.registerCommonLibraryExpansions();

    // Метаданные.<коллекция>.<имя> и вложенные коллекции (Реквизиты/ТабличныеЧасти/…):
    // specialization КоллекцияОбъектовМетаданных по per-property element-type
    // из bsl-context + развёртывание имён детей коллекции из mdclasses.
    metadataCollectionSpecializer.specialize();

    // Тип на каждую форму: реквизиты, элементы, расширение по основному реквизиту
    // и обработчики событий из Form.xml.
    formTypesProvider.register(children, projectLanguage);

    // Тип на каждый объектный тип XDTO-пакета: имя как в ссылке
    // «См. XDTOПакет.<Пакет>.<Тип>», члены — свойства из схемы пакета.
    xdtoTypesProvider.register(children);

    LOGGER.debug("Configuration types registered: {}, collection global properties: {}", count, collections);
  }

  private static List<CommonAttribute> collectCommonAttributes(Iterable<MD> children) {
    var commonAttributes = new ArrayList<CommonAttribute>();
    for (var md : children) {
      if (md instanceof CommonAttribute ca) {
        commonAttributes.add(ca);
      }
    }
    return commonAttributes;
  }

  /**
   * Обработка одного MD-объекта в {@link #register}: регистрация менеджера,
   * объектных/ссылочных типов, family-специализаций, expansion'ов для
   * Enum/Journal/регистров, алиасов и member'а для namespace. Определяемый тип
   * вместо типа отдаёт реестру свой состав — типа у него нет.
   *
   * @return {@code true} если MD относится к {@link #MANAGER_TYPES} и был зарегистрирован.
   */
  private boolean processMdoChild(MD md, List<CommonAttribute> commonAttributes,
                                  Map<MDOType, List<MemberDescriptor>> collectionMembersByType) {
    if (md instanceof DefinedType definedType) {
      // За именем определяемого типа стоит набор, а не тип, поэтому реестру отдаётся
      // состав. Реквизиты считаются лениво, уже после регистрации, — успеть до них
      // достаточно здесь.
      typeRegistry.registerDefinedType(
        definedType.getMdoReference().getMdoRefRu(),
        definedType.getValueType().getTypes().stream().map(type -> type.fullName().getRu()).toList());
      return false;
    }
    var mdoType = md.getMdoType();
    if (mdoType == MDOType.COMMON_MODULE) {
      // Имя общего модуля известно уже здесь, а разбор его файла нужен только членам.
      // Тип объявляется сразу: иначе обращение в модуль из документа, разобранного раньше
      // него, не находит даже получателя.
      moduleMembersProvider.declareCommonModuleType(md);
      return false;
    }
    if (!MANAGER_TYPES.contains(mdoType)) {
      return false;
    }
    var name = md.getName();
    if (name.isBlank()) {
      return false;
    }
    var groupRu = mdoType.fullGroupName().getRu();
    var groupEn = mdoType.fullGroupName().getEn();
    var fullName = mdoType.fullName();
    var managerNames = managerNamesFor(fullName, groupRu, groupEn, name);
    var managerRu = managerNames.ru();
    var managerEn = managerNames.en();
    var ref = typeRegistry.registerConfigurationType(managerRu);
    if (managerEn != null && !managerEn.equals(managerRu)) {
      typeRegistry.registerConfigurationTypeAlias(managerEn, ref);
    }
    typeRegistry.registerDisplayName(ref,
      BilingualString.of(managerRu, managerEn == null ? managerRu : managerEn));

    registerObjectAndRefTypes(md, mdoType, name, fullName, commonAttributes);
    registerSpecializationsAndExpansions(md, ref, name, fullName);
    registerCollectionAliases(ref, managerNames, groupRu, groupEn, name);

    collectionMembersByType
      .computeIfAbsent(mdoType, k -> new ArrayList<>())
      .add(MemberDescriptor.property(name, ref));
    return true;
  }

  private record ManagerNames(String ru, @Nullable String en) {
  }

  private static ManagerNames managerNamesFor(MultiName fullName,
                                              String groupRu, String groupEn, String name) {
    if (!fullName.getRu().isBlank()) {
      var ru = fullName.getRu() + "Менеджер." + name;
      var fullEn = fullName.getEn();
      var en = fullEn.isBlank() ? null : (fullEn + "Manager." + name);
      return new ManagerNames(ru, en);
    }
    var ru = groupRu + "." + name;
    var en = groupEn.equals(groupRu) ? null : (groupEn + "." + name);
    return new ManagerNames(ru, en);
  }

  private void registerSpecializationsAndExpansions(MD md, TypeRef ref, String name, MultiName fullName) {
    if (fullName.getRu().isBlank()) {
      return;
    }
    var familyCore = fullName.getRu();
    registerFamilySpecializations(familyCore, name);
    registerTypesRegistrar.registerFamilyFixups(md, familyCore, name);
    registerHierarchySuppressions(md, familyCore, name);
    catalogOwnerTypes.registerOwnerMembers(md, familyCore, name);
    registerDerivedSpecializations(md, name);
    if (md instanceof DocumentJournal journal) {
      registerDocumentJournalColumnMembers(journal, familyCore, name);
    }
    if (md instanceof Enum anEnum) {
      registerEnumValueExpansion(ref, familyCore, name, anEnum);
    }
    if (md instanceof PredefinedDataOwner predefinedDataOwner) {
      registerPredefinedValueExpansion(ref, familyCore, name, predefinedDataOwner);
    }
    var registerChildren = RegisterTypesRegistrar.registerChildrenOf(md);
    if (registerChildren != null) {
      registerTypesRegistrar.registerRecordSetCollectionMembers(familyCore, name);
      registerTypesRegistrar.registerRecordExpansion(familyCore, name, registerChildren);
    }
  }

  private void registerCollectionAliases(TypeRef ref, ManagerNames managerNames,
                                         String groupRu, String groupEn, String name) {
    var collectionAliasRu = groupRu + "." + name;
    if (!collectionAliasRu.equals(managerNames.ru())) {
      typeRegistry.registerConfigurationTypeAlias(collectionAliasRu, ref);
    }
    globalScopeProvider.registerConfigurationQualifiedName(collectionAliasRu);
    if (!groupEn.equals(groupRu)) {
      var collectionAliasEn = groupEn + "." + name;
      if (!collectionAliasEn.equals(managerNames.ru()) && !collectionAliasEn.equals(managerNames.en())) {
        typeRegistry.registerConfigurationTypeAlias(collectionAliasEn, ref);
      }
      globalScopeProvider.registerConfigurationQualifiedName(collectionAliasEn);
    }
  }

  /**
   * Коллекции-namespace (Справочники/Catalogs, Документы/Documents): глобальное
   * свойство с членами-MD и платформенными методами коллекции-менеджера.
   *
   * @return число зарегистрированных коллекций.
   */
  private int registerCollectionNamespaces(Map<MDOType, List<MemberDescriptor>> collectionMembersByType) {
    int collections = 0;
    for (var entry : collectionMembersByType.entrySet()) {
      var mdoType = entry.getKey();
      var members = entry.getValue();
      var collectionRu = mdoType.fullGroupName().getRu();
      var collectionEn = mdoType.fullGroupName().getEn();
      var ref = typeRegistry.registerConfigurationType(collectionRu);
      if (!collectionEn.equals(collectionRu)) {
        typeRegistry.registerConfigurationTypeAlias(collectionEn, ref);
      }
      typeRegistry.registerDisplayName(ref, BilingualString.of(collectionRu, collectionEn));
      typeRegistry.registerMemberSource(ref, () -> members, FileType.BSL);

      // коллекция-namespace — глобальное свойство (имя/bilingual и value-type
      // реестр соберёт сам из displayName/ref; declaration у коллекции нет).
      typeRegistry.registerGlobalPropertyType(ref, FileType.BSL);

      // Платформенные методы коллекции-менеджера (СправочникиМенеджер,
      // ДокументыМенеджер) — уровня всех справочников/документов, например
      // `ТипВсеСсылки()`. Имя фиксированное (без generic-плейсхолдера).
      registerInheritedMembers(ref, collectionRu + "Менеджер");
      collections++;
    }
    return collections;
  }

  /**
   * Зарегистрировать «объектную» (и, для ссылочных объектов, «ссылочную») обёртку
   * метаобъекта (например, {@code СправочникОбъект.X} + {@code СправочникСсылка.X};
   * для отчёта/обработки — только {@code ОтчётОбъект.X}/{@code ОбработкаОбъект.X})
   * и навесить на них членов из реквизитов метаданных и табличных частей.
   * <p>
   * Реквизиты (включая стандартные) регистрируются как PROPERTY; табличные части —
   * см. {@link #registerTabularSections}. Object-only типы (отчёт/обработка)
   * получают только объектную обёртку — ссылочного типа у них нет.
   */
  private void registerObjectAndRefTypes(MD md,
                                         MDOType mdoType,
                                         String name,
                                         MultiName fullName,
                                         List<CommonAttribute> commonAttributes) {
    var hasRefType = OBJECT_TYPES.contains(mdoType);
    // Отчёт/обработка: объектная обёртка есть, ссылочного типа нет — их реквизиты
    // регистрируем как члены объектного типа, но без Ссылка-обёртки.
    if (!hasRefType && !OBJECT_ONLY_TYPES.contains(mdoType)) {
      return;
    }
    var fullRu = fullName.getRu();
    var fullEn = fullName.getEn();
    if (fullRu.isBlank()) {
      return;
    }

    if (!(md instanceof AttributeOwner attributeOwner)) {
      return;
    }
    var attributes = attributeOwner.getAllAttributes();
    var commonForMd = MdoMemberFactory.applicableCommonAttributes(md, commonAttributes);

    // Описания стандартных реквизитов (Дата/Номер/Ссылка/…) в mdclasses пустые,
    // но платформа в HBK ровно их и описывает. Подмешиваем по имени.
    // Сборка делается лениво (внутри MemberSource), чтобы пересоздаваться при
    // смене языка через workspace/didChangeConfiguration — `attributeNameLocalized`
    // читает {@code configuration.getLanguage()} per-call, и при пересоздании
    // members имена обновляются.
    final var capturedAttributes = attributes;
    final var capturedCommon = commonForMd;
    final var capturedFullRu = fullRu;

    var objectRu = fullRu + "Объект." + name;
    var objectEn = fullEn.isBlank() ? "" : (fullEn + "Object." + name);
    var objectRef = registerWithAlias(objectRu, objectEn);

    // Объектный тип есть у всех обрабатываемых здесь MDOType (в т.ч. object-only
    // отчёта/обработки): его реквизиты — члены этого типа.
    MemberSource objectSource = () -> {
      var fresh = new ArrayList<MemberDescriptor>();
      fresh.addAll(mdoMembers.attributeMembers(capturedAttributes,
        mdoMembers.platformDescriptions(capturedFullRu),
        mdoMembers.platformMetadata(capturedFullRu + "Объект")));
      fresh.addAll(mdoMembers.commonAttributeMembers(capturedCommon));
      return fresh;
    };
    typeRegistry.registerMemberSource(objectRef, objectSource, FileType.BSL);

    // Ссылочный тип и связанные с ним источники — только для ссылочных объектов
    // (отчёт/обработка ссылочного типа не имеют).
    if (hasRefType) {
      registerRefType(md, name, fullName, objectRef, capturedAttributes, capturedCommon);
    }

    // Табличные части: регистрируем пару типов <prefix>ТабличнаяЧасть(Строка)?.<MD>.<TS>
    // и добавляем member <TS-name> на объектный тип.
    registerTabularSections(md, name, fullRu, fullEn, objectRef);
  }

  /**
   * Регистрирует «ссылочную» обёртку метаобъекта ({@code СправочникСсылка.X}), её
   * singular-алиас ({@code Справочник.X}) и источник членов из тех же реквизитов, что и
   * у объектной обёртки, но с метаданными ссылочного семейства.
   * <p>
   * Метаданные берутся раздельно по семействам, потому что у platform-generic'ов Object и Ref
   * на одних и тех же стандартных реквизитах разные accessMode (например, Дата мутабельна на
   * объекте и read-only на ссылке); описания при этом общие.
   */
  private void registerRefType(MD md,
                               String name,
                               MultiName fullName,
                               TypeRef objectRef,
                               List<? extends Attribute> attributes,
                               List<CommonAttribute> commonAttributes) {
    var fullRu = fullName.getRu();
    var fullEn = fullName.getEn();

    var refRu = fullRu + "Ссылка." + name;
    var refEn = fullEn.isBlank() ? "" : fullEn + "Ref." + name;
    var refRef = registerWithAlias(refRu, refEn);

    // Singular alias `Справочник.X` / `Catalog.X` ведёт на ссылочный тип:
    // соответствует семантике стандартных описаний 1С (`См. Справочник.X.Реквизит`
    // — тип реквизита справочника).
    var singularRu = fullRu + "." + name;
    if (!singularRu.equals(refRu)) {
      typeRegistry.registerConfigurationTypeAlias(singularRu, refRef);
    }
    if (!fullEn.isBlank()) {
      var singularEn = fullEn + "." + name;
      if (!singularEn.equals(refEn)) {
        typeRegistry.registerConfigurationTypeAlias(singularEn, refRef);
      }
    }

    MemberSource refSource = () -> {
      var fresh = new ArrayList<MemberDescriptor>();
      fresh.addAll(mdoMembers.attributeMembers(attributes,
        mdoMembers.platformDescriptions(fullRu),
        mdoMembers.platformMetadata(fullRu + "Ссылка")));
      fresh.addAll(mdoMembers.commonAttributeMembers(commonAttributes));
      return fresh;
    };
    typeRegistry.registerMemberSource(refRef, refSource, FileType.BSL);

    // Дополнительные mdclasses-specific аттрибуты, не входящие в getAllAttributes:
    // признаки учёта и флаги учёта субконто для плана счетов.
    registerMdoSpecificAttributeMembers(md, objectRef, refRef);
  }

  /**
   * Атрибуты, специфичные для отдельных MDOType, которые не приходят через
   * {@link AttributeOwner#getAllAttributes()}: для {@link ChartOfAccounts} —
   * признаки учёта и флаги учёта субконто. Все три типа реализуют {@link Attribute},
   * поэтому используются через {@link #buildAttributeMembers(List)} как property-члены.
   */
  private void registerMdoSpecificAttributeMembers(MD md, TypeRef objectRef, TypeRef refRef) {
    if (!(md instanceof ChartOfAccounts coa)) {
      return;
    }
    var extras = new ArrayList<Attribute>();
    extras.addAll(coa.getAccountingFlags());
    extras.addAll(coa.getExtDimensionAccountingFlags());
    if (extras.isEmpty()) {
      return;
    }
    var captured = List.copyOf(extras);
    MemberSource source = () -> mdoMembers.attributeMembers(captured);
    typeRegistry.registerMemberSource(objectRef, source, FileType.BSL);
    if (!refRef.equals(objectRef)) {
      typeRegistry.registerMemberSource(refRef, source, FileType.BSL);
    }
  }

  /**
   * Для каждой табличной части MD регистрирует два типа:
   * <ul>
   *   <li>{@code <prefix>ТабличнаяЧастьСтрока.<MD>.<TS>} — строка ТЧ, members — её колонки;</li>
   *   <li>{@code <prefix>ТабличнаяЧасть.<MD>.<TS>} — коллекция строк (item type = строка).</li>
   * </ul>
   * На объектный тип MD добавляется member {@code <TS-name>} типа коллекции —
   * это даёт dot-completion {@code Док.Объект.ТЧ.<колонки>} (через коллекцию)
   * и {@code Док.Объект.ТЧ.Добавить()} после подмешивания методов коллекции.
   * Стандартные методы коллекций (Добавить/Очистить/НайтиСтроки/…) сюда пока не
   * добавляются — для них нужен отдельный источник (нет generic-типа в HBK).
   */
  private void registerTabularSections(MD md,
                                       String name,
                                       String fullRu,
                                       String fullEn,
                                       TypeRef objectRef) {
    if (!(md instanceof TabularSectionOwner owner)) {
      return;
    }
    var sections = owner.getTabularSections();
    if (sections.isEmpty()) {
      return;
    }
    var tsMembers = new ArrayList<MemberDescriptor>(sections.size());
    for (var ts : sections) {
      var tsName = ts.getName();
      if (tsName.isBlank()) {
        continue;
      }
      var rowRu = fullRu + "ТабличнаяЧастьСтрока." + name + "." + tsName;
      var rowEn = fullEn.isBlank() ? ""
        : fullEn + "TabularSectionRow." + name + "." + tsName;
      var rowRef = registerWithAlias(rowRu, rowEn);

      var collRu = fullRu + "ТабличнаяЧасть." + name + "." + tsName;
      var collEn = fullEn.isBlank() ? ""
        : fullEn + "TabularSection." + name + "." + tsName;
      var collRef = registerWithAlias(collRu, collEn);

      var tsAttributes = ts.getAttributes();
      // Аналогично основным реквизитам: лямбда вызывает buildAttributeMembers
      // на каждый getMembers, поэтому язык читается per-call и подхватывает
      // workspace/didChangeConfiguration.
      MemberSource columnSource = () -> mdoMembers.attributeMembers(tsAttributes);
      if (!tsAttributes.isEmpty()) {
        // Колонки — только у строки: у самой табличной части их нет, обращение
        // `ТЧ.Цена` в 1С не работает.
        typeRegistry.registerMemberSource(rowRef, columnSource, FileType.BSL);
      }
      registerTabularSectionPlatformMembers(rowRef, collRef, columnSource);
      // Зеркало табличной части для управляемых форм заводится здесь, а не при
      // регистрации формы: колонки уже под рукой, а форма узнаёт о табличных частях
      // только из членов объектного типа — читать их из её ленивого источника нельзя.
      formDataTypesRegistrar.registerTabularSectionData(collRef, columnSource);

      tsMembers.add(MemberDescriptor.property(tsName, collRef));
    }
    if (!tsMembers.isEmpty()) {
      var immutableTs = List.copyOf(tsMembers);
      typeRegistry.registerMemberSource(objectRef, () -> immutableTs, FileType.BSL);
    }
  }

  /**
   * Подмешивает табличной части и её строке платформенную часть: методы коллекции
   * ({@code Добавить}, {@code НайтиСтроки}, {@code Выгрузить} …), обход
   * {@code Для Каждого}, индексатор и {@code НомерСтроки} у строки.
   * <p>
   * Типы возврата при этом уточняются под строку <b>этой</b> табличной части: у
   * платформенного описания они обобщённые, и без уточнения цепочка обрывалась бы на
   * первом же вызове (см. {@link CollectionReturnsSpecializer}). Тип элемента коллекции
   * задаётся до наследования коллекционных свойств — иначе выиграла бы унаследованная
   * обобщённая строка и {@code Для Каждого} потерял бы колонки.
   *
   * @param rowRef  тип строки этой табличной части.
   * @param collRef тип этой табличной части.
   * @param columns источник её колонок.
   */
  private void registerTabularSectionPlatformMembers(TypeRef rowRef, TypeRef collRef, MemberSource columns) {
    var genericColl = typeRegistry.resolve(TABULAR_SECTION_TYPE).orElse(null);
    var genericRow = typeRegistry.resolve(TABULAR_SECTION_ROW_TYPE).orElse(null);
    if (genericColl == null || genericRow == null) {
      return;
    }
    typeRegistry.registerExtension(rowRef, genericRow, FileType.BSL);
    typeRegistry.registerExtension(collRef, genericColl, FileType.BSL);
    typeRegistry.registerDefaultElementTypes(collRef, List.of(rowRef));
    typeRegistry.inheritCollectionTraits(collRef, genericColl, FileType.BSL);

    var valueTableRow = typeRegistry.resolve(CollectionReturnsSpecializer.VALUE_TABLE_ROW).orElse(null);
    typeRegistry.registerMemberOverride(collRef, () -> CollectionReturnsSpecializer.specialize(
      typeRegistry.getMembers(genericColl, FileType.BSL), genericRow, rowRef,
      CollectionReturnsSpecializer.unloadedRow(valueTableRow, columns)), FileType.BSL);
  }

  /**
   * Ленивый MemberSource, наследующий members у платформенного типа по точному
   * имени (без generic-плейсхолдера). Например, для коллекции {@code Справочники}
   * родитель — {@code СправочникиМенеджер}.
   */
  private void registerInheritedMembers(TypeRef target, String exactName) {
    typeRegistry.registerMemberSource(target, () -> {
      var parent = typeRegistry.resolve(exactName).orElse(null);
      if (parent == null) {
        return List.of();
      }
      return typeRegistry.getMembers(parent, FileType.BSL);
    }, FileType.BSL);
  }
  /**
   * Регистрирует специализации ВСЕХ зарегистрированных дженериков семейства
   * (с qualifiedName, начинающимся с {@code familyCore}) для конкретного
   * MD-имени. Single-placeholder обёртка: подставляет {@code mdName} во все
   * generic'и семейства с ровно одним placeholder'ом. Делегирует expander'у.
   * <p>
   * Покрывает всё семейство одним проходом: для Catalog это
   * {@code СправочникСсылка.<Имя>}, {@code СправочникОбъект.<Имя>},
   * {@code СправочникМенеджер.<Имя>}, {@code СправочникВыборка.<Имя>},
   * {@code СправочникСписок.<Имя>} и любые другие, которые HBK заведёт в
   * будущем.
   */
  private void registerFamilySpecializations(String familyCore, String mdName) {
    for (var generic : typeRegistry.findAllGenericsByFamilyCore(familyCore)) {
      var parameters = typeRegistry.getTypeParameters(generic);
      if (parameters.size() == 1) {
        genericExpander.registerFamilySpecializations(familyCore,
          Map.of(parameters.get(0), mdName));
      }
    }
  }

  /**
   * Убирает реквизиты иерархии там, где их не существует: у неиерархического справочника
   * нет ни {@code Родитель}, ни {@code ЭтоГруппа}, а при иерархии элементов нет
   * {@code ЭтоГруппа}.
   * <p>
   * Платформа объявляет их у всего семейства сразу, поэтому перекрыть их нечем — член
   * приходится именно убирать (см. {@link TypeRegistry#registerMemberSuppression}).
   * Подавление вешается на все типы семейства: где реквизита и так нет, оно безвредно.
   */
  private void registerHierarchySuppressions(MD md, String familyCore, String mdName) {
    var absent = StandardAttributesResolver.hierarchyAttributesAbsentIn(md);
    if (absent.isEmpty()) {
      return;
    }
    for (var generic : typeRegistry.findAllGenericsByFamilyCore(familyCore)) {
      var parameters = typeRegistry.getTypeParameters(generic);
      if (parameters.size() != 1) {
        continue;
      }
      var bindings = Map.of(parameters.get(0), mdName);
      typeRegistry.resolve(TypeRef.specialize(generic, bindings).qualifiedName())
        .filter(specialized -> !specialized.equals(generic))
        .ifPresent(specialized -> typeRegistry.registerMemberSuppression(specialized, absent, FileType.BSL));
    }
  }

  /**
   * Регистрирует expansion generic-property {@code <Имя значения>} на
   * {@code ПеречислениеМенеджер.<Имя перечисления>}: для каждого значения
   * перечисления из mdclasses ({@link Enum#getEnumValues()}) создаётся
   * материализованный member с подстановкой имени и наследованием HBK-меты
   * (accessMode = READ, availabilities, sinceVersion 8.0,
   * returnType = ПеречислениеСсылка.&lt;Имя перечисления&gt; со
   * специализированным placeholder'ом).
   */
  private void registerEnumValueExpansion(TypeRef managerRef, String familyCore, String enumName, Enum anEnum) {
    var values = anEnum.getEnumValues();
    if (values.isEmpty()) {
      return;
    }
    // Generic-источник: ПеречислениеМенеджер.<Имя перечисления>.
    var generic = typeRegistry.findAllGenericsByFamilyCore(familyCore + "Менеджер").stream()
      .findFirst()
      .orElse(null);
    if (generic == null) {
      return;
    }
    var parameters = typeRegistry.getTypeParameters(generic);
    if (parameters.size() != 1) {
      return;
    }
    var valueNames = new ArrayList<String>(values.size());
    for (var value : values) {
      var valueName = value.getName();
      if (!valueName.isBlank()) {
        valueNames.add(valueName);
      }
    }
    if (valueNames.isEmpty()) {
      return;
    }
    var typeBindings = Map.of(parameters.get(0), enumName);
    var memberExpansions = Map.<String, List<String>>of(memberPlaceholderName(typeRegistry, generic), valueNames);
    typeRegistry.registerMemberExpansion(managerRef, generic, typeBindings, memberExpansions,
      FileType.BSL);
  }

  /**
   * Регистрирует expansion generic-property {@code <Имя предопределённого>} на менеджер-типе
   * объекта-владельца предопределённых данных ({@code СправочникМенеджер.<Имя>},
   * {@code ПланСчетовМенеджер.<Имя>}, {@code ПланВидовХарактеристикМенеджер.<Имя>},
   * {@code ПланВидовРасчетаМенеджер.<Имя>}, {@code ПланОбменаМенеджер.<Имя>}): для каждого
   * предопределённого значения из mdclasses ({@link PredefinedDataOwner#getPredefinedValues()})
   * создаётся материализованный member. Иерархия (группы) разворачивается в плоский список имён —
   * в 1С предопределённые группы доступны по имени так же, как элементы
   * ({@code Справочники.X.ИмяГруппы}).
   */
  private void registerPredefinedValueExpansion(TypeRef managerRef, String familyCore, String mdName,
                                                PredefinedDataOwner owner) {
    var values = owner.getPredefinedValues();
    if (values.isEmpty()) {
      return;
    }
    var valueNames = new ArrayList<String>();
    collectPredefinedNames(values, valueNames);
    if (valueNames.isEmpty()) {
      return;
    }
    // Тип предопределённого значения — ссылка объекта (Справочники.X.Россия -> СправочникСсылка.X),
    // что даёт дальнейший автокомплит по реквизитам. Если ссылочный тип почему-то не зарегистрирован,
    // оставляем member без типа — имя в автокомплите всё равно появится.
    var refType = typeRegistry.resolve(familyCore + "Ссылка." + mdName).orElse(null);
    var members = valueNames.stream()
      .distinct()
      .map(valueName -> refType == null
        ? MemberDescriptor.property(valueName)
        : MemberDescriptor.property(valueName, refType))
      .toList();
    // В отличие от значений перечислений (placeholder generic-члена менеджера), у менеджеров
    // справочников/планов нет placeholder'а под предопределённые — регистрируем members напрямую.
    typeRegistry.registerMemberSource(managerRef, () -> members, FileType.BSL);
  }

  /**
   * Рекурсивно собирает имена предопределённых значений (включая вложенные группы) в плоский список.
   */
  private static void collectPredefinedNames(List<PredefinedValue> values, List<String> target) {
    for (var value : values) {
      var name = value.getName();
      if (!name.isBlank()) {
        target.add(name);
      }
      collectPredefinedNames(value.getChildItems(), target);
    }
  }

  /**
   * Графы журнала документов как property-члены на типе журнала
   * ({@code ЖурналДокументов.<имя>}). Источник имён и типов — mdclasses
   * ({@link DocumentJournal#getColumns()}); все колонки реализуют
   * {@link Attribute}, поэтому материализуются через {@link #buildAttributeMembers(List)}.
   */
  private void registerDocumentJournalColumnMembers(DocumentJournal journal, String familyCore, String name) {
    var columns = journal.getColumns();
    if (columns.isEmpty()) {
      return;
    }
    var specName = familyCore + "." + name;
    var specRef = typeRegistry.resolve(specName).orElse(null);
    if (specRef == null) {
      return;
    }
    var captured = List.copyOf(columns);
    typeRegistry.registerMemberSource(specRef, () -> mdoMembers.attributeMembers(captured), FileType.BSL);
  }

  /**
   * Производные/вложенные типы, чьё семейство (familyCore) не совпадает с
   * именем семейства родительского MD: подсемейства плана видов расчёта
   * ({@code БазовыеВидыРасчета.<Имя ПВР>} и аналоги «Ведущие»/«Вытесняющие»)
   * и перерасчёты регистра расчёта ({@code Перерасчет.<имя>}).
   */
  private void registerDerivedSpecializations(MD md, String mdName) {
    if (md instanceof ChartOfCalculationTypes) {
      registerFamilySpecializations("БазовыеВидыРасчета", mdName);
      registerFamilySpecializations("ВедущиеВидыРасчета", mdName);
      registerFamilySpecializations("ВытесняющиеВидыРасчета", mdName);
    } else if (md instanceof CalculationRegister cr) {
      registerRecalculationSpecializations(cr);
    } else {
      // Прочие MDO производных специализаций не имеют.
    }
  }

  private void registerRecalculationSpecializations(CalculationRegister cr) {
    for (var recalc : cr.getRecalculations()) {
      var recalcName = recalc.getName();
      if (!recalcName.isBlank()) {
        registerFamilySpecializations("Перерасчет", recalcName);
      }
    }
  }

  /**
   * Имя placeholder'а в member-template'е generic'а. Извлекается из единственного
   * generic-property типа-менеджера (для {@code ПеречислениеМенеджер.<Имя перечисления>}
   * это {@code "Имя значения"}). Источник — bsl-context-разобранное имя члена.
   */
  static String memberPlaceholderName(TypeRegistry typeRegistry, TypeRef generic) {
    return typeRegistry.getMembers(generic, FileType.BSL).stream()
      .filter(MemberDescriptor::generic)
      .findFirst()
      .flatMap(m -> ContextNames.placeholders(m.bilingualName().primary()).stream().findFirst())
      .map(Placeholder::name)
      .orElse("");
  }

  private TypeRef registerWithAlias(String qualifiedRu, String qualifiedEn) {
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (!qualifiedEn.isBlank() && !qualifiedEn.equals(qualifiedRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    return ref;
  }

}
