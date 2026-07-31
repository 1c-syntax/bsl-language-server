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

import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Стандартные параметры формы из синтакс-помощника: то, что платформа кладёт в
 * {@code Форма.Параметры} независимо от конфигурации.
 * <p>
 * Источник — {@code ContextType.formParameters()} (bsl-context 0.8.0+). Параметры
 * объявлены на двух уровнях: общие для любой формы — на
 * {@code ФормаКлиентскогоПриложения} ({@code ТолькоПросмотр},
 * {@code КлючНазначенияИспользования}, …), специфичные для вида основных данных —
 * на типе-расширении формы ({@code Расширение справочника}: {@code Ключ},
 * {@code ЗначенияЗаполнения}, {@code ЭтоГруппа} …).
 * <p>
 * Карта строится один раз на весь workspace: типов с параметрами формы в HBK
 * порядка двух десятков, а обращение к ним идёт на каждую форму конфигурации.
 * Без HBK карта пуста — параметры просто не разворачиваются.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class FormParametersResolver {

  private final BslContextHolder bslContextHolder;

  private final AtomicReference<Map<String, List<MemberDescriptor>>> cache = new AtomicReference<>();

  /**
   * Параметры формы, объявленные у платформенного типа.
   *
   * @param typeQualifiedName ru-имя типа ({@code "ФормаКлиентскогоПриложения"},
   *                          {@code "Расширение документа"}).
   * @return дескрипторы параметров как свойств; пусто, если у типа их нет либо HBK недоступен.
   */
  public List<MemberDescriptor> parametersOf(String typeQualifiedName) {
    return parameters().getOrDefault(typeQualifiedName.toLowerCase(Locale.ROOT), List.of());
  }

  private Map<String, List<MemberDescriptor>> parameters() {
    var cached = cache.get();
    if (cached != null) {
      return cached;
    }
    // Как и globalEvents в EventHandlerResolver, кэшируем в том числе пустой результат:
    // BslContextHolder — мемоизированный Lazy без повторных попыток, поздней подгрузки не бывает.
    var built = build();
    cache.set(built);
    return built;
  }

  private Map<String, List<MemberDescriptor>> build() {
    var providerOpt = bslContextHolder.get();
    if (providerOpt.isEmpty()) {
      return Map.of();
    }
    var provider = providerOpt.get();
    var enLookup = BslContextPlatformTypesProvider.enLookupOf(provider);
    Map<String, List<MemberDescriptor>> byType = new HashMap<>();
    for (var context : provider.getContexts()) {
      if (!(context instanceof ContextType type)) {
        continue;
      }
      var formParameters = type.formParameters();
      if (formParameters.isEmpty()) {
        continue;
      }
      var members = withCopyingValueType(formParameters.stream()
        .map(parameter -> BslContextPlatformTypesProvider.toMemberDescriptor(parameter, enLookup))
        .toList());
      // Имя типа в HBK не уникально (например, «Расширение динамического списка» —
      // и у формы списка, и у таблицы формы). Параметры есть только у одного из
      // омонимов, поэтому первая непустая регистрация и есть нужная.
      byType.putIfAbsent(type.name().getName().toLowerCase(Locale.ROOT), members);
    }
    return Map.copyOf(byType);
  }

  /** Параметр-ссылка на копируемый объект. */
  private static final String COPYING_VALUE = "ЗначениеКопирования";

  /** Параметр-ссылка на открываемый объект. */
  private static final String KEY = "Ключ";

  /**
   * Достраивает тип параметра «значение копирования». В синтакс-помощнике он объявлен
   * без типа вовсе, хотя это ссылка на копируемый объект — то есть ровно тот же тип,
   * что у параметра «ключ» того же расширения ({@code ДокументСсылка.<Имя документа>}).
   * Плейсхолдер в нём подставится дальше по общему пути, вместе с ключом.
   *
   * @param parameters параметры одного типа-источника.
   * @return они же; у «значения копирования» проставлен тип, если его удалось взять у «ключа».
   */
  private static List<MemberDescriptor> withCopyingValueType(List<MemberDescriptor> parameters) {
    var keyTypes = parameters.stream()
      .filter(parameter -> parameter.matches(KEY))
      .map(MemberDescriptor::returnTypes)
      .filter(types -> !types.isEmpty())
      .findFirst();
    if (keyTypes.isEmpty()) {
      return parameters;
    }
    return parameters.stream()
      .map(parameter -> parameter.matches(COPYING_VALUE) && parameter.returnTypes().isEmpty()
        ? parameter.withReturnTypes(keyTypes.get())
        : parameter)
      .toList();
  }
}
