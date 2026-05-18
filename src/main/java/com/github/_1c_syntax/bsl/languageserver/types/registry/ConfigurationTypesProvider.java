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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ValueType;
import com.github._1c_syntax.bsl.types.value.PrimitiveValueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
      if (fullName != null && fullName.getRu() != null && !fullName.getRu().isBlank()) {
        managerRu = fullName.getRu() + "Менеджер." + name;
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

      registerObjectAndRefTypes(md, mdoType, name, fullName);

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
                                         com.github._1c_syntax.bsl.types.MultiName fullName) {
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

    var members = buildAttributeMembers(attributes);
    if (!members.isEmpty()) {
      typeRegistry.registerMemberSource(objectRef, () -> members, LanguageScope.BSL);
      typeRegistry.registerMemberSource(refRef, () -> members, LanguageScope.BSL);
    }
  }

  private TypeRef registerWithAlias(String qualifiedRu, String qualifiedEn) {
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (qualifiedEn != null && !qualifiedEn.equals(qualifiedRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    return ref;
  }

  private List<MemberDescriptor> buildAttributeMembers(List<? extends Attribute> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(attributes.size());
    for (var attribute : attributes) {
      var attrName = attribute.getName();
      if (attrName == null || attrName.isBlank()) {
        continue;
      }
      var returnTypes = resolveAttributeReturnTypes(attribute);
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

  private TypeRef resolveValueType(ValueType vt) {
    if (vt instanceof PrimitiveValueType primitive) {
      var fullName = primitive.fullName();
      if (fullName == null) {
        return null;
      }
      return typeRegistry.resolve(fullName.getRu()).orElse(null);
    }
    // V8 / METADATA / UNKNOWN — пока не поддерживаем точно; resolve по имени даст
    // частичное покрытие для V8-типов, имена которых совпадают с регистрационными.
    var fullName = vt.fullName();
    if (fullName == null) {
      return null;
    }
    return typeRegistry.resolve(fullName.getRu()).orElse(null);
  }
}
