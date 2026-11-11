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

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Диагностика устаревших методов объекта {@code HTTPСоединение} /
 * {@code HTTPConnection}.
 * <p>
 * В платформе 8.3.21 синхронные методы HTTP-соединения объявлены устаревшими
 * в клиентском контексте. Вместо них следует использовать асинхронные аналоги
 * с суффиксом {@code Асинх} / {@code Async}. Асинхронные методы доступны только
 * на клиенте, поэтому диагностика сообщает только те вызовы, все возможные
 * контексты компиляции которых клиентские: предложенная замена должна быть
 * корректна в каждом из них.
 * <p>
 * Возможные контексты вызова определяет {@link CompilationContextResolver}:
 * он учитывает вид модуля и его метаданные, директиву компиляции метода
 * (включая серверную директиву по умолчанию для управляемых форм и команд),
 * ветку препроцессора и режимы, поддерживаемые проектом.
 * <p>
 * Чтобы исключить ложные срабатывания, тип-владелец метода резолвится через
 * {@link TypeService#membersAt}: диагностика срабатывает, если хотя бы один
 * из возможных типов-владельцев получателя — {@code HTTPСоединение}.
 *
 * @see <a href="https://dl04.1c.ru/content/Platform/8_3_21_1140/1cv8upd_8_3_21_1140.htm">
 *   Изменения платформы 8.3.21</a>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_21,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.PERFORMANCE
  }
)
@RequiredArgsConstructor
public class DeprecatedHttpConnectionMethodDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "(ВызватьHTTPМетод|CallHTTPMethod|"
    + "Записать|Put|"
    + "Изменить|Patch|"
    + "ОтправитьДляОбработки|Post|"
    + "Получить|Get|"
    + "ПолучитьЗаголовки|Head|"
    + "Удалить|Delete)"
  );

  private static final Pattern HTTP_CONNECTION_PATTERN = CaseInsensitivePattern.compile(
    "HTTPСоединение|HTTPConnection"
  );

  private final TypeService typeService;
  private final CompilationContextResolver compilationContextResolver;

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var methodName = ctx.methodName();
    if (methodName == null) {
      return super.visitMethodCall(ctx);
    }

    if (!MESSAGE_PATTERN.matcher(methodName.getText()).matches()) {
      return super.visitMethodCall(ctx);
    }

    if (!executesOnClientsOnly(ctx)) {
      return super.visitMethodCall(ctx);
    }

    var identifier = methodName.IDENTIFIER();
    if (identifier == null) {
      return super.visitMethodCall(ctx);
    }

    var isHttpConnection = typeService.membersAt(documentContext, identifier).stream()
      .map(member -> member.owner())
      .filter(Objects::nonNull)
      .anyMatch(owner -> HTTP_CONNECTION_PATTERN.matcher(owner.qualifiedName()).matches());

    if (isHttpConnection) {
      diagnosticStorage.addDiagnostic(methodName, info.getMessage(methodName.getText()));
    }

    return super.visitMethodCall(ctx);
  }

  /**
   * Проверить, что вызов исполняется только на клиенте.
   * <p>
   * Асинхронные аналоги доступны только на клиенте, поэтому замена корректна,
   * лишь когда клиентские все возможные контексты компиляции вызова. Пустое
   * множество означает, что объявление исключено препроцессором.
   */
  private boolean executesOnClientsOnly(BSLParser.MethodCallContext ctx) {
    var contexts = compilationContextResolver.possibleContexts(documentContext, ctx)
      .orElse(ExecutionContext.forFileType(documentContext.getFileType()));
    return !contexts.isEmpty() && ExecutionContext.CLIENTS.containsAll(contexts);
  }

}
