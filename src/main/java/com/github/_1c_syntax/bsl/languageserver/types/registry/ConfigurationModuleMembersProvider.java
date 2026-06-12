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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Расширяет платформенные типы менеджеров/объектов/наборов записей
 * (например, {@code СправочникМенеджер.Контрагенты},
 * {@code СправочникОбъект.Контрагенты}) методами, экспортированными из
 * соответствующих модулей конфигурации (ManagerModule.bsl, ObjectModule.bsl,
 * RecordSetModule.bsl).
 * <p>
 * Реестр сам не знает, что эти методы существуют — их даёт только AST модуля.
 * Поэтому источник членов прибит к {@link DocumentContext}: запрос членов
 * будет каждый раз идти в актуальный SymbolTree, что даёт hot-reload без
 * ручной инвалидации.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class ConfigurationModuleMembersProvider {

  /** ModuleType → префикс qualifiedName платформенного типа-обёртки. */
  private static final Map<ModuleType, String> MODULE_TYPE_TO_WRAPPER_RU = Map.of(
    ModuleType.ManagerModule, "Менеджер",
    ModuleType.ObjectModule, "Объект",
    ModuleType.RecordSetModule, "НаборЗаписей",
    ModuleType.ValueManagerModule, "МенеджерЗначения"
  );

  private static final Map<ModuleType, String> MODULE_TYPE_TO_WRAPPER_EN = Map.of(
    ModuleType.ManagerModule, "Manager",
    ModuleType.ObjectModule, "Object",
    ModuleType.RecordSetModule, "RecordSet",
    ModuleType.ValueManagerModule, "ValueManager"
  );

  private final TypeRegistry typeRegistry;
  private final GlobalScopeProvider globalScopeProvider;

  /** Уже зарегистрированные источники (по URI документа), чтобы избежать дублей. */
  private final Map<URI, TypeRef> registeredByUri = new ConcurrentHashMap<>();

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    register(documentContext);
  }

  private void register(DocumentContext documentContext) {
    var moduleType = documentContext.getModuleType();
    if (moduleType == ModuleType.CommonModule) {
      registerCommonModule(documentContext);
      return;
    }
    if (!MODULE_TYPE_TO_WRAPPER_RU.containsKey(moduleType)) {
      return;
    }
    var mdObjectOpt = documentContext.getMdObject();
    if (mdObjectOpt.isEmpty()) {
      return;
    }
    var mdObject = mdObjectOpt.get();
    var mdoType = mdObject.getMdoType();
    var fullName = mdoType.fullName();
    if (fullName == null) {
      return;
    }
    var groupNameRu = fullName.getRu(); // "Справочник", "Документ", ...
    var groupNameEn = fullName.getEn(); // "Catalog", "Document", ...

    var wrapperRu = groupNameRu + MODULE_TYPE_TO_WRAPPER_RU.get(moduleType);
    var wrapperEn = groupNameEn == null ? null : groupNameEn + MODULE_TYPE_TO_WRAPPER_EN.get(moduleType);
    var name = mdObject.getName();
    if (name == null || name.isBlank()) {
      return;
    }

    var qualifiedRu = wrapperRu + "." + name;
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (wrapperEn != null && !wrapperEn.equals(wrapperRu)) {
      typeRegistry.registerConfigurationTypeAlias(wrapperEn + "." + name, ref);
    }

    var prev = registeredByUri.put(documentContext.getUri(), ref);
    globalScopeProvider.indexModuleType(documentContext.getUri(), ref);
    if (prev != null && prev.equals(ref)) {
      // тот же URI/тип — источник уже зарегистрирован, AST подхватится автоматически
      return;
    }

    typeRegistry.registerMemberSource(ref, () -> exportMethodsAsMembers(documentContext), LanguageScope.BSL);
    LOGGER.debug("Registered module-as-member-source for {} -> {}", documentContext.getUri(), qualifiedRu);
  }

  private void registerCommonModule(DocumentContext documentContext) {
    var mdObjectOpt = documentContext.getMdObject();
    if (mdObjectOpt.isEmpty()) {
      return;
    }
    var name = mdObjectOpt.get().getName();
    if (name == null || name.isBlank()) {
      return;
    }

    var ref = typeRegistry.registerConfigurationType(name);

    var prev = registeredByUri.put(documentContext.getUri(), ref);
    globalScopeProvider.indexModuleType(documentContext.getUri(), ref);
    if (prev != null && prev.equals(ref)) {
      return;
    }

    typeRegistry.registerMemberSource(ref, () -> exportMethodsAsMembers(documentContext), LanguageScope.BSL);
    // Source-symbol — ModuleSymbol этого DocumentContext'а. Lazy-резолв,
    // чтобы пережить rebuild: SymbolTree пересоздаётся, но Supplier всегда
    // возвращает актуальный getModule().
    Supplier<Symbol> moduleSymbolSupplier =
      () -> documentContext.getSymbolTree().getModule();
    typeRegistry.registerAsGlobalProperty(ref, LanguageScope.BSL,
      SyntheticKind.PLATFORM_GLOBAL_PROPERTY, moduleSymbolSupplier);
    LOGGER.debug("Registered common module as global property {} -> {}", documentContext.getUri(), name);
  }

  private List<MemberDescriptor> exportMethodsAsMembers(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol::isExport)
      .map(this::toMethodMember)
      .toList();
  }

  private MemberDescriptor toMethodMember(
    com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol method
  ) {
    var params = method.getParameters().stream()
      .map(p -> new ParameterDescriptor(
        p.getName(),
        com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.EMPTY,
        p.isOptional(),
        ""
      ))
      .toList();
    var description = method.getDescription()
      .map(d -> d.getDescription() == null ? "" : d.getDescription().trim())
      .orElse("");
    var returnType = method.getDescription()
      .map(d -> resolveReturnType(d.getReturnedValue()))
      .orElse(TypeRef.UNKNOWN);
    var signature = new SignatureDescriptor(params, returnType, description);
    return MemberDescriptor
      .method(method.getName(), description, List.of(signature))
      .withSourceSymbol(method);
  }

  /**
   * Парсит первый элемент {@code returnedValue} JavaDoc-описания BSL-метода
   * (например, "Массив из Произвольный" → "Массив") и резолвит через
   * {@link TypeRegistry}.
   */
  private TypeRef resolveReturnType(
    List<com.github._1c_syntax.bsl.parser.description.TypeDescription> returnedValue
  ) {
    if (returnedValue == null || returnedValue.isEmpty()) {
      return TypeRef.UNKNOWN;
    }
    var raw = returnedValue.get(0).name();
    if (raw == null || raw.isBlank()) {
      return TypeRef.UNKNOWN;
    }
    // отбрасываем пояснения после первого пробела/угловой скобки/квадратной скобки
    // ("Массив из Произвольный", "Массив<Произвольный>" → "Массив")
    var head = raw.trim().split("[\\s<\\[]", 2)[0];
    return typeRegistry.resolve(head).orElse(TypeRef.UNKNOWN);
  }
}
