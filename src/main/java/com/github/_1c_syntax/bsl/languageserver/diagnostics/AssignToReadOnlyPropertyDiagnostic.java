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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Подсвечивает присваивание значению в свойство, у которого режим доступа —
 * {@link AccessMode#READ}.
 * <p>
 * Источник информации о режиме доступа — синтакс-помощник платформы 1С
 * (через {@code bsl-context}) или JSON-fallback. Метаданные пробрасываются
 * платформенным провайдером в {@link com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor#metadata()}
 * и индексируются как пары (тип-владелец, имя свойства), что позволяет
 * быстро отфильтровать безопасные присваивания.
 * <p>
 * Поток:
 * <ol>
 *   <li>{@link TypeService#hasAnyReadOnlyMember()} — глобальный гейт.
 *       Без HBK / без accessMode-данных диагностика моментально no-op.</li>
 *   <li>{@link TypeService#memberAt(com.github._1c_syntax.bsl.languageserver.context.DocumentContext,
 *       Position)} — точный резолв member'а с учётом инференции типа
 *       ресивера (глобальное свойство, локальная переменная, цепочка
 *       аксессоров). Резолв bilingual: read-only находится независимо от
 *       того, на каком языке (ru/en) записано имя свойства.</li>
 *   <li>Финальная проверка {@code member.metadata().accessMode() == READ}.</li>
 * </ol>
 * <p>
 * Pre-filter по имени свойства намеренно отсутствует: одно и то же имя
 * (например, {@code Ссылка}) может быть read-only на одном типе и
 * read-write на другом, поэтому решение принимается только по
 * резолвленному member'у конкретного типа-владельца.
 * <p>
 * <b>Lock contention.</b> Раньше шаг 3 (memberAt → ExpressionTypeInferencer
 * → ReferenceResolver → ServerContextProvider.getDocument) брал per-document
 * RWLock и конкурировал с {@code populateContext} (там WRITE). Сейчас
 * reference-finder'ы переведены на {@code getDocumentNoLock} (см.
 * {@link com.github._1c_syntax.bsl.languageserver.references}), поэтому
 * диагностика безопасно работает в фазу populate.
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
@RequiredArgsConstructor
public class AssignToReadOnlyPropertyDiagnostic extends AbstractVisitorDiagnostic {

  private final TypeService typeService;

  @Override
  public @Nullable ParseTree visitAssignment(BSLParser.AssignmentContext ctx) {
    readOnlyProperty(ctx).ifPresent(propertyId ->
      diagnosticStorage.addDiagnostic(Ranges.create(propertyId), info.getMessage(propertyId.getText())));
    return super.visitAssignment(ctx);
  }

  /**
   * Идентификатор присваиваемого свойства, если оно резолвится в read-only член
   * конкретного типа-владельца (независимо от языка имени). Иначе — empty.
   */
  private Optional<TerminalNode> readOnlyProperty(BSLParser.AssignmentContext ctx) {
    if (!typeService.hasAnyReadOnlyMember()) {
      return Optional.empty();
    }
    var lValue = ctx.lValue();
    if (lValue == null || lValue.acceptor() == null) {
      return Optional.empty();
    }
    var accessProperty = lValue.acceptor().accessProperty();
    var propertyId = accessProperty == null ? null : accessProperty.IDENTIFIER();
    if (propertyId == null) {
      return Optional.empty();
    }
    var member = typeService.memberAt(documentContext, positionInside(propertyId))
      .map(TypedMember::descriptor)
      .orElse(null);
    if (member == null || member.kind() != MemberKind.PROPERTY
      || member.metadata().accessMode() != AccessMode.READ) {
      return Optional.empty();
    }
    return Optional.of(propertyId);
  }

  private static Position positionInside(TerminalNode terminal) {
    var token = terminal.getSymbol();
    var col = token.getCharPositionInLine();
    var len = token.getText().length();
    return new Position(token.getLine() - 1, col + Math.max(0, len / 2));
  }
}
