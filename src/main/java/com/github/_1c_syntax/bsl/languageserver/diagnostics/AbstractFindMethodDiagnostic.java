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

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.Getter;
import lombok.Setter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

/**
 * Абстрактная диагностика, предназначенная для поиска вызова обычных методов и методов глобального контекста
 * с использованием регулярного выражения.
 * {@code AbstractFindMethodDiagnostic} предоставляет для переопределения два метода проверки вызовов и один
 * метод генерации сообщения пользователю.
 * По умолчанию проверяется, что имя вызываемого метода соответствует переданному в конструкторе регулярному выражению.
 * <b>Важно:</b> наследование данной диагностики без переопределения {@code getMessage} подразумевает, что первым
 * параметром сообщения пользователю <b>всегда</b> будет имя найденного метода.
 */
public abstract class AbstractFindMethodDiagnostic extends AbstractVisitorDiagnostic {

  @Getter
  @Setter
  private Pattern methodPattern;

  /**
   * Конструктор по умолчанию
   *
   * @param pattern регулярное выражение для проверки
   */
  AbstractFindMethodDiagnostic(Pattern pattern) {
    methodPattern = pattern;
  }

  /**
   * Проверка контекста глобального метода
   *
   * @param ctx контекст глобального метода
   * @return {@code true} если имя метода соответствует регулярному выражению
   */
  protected boolean checkGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    return getMethodPattern().matcher(ctx.methodName().getText()).matches();
  }

  /**
   * Проверка контекста обычного метода
   *
   * @param ctx контекст метода
   * @return {@code true} если имя метода соответствует регулярному выражению
   */
  protected boolean checkMethodCall(BSLParser.MethodCallContext ctx) {
    return getMethodPattern().matcher(ctx.methodName().getText()).matches();
  }

  /**
   * Получает сообщение диагностики для пользователя
   *
   * @param ctx контекст узла
   * @return В случае если передан контекст метода, параметризованное сообщение,
   * первым параметром которого <b>всегда</b> будет имя метода.
   * В противном случае возвращается обычное сообщение без параметров.
   */
  protected String getMessage(ParserRuleContext ctx) {

    if (ctx instanceof BSLParser.GlobalMethodCallContext globalMethodCallContext) {
      return info.getMessage(globalMethodCallContext.methodName().getText());
    } else if (ctx instanceof BSLParser.MethodCallContext methodCallContext) {
      return info.getMessage(methodCallContext.methodName().getText());
    } else {
      return info.getMessage();
    }

  }

  /**
   * Обработчик узла глобального метода. Добавляет информацию о сработавшей диагностике
   * в случае если проверка метода {@link AbstractFindMethodDiagnostic#checkGlobalMethodCall(BSLParser.GlobalMethodCallContext)}
   * возвращает {@code true}
   *
   * @param ctx контекст глобального метода
   * @return результат посещения ноды по умолчанию.
   */
  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (checkGlobalMethodCall(ctx)) {
      diagnosticStorage.addDiagnostic(ctx.methodName(), getMessage(ctx));
    }

    return super.visitGlobalMethodCall(ctx);
  }

  /**
   * Обработчик узла обычного метода. Добавляет информацию о сработавшей диагностике
   * в случае если проверка метода {@link AbstractFindMethodDiagnostic#checkMethodCall(BSLParser.MethodCallContext)}
   * возвращает {@code true}
   *
   * @param ctx контекст метода
   * @return результат посещения ноды по умолчанию.
   */
  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {

    if (checkMethodCall(ctx)) {
      diagnosticStorage.addDiagnostic(ctx.methodName(), getMessage(ctx));
    }

    return super.visitMethodCall(ctx);
  }
}
