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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MDOType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
    var contexts = serverContextProvider.getAllContexts().values();
    for (var serverContext : contexts) {
      var configuration = serverContext.getConfiguration();
      if (configuration == null || configuration.isEmpty()) {
        continue;
      }
      if (!registered.compareAndSet(false, true)) {
        return;
      }
      var children = configuration.getChildrenByMdoRef().values();
      LOGGER.debug("ConfigurationTypesProvider: registering {} MD objects", children.size());
      register(children);
      return;
    }
  }

  private void register(Iterable<MD> children) {
    int count = 0;
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

      var qualifiedRu = groupRu + "." + name;
      var ref = typeRegistry.registerConfigurationType(qualifiedRu);
      if (!groupEn.equals(groupRu)) {
        typeRegistry.registerConfigurationTypeAlias(groupEn + "." + name, ref);
      }
      count++;
    }
    LOGGER.debug("Configuration types registered: {}", count);
  }
}
