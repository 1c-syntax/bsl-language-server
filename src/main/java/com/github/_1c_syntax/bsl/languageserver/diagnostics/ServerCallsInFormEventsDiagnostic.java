/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;

import java.util.EnumSet;
import java.util.regex.Pattern;

/**
 * Диагностика для проверки вызовов серверных процедур в событиях форм.
 * 
 * Проверяет, что в событиях ПриАктивизацииСтроки и НачалоВыбора не вызываются
 * серверные процедуры, что может привести к проблемам в работе формы.
 * 
 * @see <a href="https://infostart.ru/1c/articles/1225834/">Статья на Инфостарте</a>
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule
  },
  minutesToFix = 15,
  tags = {
    DiagnosticTag.DESIGN
  }
)
public class ServerCallsInFormEventsDiagnostic extends AbstractListenerDiagnostic {

  /**
   * Регулярное выражение для поиска запрещенных событий форм.
   * Проверяет русские и английские названия событий ПриАктивизацииСтроки и НачалоВыбора.
   * Поддерживает регистронезависимый поиск.
   */
  private static final Pattern FORBIDDEN_EVENT_SUFFIX = CaseInsensitivePattern.compile( 
    ".*(ПриАктивизацииСтроки|OnActivateRow|НачалоВыбора|OnStartChoice)$"
  );

  /**
   * Множество серверных директив компиляции.
   * Содержит директивы &НаСервере и &НаСервереБезКонтекста.
   */
  private static final EnumSet<CompilerDirectiveKind> SERVER_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT
  );

  /**
   * Флаг, указывающий на нахождение внутри запрещенного события формы.
   * Автоматически сбрасывается при выходе из узла процедуры
   */
  private boolean isInForbiddenEvent;

  /**
   * Конструктор по умолчанию.
   */
  public ServerCallsInFormEventsDiagnostic() {
    super();
  }

  /**
   * Обрабатывает вход в процедуру или функцию.
   * 
   * Проверяет, является ли процедура или функция событием ПриАктивизацииСтроки или НачалоВыбора,
   * и устанавливает флаг для отслеживания контекста.
   * 
   * @param ctx контекст процедуры или функции
   */
  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    if (methodSymbol.isPresent()) {
      var methodName = methodSymbol.get().getName();
      
      // Проверяем, является ли это событием ПриАктивизацииСтроки или НачалоВыбора
      if (FORBIDDEN_EVENT_SUFFIX.matcher(methodName).matches()) {
        isInForbiddenEvent = true;
      }
    }
  }

  /**
   * Обрабатывает выход из процедуры или функции.
   * 
   * Автоматически сбрасывает флаг отслеживания контекста при выходе из любого узла процедуры.
   * Это обеспечивает корректную работу диагностики даже при вложенных процедурах.
   * 
   * @param ctx контекст процедуры или функции
   */
  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    isInForbiddenEvent = false;
  }

  /**
   * Обрабатывает вход в вызов глобального метода.
   * 
   * Проверяет, вызывается ли серверная процедура из событий ПриАктивизацииСтроки
   * или НачалоВыбора, и добавляет диагностическое сообщение при обнаружении нарушения.
   * 
   * @param ctx контекст вызова глобального метода
   */
  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    if (isInForbiddenEvent) {
      var methodName = ctx.methodName().getText();
      if (methodName != null && isServerMethod(methodName)) {
        diagnosticStorage.addDiagnostic(ctx,
          info.getMessage("message", methodName));
      }
    }
  }

  /**
   * Проверяет, является ли метод серверным.
   * 
   * Метод считается серверным, если он имеет директиву компиляции &НаСервере
   * или &НаСервереБезКонтекста.
   * 
   * @param methodName имя метода для проверки
   * @return true, если метод имеет серверную директиву компиляции
   */
  private boolean isServerMethod(String methodName) {
    var methodSymbolOpt = documentContext.getSymbolTree().getMethodSymbol(methodName);
    
    if (methodSymbolOpt.isEmpty()) {
      return false;
    }
    
    var methodSymbol = methodSymbolOpt.get();
    var directiveOpt = methodSymbol.getCompilerDirectiveKind();
    return directiveOpt.map(SERVER_DIRECTIVES::contains).orElse(false);
  }
}
