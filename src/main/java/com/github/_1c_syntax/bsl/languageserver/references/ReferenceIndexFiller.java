/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ReferenceIndexFiller {

  private static final Set<ModuleType> DEFAULT_MODULE_TYPES = EnumSet.of(
    ModuleType.ManagerModule,
    ModuleType.CommonModule,
    ModuleType.UNKNOWN
  );

  private final ReferenceIndex index;

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    DocumentContext documentContext = event.getSource();
    fill(documentContext);
  }

  public void fill(DocumentContext documentContext) {
    index.clearReferences(documentContext.getUri());
    new ReferenceFinder(documentContext).visitFile(documentContext.getAst());
  }

  @RequiredArgsConstructor
  private class ReferenceFinder extends BSLParserBaseVisitor<BSLParserRuleContext> {

    private final DocumentContext documentContext;

    @Override
    public BSLParserRuleContext visitCallStatement(BSLParser.CallStatementContext ctx) {

      if (ctx.globalMethodCall() != null) {
        // see visitGlobalMethodCall
        return super.visitCallStatement(ctx);
      }

      String mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        return super.visitCallStatement(ctx);
      }

      getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));

      return super.visitCallStatement(ctx);
    }

    @Override
    public BSLParserRuleContext visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {

      String mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        return super.visitComplexIdentifier(ctx);
      }

      getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));

      return super.visitComplexIdentifier(ctx);
    }

    @Override
    public BSLParserRuleContext visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
      var mdoRef = MdoRefBuilder.getMdoRef(documentContext);
      var moduleType = documentContext.getModuleType();
      var methodName = ctx.methodName().getStart();
      var methodNameText = methodName.getText();

      documentContext.getSymbolTree().getMethods().stream()
        .filter(methodSymbol -> methodSymbol.getName().equalsIgnoreCase(methodNameText))
        .findAny()
        .ifPresent(methodSymbol -> addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName)));

      return super.visitGlobalMethodCall(ctx);
    }

    private void checkCall(String mdoRef, Token methodName) {

      String methodNameText = methodName.getText();
      Map<ModuleType, URI> modules = documentContext.getServerContext().getConfiguration().getModulesByMDORef(mdoRef);
      for (Map.Entry<ModuleType, URI> e : modules.entrySet()) {
        ModuleType moduleType = e.getKey();
        if (!DEFAULT_MODULE_TYPES.contains(moduleType)) {
          continue;
        }
        addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName));
      }
    }

    private void addMethodCall(String mdoRef, ModuleType moduleType, String methodName, Range range) {
      index.addMethodCall(documentContext.getUri(), mdoRef, moduleType, methodName, range);
    }

    private Optional<Token> getMethodName(BSLParser.CallStatementContext ctx) {
      var modifiers = ctx.modifier();
      Optional<Token> methodName;
      if (ctx.globalMethodCall() != null) {
        methodName = getMethodName(ctx.globalMethodCall());
      } else {
        methodName = getMethodName(ctx.accessCall());
      }

      if (modifiers.isEmpty()) {
        return methodName;
      } else {
        return getMethodName(modifiers).or(() -> methodName);
      }
    }

    private Optional<Token> getMethodName(BSLParser.GlobalMethodCallContext ctx) {
      return Optional.of(ctx.methodName().getStart());
    }

    private Optional<Token> getMethodName(BSLParser.AccessCallContext ctx) {
      return Optional.of(ctx.methodCall().methodName().getStart());
    }

    private Optional<Token> getMethodName(BSLParser.ComplexIdentifierContext ctx) {
      return getMethodName(ctx.modifier());
    }

    private Optional<Token> getMethodName(List<? extends BSLParser.ModifierContext> modifiers) {
      return modifiers.stream()
        .map(BSLParser.ModifierContext::accessCall)
        .filter(Objects::nonNull)
        .map(this::getMethodName)
        .findFirst()
        .orElse(Optional.empty());
    }
  }
}
