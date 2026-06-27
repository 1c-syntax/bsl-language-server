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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.utils.StringInterner;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Фабрика для создания коллекций {@link DiagnosticInfo} для каждого workspace.
 * <p>
 * Создаёт {@link DiagnosticInfo} с привязкой к per-workspace {@link LanguageServerConfiguration}.
 * Каждый workspace имеет свою коллекцию DiagnosticInfo, чтобы поддерживать разные
 * настройки диагностик для разных рабочих областей.
 */
@Component
@RequiredArgsConstructor
public class DiagnosticInfosFactory {

  private final ApplicationContext applicationContext;
  private final StringInterner stringInterner;

  /**
   * Получить список всех классов диагностик, зарегистрированных в контексте.
   *
   * @return Список классов диагностик
   */
  @SuppressWarnings("unchecked")
  public List<Class<? extends BSLDiagnostic>> getDiagnosticClasses() {
    var beanNames = applicationContext.getBeanNamesForAnnotation(DiagnosticMetadata.class);

    return Arrays.stream(beanNames)
      .map(applicationContext::getType)
      .filter(Objects::nonNull)
      .filter(BSLDiagnostic.class::isAssignableFrom)
      .<Class<? extends BSLDiagnostic>>map(aClass -> (Class<? extends BSLDiagnostic>) aClass)
      .toList();
  }

  /**
   * Создать коллекцию DiagnosticInfo для workspace с указанной конфигурацией.
   *
   * @param configuration Per-workspace конфигурация
   * @return Коллекция DiagnosticInfo
   */
  public Collection<DiagnosticInfo> createDiagnosticInfos(LanguageServerConfiguration configuration) {
    return getDiagnosticClasses().stream()
      .map(diagnosticClass -> new DiagnosticInfo(diagnosticClass, configuration, stringInterner))
      .toList();
  }

  /**
   * Создать Map диагностик по коду для workspace с указанной конфигурацией.
   *
   * @param configuration Per-workspace конфигурация
   * @return Map: код диагностики -> DiagnosticInfo
   */
  public Map<String, DiagnosticInfo> createDiagnosticInfosByCode(LanguageServerConfiguration configuration) {
    return getDiagnosticClasses().stream()
      .map(diagnosticClass -> new DiagnosticInfo(diagnosticClass, configuration, stringInterner))
      .collect(Collectors.toMap(info -> info.getCode().getStringValue(), Function.identity()));
  }

  /**
   * Создать Map диагностик по классу для workspace с указанной конфигурацией.
   *
   * @param configuration Per-workspace конфигурация
   * @return Map: класс диагностики -> DiagnosticInfo
   */
  public Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> createDiagnosticInfosByClass(
    LanguageServerConfiguration configuration
  ) {
    return getDiagnosticClasses().stream()
      .map(diagnosticClass -> new DiagnosticInfo(diagnosticClass, configuration, stringInterner))
      .collect(Collectors.toMap(DiagnosticInfo::getDiagnosticClass, Function.identity()));
  }
}
