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
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.Check;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnknownMember;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Подсвечивает обращение к методу или свойству, которое не является известным
 * членом своего типа (вероятная опечатка или несуществующий API):
 * <ul>
 *   <li>{@code Ресивер.Член} — тип ресивера выведен и конкретен, но члена с
 *       таким именем у него нет;</li>
 *   <li>голый вызов {@code Имя(...)} — имя не резолвится ни в глобальную
 *       функцию/свойство платформы или конфигурации, ни в метод/переменную
 *       текущего модуля.</li>
 * </ul>
 * <p>
 * Проверяет каждый известный тип в автоматически выбранных режимах компиляции.
 * Невыведенный и произвольный типы сами по себе не доказывают ошибку. Без сведений
 * о контексте сохраняется обычная проверка имён, включая OneScript.
 * Выключена по умолчанию — требует обкатки на реальных конфигурациях.
 *
 * @see MemberAccessChecker
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  scope = DiagnosticScope.ALL,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
@RequiredArgsConstructor
public class UnknownMemberDiagnostic extends AbstractVisitorDiagnostic {

  private final MemberAccessChecker accessChecker;
  private final CompilationContextResolver contextResolver;
  private final TypeService typeService;

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var contexts = contextResolver.automaticContexts(documentContext);
    for (var problem : accessChecker.check(documentContext, contexts, Set.of(Check.EXISTENCE))) {
      var missing = (UnknownMember) problem;
      var message = missing.receiverTypes().isEmpty() ? info.getMessage(missing.name())
        : memberMessage(missing.name(), missing.receiverTypes());
      if (missing.context().isEmpty()) {
        diagnosticStorage.addDiagnostic(missing.range(), message);
      } else {
        var context = missing.context().orElseThrow();
        var detail = info.getResourceString(context.isFileMode() ? "callContextFile" : "callContext",
          info.getResourceString(context.optionContext().name()));
        diagnosticStorage.addDiagnostic(missing.range(), message, List.of(
          RelatedInformation.create(documentContext.getUri(), missing.range(), message),
          RelatedInformation.create(documentContext.getUri(), missing.range(), detail)));
      }
    }
    return ctx;
  }

  /**
   * Сообщение с именем(-ами) типа ресивера для подсказки, на каком типе члена
   * не нашлось. Несколько кандидатов union'а склеиваются через запятую — по алфавиту:
   * порядок типов в объединении зависит от того, в каком порядке их собрал вывод, и
   * от прогона к прогону может меняться, а текст замечания должен быть одним и тем же
   * (по нему замечания сопоставляют между анализами, например в SonarQube).
   * Имена типов соответствуют варианту встроенного языка проекта, как имена в completion.
   */
  private String memberMessage(String memberName, TypeSet ownerTypes) {
    var typeNames = ownerTypes.refs().stream()
      .map(type -> typeService.displayName(type, documentContext.getScriptVariantLanguage()))
      .distinct()
      .sorted()
      .collect(Collectors.joining(", "));
    return info.getResourceString("memberMessage", memberName, typeNames);
  }
}
