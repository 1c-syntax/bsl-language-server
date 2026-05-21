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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.mdo.children.ObjectTabularSection;
import com.github._1c_syntax.bsl.mdo.children.StandardAttribute;
import com.github._1c_syntax.bsl.mdo.support.AttributeKind;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ValueType;
import com.github._1c_syntax.bsl.types.value.PrimitiveValueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
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
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
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
    MDOType.SEQUENCE
  );

  private final TypeRegistry typeRegistry;
  private final ServerContextProvider serverContextProvider;
  private final GlobalScopeProvider globalScopeProvider;
  private final LanguageServerConfiguration configuration;

  private final AtomicBoolean registered = new AtomicBoolean(false);

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    tryRegister();
  }

  /**
   * Идемпотентная регистрация. Вызывается при первом изменении документа после
   * загрузки конфигурации; повторные вызовы — no-op.
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
    if (configuration == null || configuration.isEmpty()) {
      return;
    }
    if (!registered.compareAndSet(false, true)) {
      return;
    }
    var children = configuration.getChildrenByMdoRef().values();
    LOGGER.debug("ConfigurationTypesProvider[{}]: registering {} MD objects", workspaceUri, children.size());
    register(children);
  }

  private void register(Iterable<MD> children) {
    int count = 0;
    Map<MDOType, List<MemberDescriptor>> collectionMembersByType = new HashMap<>();

    // Все общие реквизиты конфигурации — нужны при регистрации объектных/ссылочных
    // обёрток MD: подмешиваем те, что включены для конкретного MDObject'а.
    var commonAttributes = new ArrayList<CommonAttribute>();
    for (var md : children) {
      if (md instanceof CommonAttribute ca) {
        commonAttributes.add(ca);
      }
    }

    for (var md : children) {
      var mdoType = md.getMdoType();
      if (!MANAGER_TYPES.contains(mdoType)) {
        continue;
      }
      var groupRu = mdoType.fullGroupName().getRu();
      var groupEn = mdoType.fullGroupName().getEn();
      var name = md.getName();
      if (name == null || name.isBlank()) {
        continue;
      }

      // Каноническая регистрация — менеджер-обёртка (например, СправочникМенеджер.Контрагенты).
      // На неё же навешивает методы ConfigurationModuleMembersProvider при разборе ManagerModule.bsl,
      // т.е. чейн `Справочники.Контрагенты.МетодМенеджера` резолвится через единый TypeRef.
      // Если у MDOType нет «коротко-именованной» формы (fullName), то используем групповую форму как основу.
      var fullName = mdoType.fullName();
      String managerRu;
      String managerEn;
      String managerFamilyPrefix = null;
      if (fullName != null && fullName.getRu() != null && !fullName.getRu().isBlank()) {
        managerRu = fullName.getRu() + "Менеджер." + name;
        managerFamilyPrefix = fullName.getRu() + "Менеджер";
        var fullEn = fullName.getEn();
        managerEn = (fullEn == null || fullEn.isBlank()) ? null : fullEn + "Manager." + name;
      } else {
        managerRu = groupRu + "." + name;
        managerEn = groupEn == null || groupEn.equals(groupRu) ? null : groupEn + "." + name;
      }

      var ref = typeRegistry.registerConfigurationType(managerRu);
      if (managerEn != null && !managerEn.equals(managerRu)) {
        typeRegistry.registerConfigurationTypeAlias(managerEn, ref);
      }

      // Подмешивание платформенных методов менеджера-семейства, ссылки,
      // объекта, выборки и т.п. для конкретного MD-имени делается единым
      // вызовом ниже через registerFamilySpecializations(fullRu, name)
      // в registerObjectAndRefTypes.
      registerObjectAndRefTypes(md, mdoType, name, fullName, commonAttributes);

      // Дополнительные алиасы «коллекция.Имя» для совместимости и для случаев,
      // когда пользователь обращается напрямую (например, Hover на `Справочники.Контрагенты`).
      var collectionAliasRu = groupRu + "." + name;
      if (!collectionAliasRu.equals(managerRu)) {
        typeRegistry.registerConfigurationTypeAlias(collectionAliasRu, ref);
      }
      globalScopeProvider.registerConfigurationQualifiedName(collectionAliasRu);
      if (groupEn != null && !groupEn.equals(groupRu)) {
        var collectionAliasEn = groupEn + "." + name;
        if (!collectionAliasEn.equals(managerRu) && !collectionAliasEn.equals(managerEn)) {
          typeRegistry.registerConfigurationTypeAlias(collectionAliasEn, ref);
        }
        globalScopeProvider.registerConfigurationQualifiedName(collectionAliasEn);
      }

      collectionMembersByType
        .computeIfAbsent(mdoType, k -> new ArrayList<>())
        .add(MemberDescriptor.property(name, ref));
      count++;
    }

    int collections = 0;
    for (var entry : collectionMembersByType.entrySet()) {
      var mdoType = entry.getKey();
      var members = entry.getValue();
      var groupRu = mdoType.fullGroupName().getRu();
      var groupEn = mdoType.fullGroupName().getEn();
      var collectionRu = groupRu; // "Справочники", "Документы", ...
      var collectionEn = groupEn; // "Catalogs", "Documents", ...
      var ref = typeRegistry.registerConfigurationType(collectionRu);
      if (collectionEn != null && !collectionEn.equals(collectionRu)) {
        typeRegistry.registerConfigurationTypeAlias(collectionEn, ref);
      }
      typeRegistry.registerMemberSource(ref, () -> members, LanguageScope.BSL);
      typeRegistry.registerAsGlobalProperty(ref);

      // Подмешиваем платформенные методы коллекции-менеджера (СправочникиМенеджер,
      // ДокументыМенеджер и т.п.) — это методы уровня всех справочников/документов,
      // например `ТипВсеСсылки()`. Имя фиксированное (без generic-плейсхолдера).
      registerInheritedMembers(ref, groupRu + "Менеджер");

      collections++;
    }

    LOGGER.debug("Configuration types registered: {}, collection global properties: {}", count, collections);
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
                                         com.github._1c_syntax.bsl.types.MultiName fullName,
                                         List<CommonAttribute> commonAttributes) {
    if (!OBJECT_TYPES.contains(mdoType) || fullName == null) {
      return;
    }
    var fullRu = fullName.getRu();
    var fullEn = fullName.getEn();
    if (fullRu == null || fullRu.isBlank()) {
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
    var objectEn = (fullEn == null || fullEn.isBlank()) ? null : fullEn + "Object." + name;
    var objectRef = registerWithAlias(objectRu, objectEn);

    var refRu = fullRu + "Ссылка." + name;
    var refEn = (fullEn == null || fullEn.isBlank()) ? null : fullEn + "Ref." + name;
    var refRef = registerWithAlias(refRu, refEn);

    // Singular alias `Справочник.X` / `Catalog.X` ведёт на ссылочный тип:
    // соответствует семантике стандартных описаний 1С (`См. Справочник.X.Реквизит`
    // — тип реквизита справочника).
    var singularRu = fullRu + "." + name;
    if (!singularRu.equals(refRu)) {
      typeRegistry.registerConfigurationTypeAlias(singularRu, refRef);
    }
    if (fullEn != null && !fullEn.isBlank()) {
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
    typeRegistry.registerMemberSource(objectRef, objectSource, LanguageScope.BSL);
    typeRegistry.registerMemberSource(refRef, refSource, LanguageScope.BSL);

    // Подмешиваем members generic-платформенного семейства для конкретного
    // MD-имени. Покрывает все дженерики, чьё qualifiedName начинается с
    // {fullRu}: {fullRu}Ссылка.<...>, {fullRu}Объект.<...>,
    // {fullRu}Менеджер.<...>, {fullRu}Выборка.<...>, {fullRu}Список.<...>
    // и т.п. Для уже зарегистрированных типов (refRef/objectRef/managerRef)
    // добавляется только MemberSource; для новых (Выборка/Список/…) ещё и
    // интернируется TypeRef. Резолв ленивый — не зависит от порядка
    // инициализации платформенных провайдеров.
    registerFamilySpecializations(fullRu, name);

    // Табличные части: регистрируем пару типов <prefix>ТабличнаяЧасть(Строка)?.<MD>.<TS>
    // и добавляем member <TS-name> на объектный тип.
    registerTabularSections(md, name, fullRu, fullEn, objectRef);
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
    if (sections == null || sections.isEmpty()) {
      return;
    }
    var tsMembers = new ArrayList<MemberDescriptor>(sections.size());
    for (var ts : sections) {
      var tsName = ts.getName();
      if (tsName == null || tsName.isBlank()) {
        continue;
      }
      var rowRu = fullRu + "ТабличнаяЧастьСтрока." + name + "." + tsName;
      var rowEn = (fullEn == null || fullEn.isBlank()) ? null
        : fullEn + "TabularSectionRow." + name + "." + tsName;
      var rowRef = registerWithAlias(rowRu, rowEn);

      var collRu = fullRu + "ТабличнаяЧасть." + name + "." + tsName;
      var collEn = (fullEn == null || fullEn.isBlank()) ? null
        : fullEn + "TabularSection." + name + "." + tsName;
      var collRef = registerWithAlias(collRu, collEn);

      var tsAttributes = ts.getAttributes();
      if (tsAttributes != null && !tsAttributes.isEmpty()) {
        // Аналогично основным реквизитам: лямбда вызывает buildAttributeMembers
        // на каждый getMembers, поэтому язык читается per-call и подхватывает
        // workspace/didChangeConfiguration.
        MemberSource columnSource = () -> buildAttributeMembers(tsAttributes);
        typeRegistry.registerMemberSource(rowRef, columnSource, LanguageScope.BSL);
        // Для удобства dot-completion'а ТЧ-коллекция тоже показывает колонки —
        // обращение `ТЧ.Колонка` к коллекции встречается в коде (через индекс/первую строку).
        typeRegistry.registerMemberSource(collRef, columnSource, LanguageScope.BSL);
      }

      tsMembers.add(MemberDescriptor.property(tsName, collRef));
    }
    if (!tsMembers.isEmpty()) {
      var immutableTs = List.copyOf(tsMembers);
      typeRegistry.registerMemberSource(objectRef, () -> immutableTs, LanguageScope.BSL);
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
      return typeRegistry.getMembers(parent);
    }, LanguageScope.BSL);
  }

  /**
   * Регистрирует специализации ВСЕХ зарегистрированных дженериков семейства
   * (с qualifiedName, начинающимся с {@code familyCore}) для конкретного
   * MD-имени.
   * <p>
   * Имена placeholder'ов берутся структурно из
   * {@link TypeRegistry#getTypeParameters(TypeRef)} — это пробросом из
   * {@code Context.typeParameters()} bsl-context'а. На LS-уровне больше
   * нет ручного парсинга угловых скобок.
   * <p>
   * Покрывает всё семейство одним проходом: для Catalog это
   * {@code СправочникСсылка.<Имя>}, {@code СправочникОбъект.<Имя>},
   * {@code СправочникМенеджер.<Имя>}, {@code СправочникВыборка.<Имя>},
   * {@code СправочникСписок.<Имя>} и любые другие, которые HBK заведёт в
   * будущем. ManagerRef/ObjectRef/RefRef, зарегистрированные ранее с уже
   * навешанным кастомным MemberSource'ом (атрибуты и общие реквизиты),
   * получают ДОПОЛНИТЕЛЬНЫЙ MemberSource для платформенных members. Два
   * источника на типе работают штатно — getMembers объединяет.
   */
  private void registerFamilySpecializations(String familyCore, String mdName) {
    var generics = typeRegistry.findAllGenericsByFamilyCore(familyCore);
    for (var generic : generics) {
      var parameters = typeRegistry.getTypeParameters(generic);
      if (parameters.isEmpty()) {
        continue;
      }
      // Сейчас в HBK у каждого generic'а ровно один placeholder. Если
      // когда-то появятся типы с несколькими параметрами, маппинг нужно
      // будет расширить — но binding-API уже принимает Map<placeholder, value>.
      var bindings = new LinkedHashMap<String, String>();
      for (var name : parameters) {
        bindings.put(name, mdName);
      }
      var specializedName = TypeRef.specialize(generic, bindings).qualifiedName();
      if (specializedName.equals(generic.qualifiedName())) {
        continue;
      }
      // Использует существующий TypeRef (Object/Ref/Manager уже
      // зарегистрированы как CONFIGURATION выше) либо интернирует новый
      // того же kind, что и generic (PLATFORM) — это критично, иначе
      // инференсер строит (PLATFORM, name), а в реестре висит
      // (CONFIGURATION, name) — разные TypeRef, members не находятся.
      typeRegistry.registerSpecialization(specializedName, generic, bindings, LanguageScope.BSL);
    }
  }

  private TypeRef registerWithAlias(String qualifiedRu, String qualifiedEn) {
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (qualifiedEn != null && !qualifiedEn.equals(qualifiedRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    return ref;
  }

  private List<MemberDescriptor> buildAttributeMembers(List<? extends Attribute> attributes,
                                                       Map<String, String> platformDescriptions,
                                                       Map<String, PlatformMetadata> platformMetadata) {
    if (attributes == null || attributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(attributes.size());
    for (var attribute : attributes) {
      var attrName = attributeNameLocalized(attribute);
      if (attrName == null || attrName.isBlank()) {
        continue;
      }
      var lc = attrName.toLowerCase(Locale.ROOT);
      var description = platformDescriptions.getOrDefault(lc, "");
      var meta = platformMetadata.getOrDefault(lc, PlatformMetadata.EMPTY);
      var returnTypes = resolveAttributeReturnTypes(attribute);
      MemberDescriptor descriptor;
      if (returnTypes.isEmpty()) {
        descriptor = description.isEmpty()
          ? MemberDescriptor.property(attrName)
          : MemberDescriptor.property(attrName, TypeRef.UNKNOWN, description);
      } else if (returnTypes.size() == 1) {
        descriptor = MemberDescriptor.property(attrName, returnTypes.refs().iterator().next(), description);
      } else {
        descriptor = MemberDescriptor.property(attrName, returnTypes, description);
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
   * Собирает {@code name(lower) → description} для платформенных generic-типов
   * {@code <fullRu>Ссылка.<...>} и {@code <fullRu>Объект.<...>} — это HBK-описания,
   * подходящие к стандартным реквизитам соответствующего MD.
   */
  private Map<String, String> collectPlatformMemberDescriptions(String fullRu) {
    var result = new HashMap<String, String>();
    addPlatformDescriptionsTo(result, fullRu + "Ссылка");
    addPlatformDescriptionsTo(result, fullRu + "Объект");
    return result;
  }

  private void addPlatformDescriptionsTo(Map<String, String> sink, String familyPrefix) {
    var generic = typeRegistry.resolveGenericByPrefix(familyPrefix).orElse(null);
    if (generic == null) {
      return;
    }
    for (var m : typeRegistry.getMembers(generic)) {
      if (m.generic()) {
        continue;
      }
      if (m.description() == null || m.description().isBlank()) {
        continue;
      }
      sink.putIfAbsent(m.name().toLowerCase(Locale.ROOT), m.description());
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
    for (var m : typeRegistry.getMembers(generic)) {
      if (m.generic()) {
        continue;
      }
      var meta = m.metadata();
      if (meta == null || meta.isEmpty()) {
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
      if (attrName == null || attrName.isBlank()) {
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
    if (valueType == null || valueType.isEmpty()) {
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
   * Имя реквизита, локализованное под сконфигурированный {@link
   * com.github._1c_syntax.bsl.languageserver.configuration.Language}. Кастомные
   * реквизиты ({@link com.github._1c_syntax.bsl.mdo.children.ObjectAttribute}) имеют
   * только одно имя — оно и возвращается. Стандартные реквизиты (Дата/Номер/Ссылка/...) хранят
   * имя в {@link com.github._1c_syntax.bsl.types.MultiName} с обоими языками — берём нужный.
   */
  private String attributeNameLocalized(Attribute attribute) {
    if (attribute instanceof StandardAttribute std) {
      var fullName = std.getFullName();
      if (fullName != null && !fullName.isEmpty()) {
        return fullName.get(configuration.getLanguage().getLanguageCode());
      }
    }
    return attribute.getName();
  }

  /**
   * Получить набор {@link TypeRef} типов значения реквизита (union для
   * composite-типа из нескольких {@code v8:Type}).
   */
  private TypeSet resolveAttributeReturnTypes(Attribute attribute) {
    var valueType = attribute.getValueType();
    if (valueType == null || valueType.isEmpty()) {
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
