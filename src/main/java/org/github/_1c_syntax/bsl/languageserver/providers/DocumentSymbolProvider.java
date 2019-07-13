/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class DocumentSymbolProvider {

  private DocumentSymbolProvider() {
    // only statics
  }

  public static List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbol(DocumentContext documentContext) {

    List<DocumentSymbol> globalVariables = getGlobalVariables(documentContext);

    List<DocumentSymbol> symbols = new ArrayList<>(globalVariables);

    documentContext.getRegions().forEach(regionSymbol -> addRegion(documentContext, symbols, regionSymbol));

    documentContext.getMethods().stream()
      .filter(methodSymbol -> methodSymbol.getRegion() == null)
      .forEach((MethodSymbol methodSymbol) -> addMethod(symbols, methodSymbol));

    return symbols.stream()
      .map(Either::<SymbolInformation, DocumentSymbol>forRight)
      .collect(Collectors.toList());
  }

  private static void addRegion(
    DocumentContext documentContext,
    Collection<DocumentSymbol> symbols,
    RegionSymbol regionSymbol
  ) {
    DocumentSymbol documentSymbol = new DocumentSymbol(
      regionSymbol.getName(),
      SymbolKind.Namespace,
      RangeHelper.newRange(regionSymbol.getStartNode().getStart(), regionSymbol.getEndNode().getStop()),
      RangeHelper.newRange(regionSymbol.getNameNode())
    );

    List<DocumentSymbol> children = new ArrayList<>();
    regionSymbol.getChildren().forEach(childRegionSymbol -> addRegion(documentContext, children, childRegionSymbol));

    documentContext.getMethods().stream()
      .filter(methodSymbol -> regionSymbol.equals(methodSymbol.getRegion()))
      .forEach(methodSymbol -> addMethod(children, methodSymbol));

    documentSymbol.setChildren(children);

    symbols.add(documentSymbol);
  }

  private static void addMethod(Collection<DocumentSymbol> symbols, MethodSymbol methodSymbol) {
    BSLParserRuleContext context = methodSymbol.getNode();
    BSLParser.SubNameContext subNameContext = getSubNameContext(methodSymbol);

    DocumentSymbol documentSymbol = new DocumentSymbol(
      methodSymbol.getName(),
      SymbolKind.Method,
      RangeHelper.newRange(context),
      RangeHelper.newRange(subNameContext)
    );

    BSLParser.SubVarsContext subVariablesContext = getSubVarsContext(methodSymbol);
    List<DocumentSymbol> subVariables = getSubVariables(subVariablesContext);

    documentSymbol.setChildren(subVariables);

    symbols.add(documentSymbol);
  }



  private static BSLParser.SubNameContext getSubNameContext(MethodSymbol methodSymbol) {
    BSLParser.SubNameContext subNameContext;
    if (methodSymbol.isFunction()) {
      subNameContext = ((BSLParser.FunctionContext) methodSymbol.getNode()).funcDeclaration().subName();
    } else {
      subNameContext = ((BSLParser.ProcedureContext) methodSymbol.getNode()).procDeclaration().subName();
    }
    return subNameContext;
  }

  private static BSLParser.SubVarsContext getSubVarsContext(MethodSymbol methodSymbol) {
    BSLParser.SubVarsContext subVariablesContext;
    if (methodSymbol.isFunction()) {
      subVariablesContext = ((BSLParser.FunctionContext) methodSymbol.getNode()).subCodeBlock().subVars();
    } else {
      subVariablesContext = ((BSLParser.ProcedureContext) methodSymbol.getNode()).subCodeBlock().subVars();
    }
    return subVariablesContext;
  }

  private static List<DocumentSymbol> getSubVariables(@Nullable BSLParser.SubVarsContext subVariablesContext) {

    if (subVariablesContext == null) {
      return Collections.emptyList();
    }

    return subVariablesContext.subVar().stream()
      .flatMap(subVarContext -> subVarContext.subVarsList().subVarDeclaration().stream())
      .map(subVarDeclarationContext -> new DocumentSymbol(
        subVarDeclarationContext.var_name().getText(),
        SymbolKind.Variable,
        RangeHelper.newRange(subVarDeclarationContext),
        RangeHelper.newRange(subVarDeclarationContext.var_name())
      ))
      .collect(Collectors.toList());
  }

  private static List<DocumentSymbol> getGlobalVariables(DocumentContext documentContext) {
    BSLParser.ModuleVarsContext moduleVarsContext = documentContext.getAst().moduleVars();

    if (moduleVarsContext == null) {
      return Collections.emptyList();
    }

    return moduleVarsContext.moduleVar().stream()
      .flatMap(moduleVarContext -> moduleVarContext.moduleVarsList().moduleVarDeclaration().stream())
      .map(moduleVarDeclarationContext -> new DocumentSymbol(
        moduleVarDeclarationContext.var_name().getText(),
        SymbolKind.Variable,
        RangeHelper.newRange(moduleVarDeclarationContext),
        RangeHelper.newRange(moduleVarDeclarationContext.var_name())
      ))
      .collect(Collectors.toList());
  }
}
