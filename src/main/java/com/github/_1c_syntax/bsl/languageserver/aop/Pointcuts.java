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
package com.github._1c_syntax.bsl.languageserver.aop;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.aspectj.lang.annotation.Pointcut;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Сборник общих Pointcut для AOP-слоя.
 */
public class Pointcuts {

  /**
   * Это обращение к одному из классов продукта.
   */
  @Pointcut("within(com.github._1c_syntax.bsl.languageserver..*)")
  public void isBSLLanguageServerScope() {
    // no-op
  }

  /**
   * Это обращение к классу {@link LanguageServerConfiguration}.
   */
  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration)")
  public void isLanguageServerConfiguration() {
    // no-op
  }

  /**
   * Это обращение к классу {@link DocumentContext}.
   */
  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.context.DocumentContext)")
  public void isDocumentContext() {
    // no-op
  }

  /**
   * Это обращение к реализации {@link LanguageServer}.
   */
  @Pointcut("within(org.eclipse.lsp4j.services.LanguageServer+)")
  public void isLanguageServer() {
    // no-op
  }

  /**
   * Это обращение к реализации {@link TextDocumentService}.
   */
  @Pointcut("within(org.eclipse.lsp4j.services.TextDocumentService+)")
  public void isTextDocumentService() {
    // no-op
  }

  /**
   * Это обращение к реализации {@link WorkspaceService}.
   */
  @Pointcut("within(org.eclipse.lsp4j.services.WorkspaceService+)")
  public void isWorkspaceService() {
    // no-op
  }

  /**
   * Это обращение к классу {@link ServerContext}.
   */
  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.context.ServerContext)")
  public void isServerContext() {
    // no-op
  }

  /**
   * Это обращение к реализации интерфейса {@link BSLDiagnostic}.
   */
  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic+)")
  public void isBSLDiagnostic() {
    // no-op
  }

  /**
   * Это вызов метода rebuild.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* rebuild(..))")
  public void isRebuildCall() {
    // no-op
  }

  /**
   * Это вызов метода populateContext.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* populateContext(..))")
  public void isPopulateContextCall() {
    // no-op
  }

  /**
   * Это вызов метода removeDocument.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* removeDocument(..))")
  public void isRemoveDocumentCall() {
    // no-op
  }

  /**
   * Это вызов метода closeDocument.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* closeDocument(..))")
  public void isCloseDocumentCall() {
    // no-op
  }

  /**
   * Это вызов метода update.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* update(..))")
  public void isUpdateCall() {
    // no-op
  }

  /**
   * Это вызов метода reset.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* reset(..))")
  public void isResetCall() {
    // no-op
  }

  /**
   * Это вызов метода getDiagnostics.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* getDiagnostics(..))")
  public void isGetDiagnosticsCall() {
    // no-op
  }

  /**
   * Это вызов метода initialize.
   */
  @Pointcut("isBSLLanguageServerScope() && execution(* initialize(..))")
  public void isInitializeCall() {
    // no-op
  }

  /**
   * Это вызов публичного метода.
   */
  @Pointcut("execution(public * com.github._1c_syntax.bsl.languageserver..*(..))")
  public void isPublicMethodCall() {
    // no-op
  }
}
