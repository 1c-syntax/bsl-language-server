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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.platform.PlatformMemberCalls;
import com.github._1c_syntax.bsl.languageserver.diagnostics.platform.PlatformMemberCalls.ConstructedType;
import com.github._1c_syntax.bsl.languageserver.types.PlatformMemberVersions;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;

import java.util.HashSet;
import java.util.List;

/**
 * Подсвечивает вызов метода, обращение к свойству платформенного типа или
 * конструирование самого типа, недоступных в целевой версии платформы: член
 * (или тип) появился в версии новее, чем режим совместимости проекта
 * ({@code target < sinceVersion}).
 * <p>
 * Источник версий — синтакс-помощник установленной платформы 1С (через
 * {@code bsl-context}) или встроенный справочник. Для членов срабатывает, если
 * хотя бы один из возможных типов-владельцев ресивера делает член недоступным.
 * Если режим совместимости проекта не задан, считается «самая свежая
 * платформа» — тогда проверка не срабатывает.
 *
 * @see PlatformMemberCalls
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  scope = DiagnosticScope.BSL,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
@RequiredArgsConstructor
public class UnavailableMemberCallDiagnostic extends AbstractDiagnostic {

  private final TypeService typeService;
  private final LanguageServerConfiguration configuration;

  @Override
  public void check() {
    var target = PlatformMemberVersions.targetCompatibilityMode(documentContext, configuration);
    // Оба вида сайтов — за один обход AST.
    var callSites = PlatformMemberCalls.collect(documentContext, typeService);
    checkMembers(callSites.members(), target);
    checkConstructedTypes(callSites.constructedTypes(), target);
  }

  private void checkMembers(List<TypedMember> members, CompatibilityMode target) {
    var reported = new HashSet<Range>();
    for (var member : members) {
      var metadata = member.descriptor().metadata();
      if (!PlatformMemberVersions.firesUnavailable(metadata.sinceVersion(), target)) {
        continue;
      }
      if (reported.add(member.range())) {
        diagnosticStorage.addDiagnostic(member.range(),
          info.getMessage(member.descriptor().name(), metadata.sinceVersion()));
      }
    }
  }

  /**
   * Конструирование типа, которого нет в целевой версии платформы
   * ({@code Новый ЧтениеJSON()} при режиме совместимости ниже 8.3.6). Версия
   * появления берётся с главной страницы типа в синтакс-помощнике.
   */
  private void checkConstructedTypes(List<ConstructedType> constructedTypes, CompatibilityMode target) {
    for (var constructed : constructedTypes) {
      var sinceVersion = constructed.metadata().sinceVersion();
      if (!PlatformMemberVersions.firesUnavailable(sinceVersion, target)) {
        continue;
      }
      diagnosticStorage.addDiagnostic(constructed.node(),
        info.getResourceString("typeMessage", constructed.typeName(), sinceVersion));
    }
  }
}
