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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

/**
 * Диагностика устаревших методов объекта {@code HTTPСоединение} /
 * {@code HTTPConnection}.
 * <p>
 * В платформе 8.3.21 синхронные методы HTTP-соединения объявлены устаревшими
 * в клиентском контексте. Вместо них следует использовать асинхронные аналоги
 * с суффиксом {@code Асинх} / {@code Async}. Асинхронные методы доступны только
 * на клиенте, поэтому диагностика сообщает только о вызовах, которые
 * гарантированно исполняются на клиенте.
 * <p>
 * Контекст исполнения определяется по типу модуля и директиве компиляции метода.
 * В модулях приложения и клиентских общих модулях директив нет — весь код
 * считается клиентским. В модулях управляемых форм и команд сообщаются только
 * методы с директивой {@code &НаКлиенте}; методы без директивы, а также методы
 * с директивами {@code &НаСервере}, {@code &НаСервереБезКонтекста} и
 * {@code &НаКлиентеНаСервере} компилируются в том числе для сервера и не
 * сообщаются. В обычных формах директивы не применяются, поэтому весь модуль
 * считается клиентским.
 * <p>
 * Чтобы исключить ложные срабатывания, тип-владелец метода резолвится через
 * {@link TypeService#memberAt}: диагностика срабатывает только если метод
 * вызван на объекте типа {@code HTTPСоединение}.
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

  /**
   * Признак того, что вложенный узел дерева разбора исполняется на клиенте.
   * Значение для тела модуля выставляется в {@link #visitFile}, для методов —
   * в {@link #visitSub}.
   */
  private boolean clientContext;

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (!isClientCapableModule()) {
      return ctx;
    }
    clientContext = isDirectiveLessClientModule();
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    var directive = documentContext.getSymbolTree()
      .getMethodSymbol(ctx)
      .flatMap(MethodSymbol::getCompilerDirectiveKind);

    if (directive.isPresent()) {
      if (directive.get() != CompilerDirectiveKind.AT_CLIENT) {
        return ctx;
      }
    } else if (!isDirectiveLessClientModule()) {
      return ctx;
    }

    var savedClientContext = clientContext;
    clientContext = true;
    try {
      return super.visitSub(ctx);
    } finally {
      clientContext = savedClientContext;
    }
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    if (!clientContext) {
      return super.visitMethodCall(ctx);
    }

    var methodName = ctx.methodName();
    if (methodName == null) {
      return super.visitMethodCall(ctx);
    }

    if (!MESSAGE_PATTERN.matcher(methodName.getText()).matches()) {
      return super.visitMethodCall(ctx);
    }

    var identifier = methodName.IDENTIFIER();
    if (identifier == null) {
      return super.visitMethodCall(ctx);
    }

    var typedMember = typeService.memberAt(documentContext, identifier);
    if (typedMember.isEmpty()) {
      return super.visitMethodCall(ctx);
    }

    var owner = typedMember.get().owner();
    if (owner != null && HTTP_CONNECTION_PATTERN.matcher(owner.qualifiedName()).matches()) {
      diagnosticStorage.addDiagnostic(methodName, info.getMessage(methodName.getText()));
    }

    return super.visitMethodCall(ctx);
  }

  private boolean isClientCapableModule() {
    return switch (documentContext.getModuleType()) {
      case ApplicationModule, OrdinaryApplicationModule, ManagedApplicationModule,
           FormModule, CommandModule -> true;
      case CommonModule -> isClientOnlyCommonModule();
      default -> false;
    };
  }

  private boolean isDirectiveLessClientModule() {
    return switch (documentContext.getModuleType()) {
      case ApplicationModule, OrdinaryApplicationModule, ManagedApplicationModule -> true;
      case FormModule -> isOrdinaryForm();
      case CommonModule -> isClientOnlyCommonModule();
      default -> false;
    };
  }

  private boolean isClientOnlyCommonModule() {
    var mdObject = documentContext.getMdObject();

    if (mdObject.isEmpty()) {
      // Метаданные недоступны: модуль не считаем строго серверным.
      return true;
    }

    if (!(mdObject.get() instanceof CommonModule commonModule)) {
      return false;
    }

    return (commonModule.isClientManagedApplication() || commonModule.isClientOrdinaryApplication())
      && !commonModule.isServer()
      && !commonModule.isExternalConnection()
      && !commonModule.isServerCall();
  }

  private boolean isOrdinaryForm() {
    return documentContext.getMdObject()
      .filter(Form.class::isInstance)
      .map(Form.class::cast)
      .map(form -> form.getFormType() != FormType.MANAGED)
      .orElse(false);
  }

}
