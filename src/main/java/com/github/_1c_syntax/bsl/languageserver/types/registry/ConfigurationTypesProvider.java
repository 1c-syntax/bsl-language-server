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
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.HTTPService;
import com.github._1c_syntax.bsl.mdo.IntegrationService;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceMethod;
import com.github._1c_syntax.bsl.mdo.children.IntegrationServiceChannel;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import com.github._1c_syntax.bsl.mdo.ExternalDataSource;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.MDObject;
import com.github._1c_syntax.bsl.mdo.PredefinedDataOwner;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.children.PredefinedValue;
import com.github._1c_syntax.bsl.mdo.children.StandardAttribute;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MultiName;
import com.github._1c_syntax.bsl.types.ValueType;
import com.github._1c_syntax.bsl.types.value.PrimitiveValueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class ConfigurationTypesProvider {

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

  private final TypeRegistry typeRegistry;
  private final ServerContextProvider serverContextProvider;
  private final GlobalScopeProvider globalScopeProvider;
  private final LanguageServerConfiguration configuration;
  private final MetadataCollectionSpecializer metadataCollectionSpecializer;
  private final ConfigurationGenericExpander genericExpander;
  private final ApplicationEventPublisher eventPublisher;

  private final AtomicBoolean registered = new AtomicBoolean(false);

  @EventListener
  public void handleEvent(ServerContextPopulatedEvent event) {
    tryRegister();
  }

  /**
   * Идемпотентная регистрация. Вызывается при заполнении ServerContext (после
   * того как платформенные generic-типы загружены через {@code BslContextPlatformTypesProvider}
   * — это критично для {@code registerFamilySpecializations}, который ищет
   * generic'и по familyCore и без них пропускает специализации). Повторные
   * вызовы — no-op.
   */
  public void tryRegister() {
    if (registered.get()) {
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
    if (!registered.compareAndSet(false, true)) {
      return;
    }
    var children = configuration.getChildrenByMdoRef().values();
    LOGGER.debug("ConfigurationTypesProvider[{}]: registering {} MD objects", workspaceUri, children.size());
    register(children);
    registerServiceModuleEventSpecializations(children);
    eventPublisher.publishEvent(new ConfigurationTypesRegisteredEvent(serverContext));
  }

  /**
   * Развёртывает generic-события платформенных типов модулей сервисов в
   * конкретные имена обработчиков, объявленных в конфигурации:
   * <ul>
   *   <li>{@code Модуль HTTP-сервиса.<Имя обработчика>} ← {@code HTTPService.urlTemplates.methods.handler}
   *       с сигнатурой {@code (Запрос: HTTPСервисЗапрос)};</li>
   *   <li>{@code Модуль Web-сервиса.<Имя обработчика>} ← {@code WebService.operations.procedureName}
   *       с параметрами {@code WebServiceOperation.parameters};</li>
   *   <li>{@code Модуль сервиса интеграции.<Имя обработчика полученного сообщения>}
   *       ← {@code IntegrationService.integrationServiceChannels.receiveMessageProcessing}.</li>
   * </ul>
   * Generic-события прилетают из HBK с placeholder'ом в имени; здесь они
   * материализуются именами из mdclasses + кастомные сигнатуры подменяются на
   * реальные параметры из XML.
   */
  private void registerServiceModuleEventSpecializations(Iterable<MD> children) {
    var httpEvents = new ArrayList<HandlerSpec>();
    var webEvents = new ArrayList<HandlerSpec>();
    var integrationEvents = new ArrayList<HandlerSpec>();
    for (var md : children) {
      collectHttpHandlers(md, httpEvents);
      collectWebProcedures(md, webEvents);
      collectIntegrationHandlers(md, integrationEvents);
    }
    registerServiceHandlerEvents("Модуль HTTP-сервиса", "Имя обработчика", httpEvents);
    registerServiceHandlerEvents("Модуль Web-сервиса", "Имя обработчика", webEvents);
    registerServiceHandlerEvents("Модуль сервиса интеграции",
      "Имя обработчика полученного сообщения", integrationEvents);
  }

  private void collectHttpHandlers(MD md, List<HandlerSpec> sink) {
    if (!(md instanceof HTTPService http)) {
      return;
    }
    http.getUrlTemplates().forEach(tpl -> tpl.getMethods().forEach((HTTPServiceMethod m) -> {
      if (!m.getHandler().isBlank()) {
        sink.add(new HandlerSpec(m.getHandler(), httpServiceMethodSignature()));
      }
    }));
  }

  private void collectWebProcedures(MD md, List<HandlerSpec> sink) {
    if (!(md instanceof WebService web)) {
      return;
    }
    web.getOperations().forEach((WebServiceOperation op) -> {
      if (!op.getProcedureName().isBlank()) {
        sink.add(new HandlerSpec(op.getProcedureName(), webOperationSignature(op)));
      }
    });
  }

  private void collectIntegrationHandlers(MD md, List<HandlerSpec> sink) {
    if (!(md instanceof IntegrationService isvc)) {
      return;
    }
    isvc.getIntegrationServiceChannels().forEach((IntegrationServiceChannel ch) -> {
      if (!ch.getReceiveMessageProcessing().isBlank()) {
        sink.add(new HandlerSpec(ch.getReceiveMessageProcessing(),
          integrationChannelSignature()));
      }
    });
  }

  /**
   * Материализует generic-event типа по placeholder'у и подменяет signatures
   * на сигнатуру с реальными параметрами обработчика из mdclasses.
   * <p>
   * Описание/двуязычие/sinceVersion event'а наследуются от HBK-шаблона: общий
   * для всего семейства источник правды.
   */
  private void registerServiceHandlerEvents(String typeQualifiedName, String placeholder,
                                            List<HandlerSpec> specs) {
    if (specs.isEmpty()) {
      return;
    }
    var typeRef = typeRegistry.resolve(typeQualifiedName).orElse(null);
    if (typeRef == null) {
      return;
    }
    var names = specs.stream().map(HandlerSpec::name).distinct().toList();
    var templates = typeRegistry.expandedMembers(typeRef, Map.of(),
      Map.of(placeholder, names), FileType.BSL);
    if (templates.isEmpty()) {
      return;
    }
    var sigByName = specs.stream()
      .collect(Collectors.toMap(HandlerSpec::name, HandlerSpec::signature, (a, b) -> a));
    var withSignatures = templates.stream()
      .map((MemberDescriptor m) -> {
        var sig = sigByName.get(m.name());
        return sig == null ? m : m.withSignatures(List.of(sig));
      })
      .toList();
    typeRegistry.registerMemberSource(typeRef, () -> withSignatures, FileType.BSL);
  }

  /** Сигнатура HTTP-обработчика: один параметр {@code Запрос} типа {@code HTTPСервисЗапрос}. */
  private SignatureDescriptor httpServiceMethodSignature() {
    var requestRef = typeRegistry.resolve("HTTPСервисЗапрос").orElse(TypeRef.UNKNOWN);
    var param = new ParameterDescriptor(
      BilingualString.of("Запрос", "Request"),
      TypeSet.of(requestRef), false, BilingualString.EMPTY, "");
    return new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");
  }

  /**
   * Сигнатура обработчика операции Web-сервиса: имена параметров из XML.
   * Типы пока не сопоставляем — XSD-тип параметра ({@code {ns}type}) требует
   * отдельной мапы в BSL-типы; до её появления оставляем пусто.
   */
  private static SignatureDescriptor webOperationSignature(WebServiceOperation op) {
    var params = op.getParameters().stream()
      .map(p -> new ParameterDescriptor(
        BilingualString.of(p.getName(), p.getName()),
        TypeSet.EMPTY, false, BilingualString.EMPTY, ""))
      .toList();
    return new SignatureDescriptor(params, TypeSet.EMPTY, "");
  }

  /** Сигнатура обработчика канала: один параметр {@code Сообщение}. */
  private static SignatureDescriptor integrationChannelSignature() {
    var param = new ParameterDescriptor(
      BilingualString.of("Сообщение", "Message"),
      TypeSet.EMPTY, false, BilingualString.EMPTY, "");
    return new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");
  }

  /** Пара «имя обработчика → его сигнатура» для регистрации event'ов на типе модуля. */
  private record HandlerSpec(String name, SignatureDescriptor signature) {
  }

  private void register(Iterable<MD> children) {
    Map<MDOType, List<MemberDescriptor>> collectionMembersByType = new HashMap<>();

    var commonAttributes = collectCommonAttributes(children);
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
   * Enum/Journal/регистров, алиасов и member'а для namespace.
   *
   * @return {@code true} если MD относится к {@link #MANAGER_TYPES} и был зарегистрирован.
   */
  private boolean processMdoChild(MD md, List<CommonAttribute> commonAttributes,
                                  Map<MDOType, List<MemberDescriptor>> collectionMembersByType) {
    var mdoType = md.getMdoType();
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
    var registerChildren = registerChildrenOf(md);
    if (registerChildren != null) {
      registerRegisterRecordExpansion(familyCore, name, registerChildren);
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
      typeRegistry.registerAsGlobalProperty(ref, FileType.BSL);

      // Платформенные методы коллекции-менеджера (СправочникиМенеджер,
      // ДокументыМенеджер) — уровня всех справочников/документов, например
      // `ТипВсеСсылки()`. Имя фиксированное (без generic-плейсхолдера).
      registerInheritedMembers(ref, collectionRu + "Менеджер");
      collections++;
    }
    return collections;
  }

  /** MDOType'ы, у которых есть «объектная» обёртка (СправочникОбъект.X / ДокументОбъект.X / ...). */
  private static final Set<MDOType> OBJECT_TYPES = Set.of(
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
   * Зарегистрировать «объектную» и «ссылочную» обёртки метаобъекта (например,
   * {@code СправочникОбъект.X}, {@code СправочникСсылка.X}) и навесить на них
   * членов из реквизитов метаданных.
   * <p>
   * Сейчас обрабатываются только атрибуты (как PROPERTY). Табчасти — отдельная
   * задача (требуют регистрации СправочникТабличнаяЧастьСтрока.X.Y типа).
   */
  private void registerObjectAndRefTypes(MD md,
                                         MDOType mdoType,
                                         String name,
                                         MultiName fullName,
                                         List<CommonAttribute> commonAttributes) {
    if (!OBJECT_TYPES.contains(mdoType)) {
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
    var commonForMd = applicableCommonAttributes(md, commonAttributes);

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

    // У platform-generic'ов Object и Ref на одних и тех же стандартных
    // реквизитах разные accessMode (например, Дата мутабельна на Объекте,
    // но read-only на Ссылке). Поэтому собираем метаданные раздельно по
    // семействам и регистрируем разные MemberSource'ы — описания общие
    // и шарятся через collectPlatformMemberDescriptions.
    MemberSource refSource = () -> {
      var fresh = new ArrayList<MemberDescriptor>();
      fresh.addAll(buildAttributeMembers(capturedAttributes,
        collectPlatformMemberDescriptions(capturedFullRu),
        collectPlatformMemberMetadata(capturedFullRu + "Ссылка")));
      fresh.addAll(buildCommonAttributeMembers(capturedCommon));
      return fresh;
    };
    MemberSource objectSource = () -> {
      var fresh = new ArrayList<MemberDescriptor>();
      fresh.addAll(buildAttributeMembers(capturedAttributes,
        collectPlatformMemberDescriptions(capturedFullRu),
        collectPlatformMemberMetadata(capturedFullRu + "Объект")));
      fresh.addAll(buildCommonAttributeMembers(capturedCommon));
      return fresh;
    };
    typeRegistry.registerMemberSource(objectRef, objectSource, FileType.BSL);
    typeRegistry.registerMemberSource(refRef, refSource, FileType.BSL);

    // Дополнительные mdclasses-specific аттрибуты, не входящие в getAllAttributes:
    // признаки учёта и флаги учёта субконто для плана счетов.
    registerMdoSpecificAttributeMembers(md, objectRef, refRef);

    // Табличные части: регистрируем пару типов <prefix>ТабличнаяЧасть(Строка)?.<MD>.<TS>
    // и добавляем member <TS-name> на объектный тип.
    registerTabularSections(md, name, fullRu, fullEn, objectRef);
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
    MemberSource source = () -> buildAttributeMembers(captured);
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
      if (!tsAttributes.isEmpty()) {
        // Аналогично основным реквизитам: лямбда вызывает buildAttributeMembers
        // на каждый getMembers, поэтому язык читается per-call и подхватывает
        // workspace/didChangeConfiguration.
        MemberSource columnSource = () -> buildAttributeMembers(tsAttributes);
        typeRegistry.registerMemberSource(rowRef, columnSource, FileType.BSL);
        // Для удобства dot-completion'а ТЧ-коллекция тоже показывает колонки —
        // обращение `ТЧ.Колонка` к коллекции встречается в коде (через индекс/первую строку).
        typeRegistry.registerMemberSource(collRef, columnSource, FileType.BSL);
      }

      tsMembers.add(MemberDescriptor.property(tsName, collRef));
    }
    if (!tsMembers.isEmpty()) {
      var immutableTs = List.copyOf(tsMembers);
      typeRegistry.registerMemberSource(objectRef, () -> immutableTs, FileType.BSL);
    }
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
   * Триплет имён детей регистра (измерения/ресурсы/реквизиты), полученный
   * из конкретного MD-класса регистра. {@code null} — для не-регистров.
   */
  record RegisterChildren(List<? extends Attribute> dimensions,
                          List<? extends Attribute> resources,
                          List<? extends Attribute> attributes) {
  }

  static @Nullable RegisterChildren registerChildrenOf(MD md) {
    return switch (md) {
      case InformationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), r.getAttributes());
      case AccumulationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), r.getAttributes());
      case AccountingRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), r.getAttributes());
      case CalculationRegister r ->
        new RegisterChildren(r.getDimensions(), r.getResources(), r.getAttributes());
      default -> null;
    };
  }

  /**
   * Регистрирует expansion generic-property {@code <Имя измерения>/<Имя ресурса>/
   * <Имя реквизита>} на типе записи регистра ({@code РегистрСведенийЗапись.<Имя>}
   * и аналоги для других семейств регистров). Имена детей берутся из mdclasses;
   * мета — наследуется от HBK-template'ов.
   *
   * @param familyCore ru-часть имени семейства ({@code "РегистрСведений"} и т.п.)
   * @param regName    имя регистра в конфигурации
   * @param children   измерения/ресурсы/реквизиты регистра
   */
  private void registerRegisterRecordExpansion(String familyCore, String regName, RegisterChildren children) {
    var generic = typeRegistry.findAllGenericsByFamilyCore(familyCore + "Запись").stream()
      .findFirst()
      .orElse(null);
    if (generic == null) {
      return;
    }
    var parameters = typeRegistry.getTypeParameters(generic);
    if (parameters.size() != 1) {
      return;
    }
    var typeBindings = Map.of(parameters.get(0), regName);
    var specializedName = TypeRef.specialize(generic, typeBindings).qualifiedName();
    var specialized = typeRegistry.resolve(specializedName).orElse(null);
    if (specialized == null) {
      return;
    }
    var expansions = new LinkedHashMap<String, List<String>>();
    putAttributeNames(expansions, "Имя измерения", children.dimensions());
    putAttributeNames(expansions, "Имя ресурса", children.resources());
    putAttributeNames(expansions, "Имя реквизита", children.attributes());
    if (expansions.isEmpty()) {
      return;
    }
    typeRegistry.registerMemberExpansion(specialized, generic, typeBindings, expansions, FileType.BSL);
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
    typeRegistry.registerMemberSource(specRef, () -> buildAttributeMembers(captured), FileType.BSL);
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

  /** Кладёт в expansion-map имена непустых атрибутов под ключом-placeholder'ом. */
  static void putAttributeNames(Map<String, List<String>> sink, String placeholder,
                                List<? extends Attribute> attributes) {
    var names = attributes.stream()
      .map(Attribute::getName)
      .filter(n -> !n.isBlank())
      .toList();
    if (!names.isEmpty()) {
      sink.put(placeholder, names);
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

  private List<MemberDescriptor> buildAttributeMembers(List<? extends Attribute> attributes,
                                                       Map<String, BilingualString> platformDescriptions,
                                                       Map<String, PlatformMetadata> platformMetadata) {
    if (attributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(attributes.size());
    for (var attribute : attributes) {
      var bilingualName = attributeBilingualName(attribute);
      if (bilingualName.isEmpty()) {
        continue;
      }
      // По ru-имени матчим описания/мета платформы — словарь HBK именован по-русски.
      var lc = bilingualName.primary().toLowerCase(Locale.ROOT);
      var description = platformDescriptions.getOrDefault(lc, BilingualString.EMPTY);
      var meta = platformMetadata.getOrDefault(lc, PlatformMetadata.EMPTY);
      var returnTypes = resolveAttributeReturnTypes(attribute);
      var primaryName = bilingualName.primary();
      MemberDescriptor descriptor;
      if (returnTypes.isEmpty()) {
        descriptor = MemberDescriptor.property(primaryName);
      } else if (returnTypes.size() == 1) {
        descriptor = MemberDescriptor.property(primaryName, returnTypes.refs().iterator().next(), "");
      } else {
        descriptor = MemberDescriptor.property(primaryName, returnTypes, "");
      }
      descriptor = descriptor.withBilingualName(bilingualName);
      if (!description.isEmpty()) {
        descriptor = descriptor.withBilingualDescription(description);
      }
      if (!meta.isEmpty()) {
        descriptor = descriptor.withMetadata(meta);
      }
      result.add(descriptor);
    }
    return result;
  }

  /** Overload без подмеса описаний/метаданных — для случаев без контекста MD (табчасти). */
  private List<MemberDescriptor> buildAttributeMembers(List<? extends Attribute> attributes) {
    return buildAttributeMembers(attributes, Map.of(), Map.of());
  }

  /**
   * Собирает {@code name(lower) → }{@link BilingualString} (ru + en описание)
   * для платформенных generic-типов {@code <fullRu>Ссылка.<...>} и
   * {@code <fullRu>Объект.<...>} — это HBK-описания, подходящие к стандартным
   * реквизитам соответствующего MD. Двуязычность нужна, чтобы hover на
   * стандартном реквизите (Дата/Ссылка/...) показывал описание в текущей
   * локали, а не всегда ru-вариант.
   */
  private Map<String, BilingualString> collectPlatformMemberDescriptions(String fullRu) {
    var result = new HashMap<String, BilingualString>();
    addPlatformDescriptionsTo(result, fullRu + "Ссылка");
    addPlatformDescriptionsTo(result, fullRu + "Объект");
    return result;
  }

  private void addPlatformDescriptionsTo(Map<String, BilingualString> sink, String familyPrefix) {
    var generic = typeRegistry.resolveGenericByPrefix(familyPrefix).orElse(null);
    if (generic == null) {
      return;
    }
    for (var m : typeRegistry.getMembers(generic, FileType.BSL)) {
      if (m.generic()) {
        continue;
      }
      var desc = m.bilingualDescription();
      if (desc.isEmpty()) {
        continue;
      }
      sink.putIfAbsent(m.name().toLowerCase(Locale.ROOT), desc);
    }
  }

  /**
   * Собирает {@code name(lower) → }{@link PlatformMetadata} для конкретного
   * generic-семейства (например, {@code "СправочникСсылка"} или
   * {@code "ДокументОбъект"}) — это метаданные стандартных реквизитов
   * (accessMode/sinceVersion/availabilities и т.п.), которые иначе теряются
   * при сборке member'а из mdclasses через {@link #buildAttributeMembers}.
   * <p>
   * Раздельный сбор для Ссылки и Объекта принципиален: у одного и того же
   * стандартного реквизита (например, {@code Дата} документа) разные
   * {@code accessMode} в этих семействах (на Объекте мутабельно, на Ссылке —
   * только чтение).
   */
  private Map<String, PlatformMetadata> collectPlatformMemberMetadata(String familyPrefix) {
    var result = new HashMap<String, PlatformMetadata>();
    var generic = typeRegistry.resolveGenericByPrefix(familyPrefix).orElse(null);
    if (generic == null) {
      return result;
    }
    for (var m : typeRegistry.getMembers(generic, FileType.BSL)) {
      if (m.generic()) {
        continue;
      }
      var meta = m.metadata();
      if (meta.isEmpty()) {
        continue;
      }
      result.putIfAbsent(m.name().toLowerCase(Locale.ROOT), meta);
    }
    return result;
  }

  /**
   * Общие реквизиты, применимые к конкретному MDObject. Если MD явно присутствует в
   * составе общего реквизита, используется его персональный режим; иначе откатываемся
   * к {@link CommonAttribute#getAutoUse()}. Включаются режимы USE/USE_WITH_WARNINGS.
   */
  private static List<CommonAttribute> applicableCommonAttributes(MD md, List<CommonAttribute> all) {
    if (all.isEmpty()) {
      return List.of();
    }
    var mdoRef = md.getMdoReference();
    var result = new ArrayList<CommonAttribute>();
    for (var ca : all) {
      var effective = ca.contains(mdoRef) ? ca.useMode(mdoRef) : ca.getAutoUse();
      if (effective == com.github._1c_syntax.bsl.mdo.support.UseMode.USE
        || effective == com.github._1c_syntax.bsl.mdo.support.UseMode.USE_WITH_WARNINGS) {
        result.add(ca);
      }
    }
    return result;
  }

  private List<MemberDescriptor> buildCommonAttributeMembers(List<CommonAttribute> commonAttributes) {
    if (commonAttributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(commonAttributes.size());
    for (var ca : commonAttributes) {
      var attrName = ca.getName();
      if (attrName.isBlank()) {
        continue;
      }
      var returnTypes = resolveCommonAttributeReturnTypes(ca);
      if (returnTypes.isEmpty()) {
        result.add(MemberDescriptor.property(attrName));
      } else if (returnTypes.size() == 1) {
        result.add(MemberDescriptor.property(attrName, returnTypes.refs().iterator().next()));
      } else {
        result.add(MemberDescriptor.property(attrName, returnTypes, ""));
      }
    }
    return result;
  }

  private TypeSet resolveCommonAttributeReturnTypes(CommonAttribute ca) {
    var valueType = ca.getValueType();
    if (valueType.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new java.util.LinkedHashSet<TypeRef>();
    for (var vt : valueType.getTypes()) {
      var resolved = resolveValueType(vt);
      if (resolved != null) {
        refs.add(resolved);
      }
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  /**
   * Двуязычное имя реквизита. Стандартные реквизиты (Дата/Номер/Ссылка/...)
   * хранят оба написания в {@link MultiName} —
   * собираем {@link BilingualString} ровно из этой пары, чтобы:
   * <ul>
   *   <li>{@code MemberDescriptor.matches(name)} находил член по любому
   *       написанию (hover, диагностика {@code AssignToReadOnly}, и т.п.);</li>
   *   <li>completion выдавал имя в нужной локали через
   *       {@code displayName(scriptVariant)}, без необходимости держать два
   *       параллельных дескриптора на одном члене.</li>
   * </ul>
   * Кастомные реквизиты ({@link com.github._1c_syntax.bsl.mdo.children.ObjectAttribute})
   * имеют только одно имя — возвращаем моноязычный {@link BilingualString#of(String)}.
   */
  private static BilingualString attributeBilingualName(Attribute attribute) {
    if (attribute instanceof StandardAttribute std) {
      var fullName = std.getFullName();
      if (!fullName.isEmpty()) {
        var ru = fullName.get("ru");
        var en = fullName.get("en");
        if (!ru.isBlank() && !en.isBlank()) {
          return BilingualString.of(ru, en);
        }
        if (!ru.isBlank()) {
          return BilingualString.of(ru);
        }
        if (!en.isBlank()) {
          return BilingualString.of("", en);
        }
      }
    }
    var name = attribute.getName();
    return name.isBlank() ? BilingualString.EMPTY : BilingualString.of(name);
  }

  /**
   * Получить набор {@link TypeRef} типов значения реквизита (union для
   * composite-типа из нескольких {@code v8:Type}).
   */
  private TypeSet resolveAttributeReturnTypes(Attribute attribute) {
    var valueType = attribute.getValueType();
    if (valueType.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new java.util.LinkedHashSet<TypeRef>();
    for (var vt : valueType.getTypes()) {
      var resolved = resolveValueType(vt);
      if (resolved != null) {
        refs.add(resolved);
      }
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  @Nullable
  private TypeRef resolveValueType(ValueType vt) {
    if (vt instanceof PrimitiveValueType primitive) {
      return typeRegistry.resolve(primitive.fullName().getRu()).orElse(null);
    }
    // V8 / METADATA / UNKNOWN — пока не поддерживаем точно; resolve по имени даст
    // частичное покрытие для V8-типов, имена которых совпадают с регистрационными.
    return typeRegistry.resolve(vt.fullName().getRu()).orElse(null);
  }
}
