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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.platform.PlatformMemberCalls;
import com.github._1c_syntax.bsl.languageserver.diagnostics.platform.PlatformMemberCalls.ConstructedType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.PlatformMemberVersions;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.DESIGN
  }
)
@RequiredArgsConstructor
public class DeprecatedMethodCallDiagnostic extends AbstractDiagnostic {
  private final ReferenceIndex referenceIndex;
  private final TypeService typeService;
  private final LanguageServerConfiguration configuration;

  @Override
  public void check() {
    checkUserDefinedMethods();

    var target = PlatformMemberVersions.targetCompatibilityMode(documentContext, configuration);
    // Сбор сайтов обращения к платформенному API — один раз на модуль; все три
    // проверки ниже работают по одному и тому же результату (раньше каждая
    // независимо пересобирала его, удваивая обход AST и резолв членов).
    var callSites = PlatformMemberCalls.collect(documentContext, typeService);
    checkPlatformMembers(callSites.members(), target);
    checkDeletedPrefixMembers(callSites.members());
    checkConstructedTypes(callSites.constructedTypes(), target);
  }

  /**
   * Вызовы пользовательских методов, помеченных «Устарела.» в doc-комментарии.
   */
  private void checkUserDefinedMethods() {
    var uri = documentContext.getUri();

    referenceIndex.getReferencesFrom(uri, SymbolKind.Method).stream()
      .filter(reference -> reference.symbol().isDeprecated())
      .filter(reference -> !reference.from().isDeprecated())
      .forEach((Reference reference) -> {
        var deprecatedSymbol = reference.symbol();
        var deprecationInfo = getDeprecationInfo(deprecatedSymbol);
        var message = info.getMessage(deprecatedSymbol.getName(), deprecationInfo);
        diagnosticStorage.addDiagnostic(reference.selectionRange(), message);
      });
  }

  /**
   * Вызовы платформенных членов, устаревших для целевой версии платформы
   * ({@code target >= deprecatedSinceVersion}). Срабатывает, если хотя бы один
   * из возможных типов-владельцев ресивера делает член устаревшим.
   */
  private void checkPlatformMembers(List<TypedMember> platformMemberCalls, CompatibilityMode target) {
    var reported = new HashSet<Range>();
    for (var member : platformMemberCalls) {
      var metadata = member.descriptor().metadata();
      if (PlatformMemberVersions.firesDeprecated(metadata.deprecatedSinceVersion(), target)
        && reported.add(member.range())) {
        diagnosticStorage.addDiagnostic(member.range(),
          info.getMessage(member.descriptor().name(), replacementsHint(metadata)));
      }
    }
  }

  /**
   * Конструирование типа, устаревшего для целевой версии платформы
   * ({@code Новый ЗащищенноеСоединениеNSS()} при {@code target >= 8.3.8}).
   * Версия устаревания и рекомендуемые замены берутся с главной страницы типа
   * в синтакс-помощнике.
   */
  private void checkConstructedTypes(List<ConstructedType> constructedTypes, CompatibilityMode target) {
    for (var constructed : constructedTypes) {
      var metadata = constructed.metadata();
      if (PlatformMemberVersions.firesDeprecated(metadata.deprecatedSinceVersion(), target)) {
        diagnosticStorage.addDiagnostic(constructed.node(),
          info.getMessage(constructed.typeName(), replacementsHint(metadata)));
      }
    }
  }

  /** Хвост сообщения «Следует использовать: …» либо пустая строка, если замены не указаны. */
  private String replacementsHint(PlatformMetadata metadata) {
    var replacements = metadata.recommendedReplacements();
    return replacements.isEmpty()
      ? ""
      : info.getResourceString("recommendedReplacementsHint", String.join(", ", replacements));
  }

  /**
   * Обращения к конфигурационным свойствам с префиксом «Удалить»/«Delete» —
   * стандартная 1С-конвенция пометки устаревших реквизитов, значений
   * перечислений, объектов конфигурации. Срабатывает независимо от целевой
   * версии платформы и наличия HBK-меты. Только {@link MemberKind#PROPERTY} —
   * action-методы вроде {@code УдалитьФайл()} (METHOD) сюда не попадают.
   */
  private void checkDeletedPrefixMembers(List<TypedMember> platformMemberCalls) {
    var reported = new HashSet<Range>();
    for (var member : platformMemberCalls) {
      if (member.descriptor().kind() != MemberKind.PROPERTY) {
        continue;
      }
      var name = member.descriptor().name();
      if (PlatformMemberCalls.hasDeletedPrefix(name) && reported.add(member.range())) {
        var hint = info.getResourceString("deletedPrefixHint");
        diagnosticStorage.addDiagnostic(member.range(), info.getMessage(name, hint));
      }
    }
  }

  private static String getDeprecationInfo(Symbol deprecatedSymbol) {
    return Optional.of(deprecatedSymbol)
      .filter(Describable.class::isInstance)
      .map(Describable.class::cast)
      .flatMap(Describable::getDescription)
      .map(SourceDefinedSymbolDescription::getDeprecationInfo)
      .orElse("");
  }
}
