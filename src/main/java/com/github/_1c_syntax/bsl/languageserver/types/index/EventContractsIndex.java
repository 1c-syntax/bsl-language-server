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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш контрактов платформенных событий, разрезанный по URI документа.
 * Для каждого документа лениво строит карту «имя метода → контракт события
 * owner-типа модуля» через {@link EventHandlerResolver#lookupContract}.
 * <p>
 * Точечная инвалидация на событиях жизненного цикла документа
 * ({@link AbstractDocumentLifecycleClearableIndex}) — на изменение,
 * закрытие, удаление и сброс вторичных данных.
 * Полная инвалидация — на {@link ConfigurationTypesRegisteredEvent}: до
 * регистрации owner-тип модуля в реестре может отсутствовать, после —
 * вся кэшированная карта пустых контрактов должна перестроиться.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class EventContractsIndex extends AbstractDocumentLifecycleClearableIndex {

  private final EventHandlerResolver eventHandlerResolver;

  private final Map<URI, Lazy<Map<String, Optional<MemberDescriptor>>>> contractsByUri
    = new ConcurrentHashMap<>();

  /**
   * Возвращает контракт платформенного события для метода с указанным именем
   * либо {@link Optional#empty()}, если метод не является обработчиком.
   */
  public Optional<MemberDescriptor> getContract(DocumentContext documentContext, String methodName) {
    var lazy = contractsByUri.computeIfAbsent(
      documentContext.getUri(),
      uri -> new Lazy<>(() -> buildFor(documentContext))
    );
    return lazy.getOrCompute().getOrDefault(methodName.toLowerCase(Locale.ROOT), Optional.empty());
  }

  @Override
  public void clear(URI uri) {
    contractsByUri.remove(uri);
  }

  @EventListener
  public void handleConfigurationTypesRegistered(ConfigurationTypesRegisteredEvent event) {
    contractsByUri.clear();
  }

  private Map<String, Optional<MemberDescriptor>> buildFor(DocumentContext documentContext) {
    var methods = documentContext.getSymbolTree().getMethods();
    if (methods.isEmpty()) {
      return Map.of();
    }
    Map<String, Optional<MemberDescriptor>> result = HashMap.newHashMap(methods.size());
    for (var method : methods) {
      var key = method.getName().toLowerCase(Locale.ROOT);
      result.put(key, eventHandlerResolver.lookupContract(documentContext, method.getName()));
    }
    return Map.copyOf(result);
  }
}
