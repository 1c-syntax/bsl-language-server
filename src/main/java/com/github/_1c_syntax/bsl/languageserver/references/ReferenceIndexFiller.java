/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    var documentContext = event.getSource();
    if (documentContext.isComputedDataFrozen()) {
      return;
    }
    fill(documentContext);
  }

  public void fill(DocumentContext documentContext) {
    index.clearReferences(documentContext.getUri());
    BSLParser.FileContext documentContextAst = documentContext.getAst();
    new MethodSymbolReferenceIndexFinder(documentContext).visitFile(documentContextAst);
    new VariableSymbolReferenceIndexFinder(documentContext).visitFile(documentContextAst);
  }

  @RequiredArgsConstructor
  private class MethodSymbolReferenceIndexFinder extends BSLParserBaseVisitor<BSLParserRuleContext> {

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

      documentContext.getSymbolTree().getMethodSymbol(methodNameText)
        .ifPresent(methodSymbol -> addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName)));

      return super.visitGlobalMethodCall(ctx);
    }

    private void checkCall(String mdoRef, Token methodName) {

      String methodNameText = methodName.getText();
      Map<ModuleType, URI> modulesMDO = documentContext.getServerContext().getConfiguration().getModulesByMDORef(mdoRef);
      Map<ModuleType, URI> modules = modulesMDO.entrySet().stream()
        .collect(Collectors.toMap(m -> ModuleType.valueOf(m.getKey().name()), Map.Entry::getValue));

      for (Map.Entry<ModuleType, URI> e : modules.entrySet()) {
        var moduleType = e.getKey();
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

  @RequiredArgsConstructor
  private class VariableSymbolReferenceIndexFinder extends BSLParserBaseVisitor<BSLParserRuleContext> {

    private final DocumentContext documentContext;
    private SourceDefinedSymbol currentScope;

    @Override
    public BSLParserRuleContext visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {
      findVariableSymbol(ctx.var_name().getText()).ifPresent(s -> {
        if (notVariableInitialization(ctx, s)) {
          addVariableUsage(
            s.getRootParent(SymbolKind.Method),
            ctx.var_name().getText(),
            Ranges.create(ctx.var_name()),
            false
          );
        }
      });

      return ctx;
    }

    @Override
    public BSLParserRuleContext visitSub(BSLParser.SubContext ctx) {
      currentScope = documentContext.getSymbolTree().getModule();

      if (!Trees.nodeContainsErrors(ctx)) {
        documentContext
          .getSymbolTree()
          .getMethodSymbol(ctx)
          .ifPresent(scope -> currentScope = scope);
      }

      BSLParserRuleContext result = super.visitSub(ctx);
      currentScope = documentContext.getSymbolTree().getModule();
      return result;
    }

    @Override
    public BSLParserRuleContext visitLValue(BSLParser.LValueContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitLValue(ctx);
      }

      findVariableSymbol(ctx.IDENTIFIER().getText()).ifPresent((VariableSymbol s) -> {
        if (notVariableInitialization(ctx, s)) {
          addVariableUsage(
            s.getRootParent(SymbolKind.Method),
            ctx.IDENTIFIER().getText(),
            Ranges.create(ctx.IDENTIFIER()),
            ctx.acceptor() != null
          );
        }
      });

      return super.visitLValue(ctx);
    }

    @Override
    public BSLParserRuleContext visitCallStatement(BSLParser.CallStatementContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitCallStatement(ctx);
      }

      var variableName = ctx.IDENTIFIER().getText();
      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(s.getRootParent(SymbolKind.Method), variableName, Ranges.create(ctx.IDENTIFIER()), true));
      return super.visitCallStatement(ctx);
    }

    @Override
    public BSLParserRuleContext visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitComplexIdentifier(ctx);
      }

      var variableName = ctx.IDENTIFIER().getText();
      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(s.getRootParent(SymbolKind.Method), variableName, Ranges.create(ctx.IDENTIFIER()), true));
      return super.visitComplexIdentifier(ctx);
    }

    @Override
    public BSLParserRuleContext visitForStatement(BSLParser.ForStatementContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitForStatement(ctx);
      }

      findVariableSymbol(ctx.IDENTIFIER().getText()).ifPresent((VariableSymbol s) -> {
        if (notVariableInitialization(ctx, s)) {
          addVariableUsage(
            s.getRootParent(SymbolKind.Method),
            ctx.IDENTIFIER().getText(),
            Ranges.create(ctx.IDENTIFIER()),
            false
          );
        }
      });

      return super.visitForStatement(ctx);
    }

    @Override
    public BSLParserRuleContext visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitForEachStatement(ctx);
      }

      findVariableSymbol(ctx.IDENTIFIER().getText()).ifPresent((VariableSymbol s) -> {
        if (notVariableInitialization(ctx, s)) {
          addVariableUsage(
            s.getRootParent(SymbolKind.Method),
            ctx.IDENTIFIER().getText(),
            Ranges.create(ctx.IDENTIFIER()),
            false
          );
        }
      });

      return super.visitForEachStatement(ctx);
    }

    private Optional<VariableSymbol> findVariableSymbol(String variableName) {
      var variableSymbol = documentContext.getSymbolTree()
        .getVariableSymbol(variableName, currentScope);

      if (variableSymbol.isPresent()) {
        return variableSymbol;
      }

      return documentContext.getSymbolTree()
        .getVariableSymbol(variableName, documentContext.getSymbolTree().getModule());
    }

    private boolean notVariableInitialization(BSLParser.LValueContext ctx, VariableSymbol variableSymbol) {
      return !Ranges.containsRange(variableSymbol.getRange(), Ranges.create(ctx));
    }

    private boolean notVariableInitialization(BSLParser.ModuleVarDeclarationContext ctx, VariableSymbol variableSymbol) {
      return !Ranges.containsRange(variableSymbol.getRange(), Ranges.create(ctx));
    }

    private boolean notVariableInitialization(BSLParser.ForStatementContext ctx, VariableSymbol variableSymbol) {
      return !Ranges.containsRange(variableSymbol.getRange(), Ranges.create(ctx.IDENTIFIER()));
    }

    private boolean notVariableInitialization(BSLParser.ForEachStatementContext ctx, VariableSymbol variableSymbol) {
      return !Ranges.containsRange(variableSymbol.getRange(), Ranges.create(ctx.IDENTIFIER()));
    }

    private void addVariableUsage(Optional<SourceDefinedSymbol> methodSymbol,
                                  String variableName,
                                  Range range,
                                  boolean usage) {
      String methodName = "";

      if (methodSymbol.isPresent()) {
        methodName = methodSymbol.get().getName();
      }

      index.addVariableUsage(
        documentContext.getUri(),
        MdoRefBuilder.getMdoRef(documentContext),
        documentContext.getModuleType(),
        methodName,
        variableName,
        range,
        !usage
      );
    }

  }

}
