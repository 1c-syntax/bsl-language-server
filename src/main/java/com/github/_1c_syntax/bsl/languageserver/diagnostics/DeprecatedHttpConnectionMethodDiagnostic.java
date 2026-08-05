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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

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
/**
 * Диагностика устаревших методов объекта {@code HTTPСоединение} /
 * {@code HTTPConnection}.
 * <p>
 * В платформе 8.3.21 синхронные методы HTTP-соединения объявлены
 * устаревшими в клиентском контексте. Вместо них следует использовать
 * асинхронные аналоги с суффиксом {@code Асинх} / {@code Async}.
 * <p>
 * Асинхронные методы доступны только на клиенте. Чтобы исключить ложные
 * срабатывания, диагностика работает только в модулях с клиентским
 * контекстом (формы, команды, обычное/управляемое приложение, клиентские
 * общие модули) и только внутри методов, выполняющихся на клиенте
 * (директивы {@code &НаКлиенте}, {@code &НаКлиентеНаСервере} и методы без
 * директивы в клиентском модуле).
 * <p>
 * Для исключения ложных срабатываний тип-владелец метода резолвится
 * через {@link TypeService#memberAt}: диагностика срабатывает только
 * если метод вызван на объекте типа {@code HTTPСоединение}.
 *
 * @see <a href="https://dl04.1c.ru/content/Platform/8_3_21_1140/1cv8upd_8_3_21_1140.htm">
 *   Изменения платформы 8.3.21</a>
 */
@RequiredArgsConstructor
public class DeprecatedHttpConnectionMethodDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern MESSAGE_PATTERN = CaseInsensitivePattern.compile(
    "(ВызватьHTTPМетод|CallHTTPMethod|"
    + "Записать|Write|"
    + "Изменить|Change|Modify|"
    + "ОтправитьДляОбработки|SendForProcessing|"
    + "Получить|Get|"
    + "ПолучитьЗаголовки|GetHeaders|"
    + "Удалить|Delete)"
  );

  private static final Pattern HTTP_CONNECTION_PATTERN = CaseInsensitivePattern.compile(
    "HTTPСоединение|HTTPConnection"
  );

  private static final Set<CompilerDirectiveKind> SERVER_COMPILER_DIRECTIVES =
    EnumSet.of(CompilerDirectiveKind.AT_SERVER, CompilerDirectiveKind.AT_SERVER_NO_CONTEXT);

  private final TypeService typeService;

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (isServerModule(documentContext)) {
      return ctx;
    }
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    if (methodSymbol.isPresent()) {
      var compilerDirective = methodSymbol.get().getCompilerDirectiveKind();
      if (compilerDirective.isPresent()
        && SERVER_COMPILER_DIRECTIVES.contains(compilerDirective.get())) {
        return ctx;
      }
    }
    return super.visitSub(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var methodName = ctx.methodName();
    if (methodName == null) {
      return ctx;
    }

    if (!MESSAGE_PATTERN.matcher(methodName.getText()).matches()) {
      return ctx;
    }

    var identifier = methodName.IDENTIFIER();
    if (identifier == null) {
      return ctx;
    }

    var typedMember = typeService.memberAt(documentContext, identifier);
    if (typedMember.isEmpty()) {
      return ctx;
    }

    var owner = typedMember.get().owner();
    if (owner != null && HTTP_CONNECTION_PATTERN.matcher(owner.qualifiedName()).matches()) {
      diagnosticStorage.addDiagnostic(methodName, info.getMessage(methodName.getText()));
    }

    return ctx;
  }

  private static boolean isServerModule(DocumentContext documentContext) {
    return switch (documentContext.getModuleType()) {
      case ApplicationModule, CommandModule, FormModule, ManagedApplicationModule -> false;
      case CommonModule -> isServerCommonModule(documentContext);
      default -> true; // Все прочие модули — строго серверные
    };
  }

  private static boolean isServerCommonModule(DocumentContext documentContext) {
    var mdObject = documentContext.getMdObject();

    return mdObject.map(CommonModule.class::cast)
      .filter(commonModule -> !(commonModule.isClientManagedApplication()
        || commonModule.isClientOrdinaryApplication()))
      .isPresent();
  }

}
