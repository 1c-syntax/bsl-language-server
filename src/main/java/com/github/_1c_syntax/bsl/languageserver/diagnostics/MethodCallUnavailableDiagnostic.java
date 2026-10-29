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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessCheckKind;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnavailableMember;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Set;

/**
 * Сообщения об исходных и платформенных членах, недоступных в выбранном режиме.
 * <p>
 * Разрешение обращений и проверку доступности выполняет {@link MemberAccessChecker}.
 * Диагностика выбирает проверяемые режимы и формирует сообщения. Параметры принимают
 * {@code on}, {@code off}, {@code auto} без учёта регистра; остальные значения означают
 * {@code auto}. Файловые части используют параметр соответствующего режима приложения.
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {DiagnosticTag.ERROR}
)
@RequiredArgsConstructor
public class MethodCallUnavailableDiagnostic extends AbstractVisitorDiagnostic {
  private final CompilationContextResolver contextResolver;
  private final MemberAccessChecker accessChecker;

  /**
   * Проверка серверного контекста клиент-серверного варианта.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkServer = "auto";

  /**
   * Проверка тонкого клиента.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkThinClient = "auto";

  /**
   * Проверка веб-клиента.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkWebClient = "auto";

  /**
   * Проверка мобильного клиента приложения для платформы.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkMobileClient = "auto";

  /**
   * Проверка управляемого толстого клиента, включая общий и раздельные файловые контексты.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkThickClientManagedApplication = "auto";

  /**
   * Проверка обычного толстого клиента, включая общий и раздельные файловые контексты.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkThickClientOrdinaryApplication = "auto";

  /**
   * Проверка внешнего соединения в клиент-серверном и файловом вариантах.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkExternalConnection = "auto";

  /**
   * Проверка клиентской части приложения мобильной платформы.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkMobileApplicationClient = "auto";

  /**
   * Проверка серверной части приложения мобильной платформы.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkMobileApplicationServer = "auto";

  /**
   * Проверка мобильного автономного сервера; в режиме {@code auto} пока отключена.
   */
  @DiagnosticParameter(type = String.class, defaultValue = "auto")
  private String checkMobileStandaloneServer = "auto";


  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var automatic = contextResolver.automaticContexts(documentContext);
    var enabled = ExecutionContext.copyOf(ExecutionContext.ALL);
    enabled.removeIf(context -> !isEnabled(context, automatic));
    for (var problem : accessChecker.check(documentContext, enabled, Set.of(MemberAccessCheckKind.AVAILABILITY))) {
      var call = (UnavailableMember) problem;
      var context = info.getResourceString(call.context().name());
      String message;
      if (call.moduleUnavailable()) {
        message = info.getResourceString("moduleUnavailableMessage", call.moduleName().orElseThrow(), context);
      } else if (call.kind() == MemberKind.PROPERTY) {
        message = info.getResourceString("propertyUnavailableMessage", call.name(), context);
      } else if (call.moduleName().isEmpty()) {
        message = info.getMessage(call.name(), context);
      } else {
        message = info.getResourceString("commonMethodUnavailableMessage", call.name(),
          call.moduleName().orElseThrow(), context);
      }
      diagnosticStorage.addDiagnostic(call.range(), message);
    }
    return ctx;
  }

  private boolean isEnabled(ExecutionContext context, Set<ExecutionContext> automaticContexts) {
    var option = context.optionContext();
    var mode = ExecutionContext.SERVERS.contains(option) ? serverMode(option) : clientOrConnectionMode(option);
    if ("on".equalsIgnoreCase(mode)) {
      return true;
    }
    return !"off".equalsIgnoreCase(mode) && automaticContexts.contains(context);
  }

  private String serverMode(ExecutionContext context) {
    return switch (context) {
      case SERVER -> checkServer;
      case MOBILE_APPLICATION_SERVER -> checkMobileApplicationServer;
      case MOBILE_STANDALONE_SERVER -> checkMobileStandaloneServer;
      default -> throw new IllegalArgumentException("Expected a standalone server context: " + context);
    };
  }

  private String clientOrConnectionMode(ExecutionContext context) {
    return switch (context) {
      case THIN_CLIENT -> checkThinClient;
      case WEB_CLIENT -> checkWebClient;
      case MOBILE_CLIENT -> checkMobileClient;
      case THICK_CLIENT_MANAGED -> checkThickClientManagedApplication;
      case THICK_CLIENT_ORDINARY -> checkThickClientOrdinaryApplication;
      case EXTERNAL_CONNECTION -> checkExternalConnection;
      case MOBILE_APPLICATION_CLIENT -> checkMobileApplicationClient;
      default -> throw new IllegalArgumentException("Expected a client or connection context: " + context);
    };
  }

}
