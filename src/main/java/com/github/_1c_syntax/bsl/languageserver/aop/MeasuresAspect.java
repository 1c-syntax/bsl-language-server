/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.aop.measures.MeasureCollector;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Collection;

@Aspect
@NoArgsConstructor
public class MeasuresAspect {

  @Setter(onMethod = @__({@Autowired}))
  private MeasureCollector measureCollector;

  @PreDestroy
  void destroy() {
    setMeasureCollector(null);
  }

  @Around("Pointcuts.isBSLDiagnostic() && Pointcuts.isGetDiagnosticsCall()")
  public Object measureBSLDiagnostic(ProceedingJoinPoint jp) throws Throwable {
    if (measureCollector == null) {
      return jp.proceed();
    }
    var diagnostic = (BSLDiagnostic) jp.getThis();
    var diagnosticCode = diagnostic.getInfo().getCode().getStringValue();
    return measureCollector.measureIt(jp::proceed, "diagnostic: " + diagnosticCode);
  }

  @Before("Pointcuts.isDocumentContext() && execution(* computeSymbolTree(..))")
  public void measureGetTokens(JoinPoint jp) {
    if (measureCollector == null) {
      return;
    }
    var documentContext = (DocumentContext) jp.getTarget();
    measureCollector.measureIt(documentContext::getTokens, "context: tokens");
    measureCollector.measureIt(documentContext::getAst, "context: ast");
  }

  @Around("Pointcuts.isDocumentContext() && execution(* computeSymbolTree(..))")
  public Object measureComputeSymbolTree(ProceedingJoinPoint jp) throws Throwable {
    if (measureCollector == null) {
      return jp.proceed();
    }
    return measureCollector.measureIt(jp::proceed, "context: symbolTree");
  }

  @Before("Pointcuts.isServerContext() && execution(* populateContext(..)) && args(files)")
  public void initializeConfiguration(JoinPoint jp, Collection<File> files) {
    if (measureCollector == null) {
      return;
    }
    var serverContext = (ServerContext) jp.getTarget();
    measureCollector.measureIt(serverContext::getConfiguration, "context: configuration");
  }

  @Around("within(com.github._1c_syntax.bsl.languageserver.context.computer.*) && execution(* compute(..))")
  public Object measureComputers(ProceedingJoinPoint jp) throws Throwable {
    if (measureCollector == null) {
      return jp.proceed();
    }
    var simpleName = jp.getTarget().getClass().getSimpleName();
    return measureCollector.measureIt(jp::proceed, "computer: " + simpleName);
  }

  @AfterReturning("within(com.github._1c_syntax.bsl.languageserver.cli.AnalyzeCommand) && execution(* call(..))")
  public void printMeasures() {
    if (measureCollector == null) {
      return;
    }
    measureCollector.printMeasures();
  }
}
