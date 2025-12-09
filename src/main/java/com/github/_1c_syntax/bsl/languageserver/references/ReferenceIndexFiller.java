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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.bsl.languageserver.utils.ModuleReference;
import com.github._1c_syntax.bsl.languageserver.utils.Modules;
import com.github._1c_syntax.bsl.languageserver.utils.NotifyDescription;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Strings;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Заполнитель индекса ссылок.
 * <p>
 * Обрабатывает события изменения контекста документов
 * и обновляет индекс ссылок на символы.
 */
@Component
@RequiredArgsConstructor
public class ReferenceIndexFiller {

  private static final Set<ModuleType> DEFAULT_MODULE_TYPES = EnumSet.of(
    ModuleType.ManagerModule,
    ModuleType.CommonModule,
    ModuleType.UNKNOWN
  );

  private final ReferenceIndex index;
  private final LanguageServerConfiguration languageServerConfiguration;

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    if (documentContext.isComputedDataFrozen()) {
      return;
    }
    fill(documentContext);
  }

  /**
   * Обрабатывает событие удаления документа из контекста сервера.
   * <p>
   * При удалении документа очищает все зарегистрированные в индексе ссылки,
   * исходящие из этого документа (вызовы методов, использование переменных и т.д.).
   *
   * @param event событие удаления документа
   */
  @EventListener
  public void handleEvent(ServerContextDocumentRemovedEvent event) {
    index.clearReferences(event.getUri());
  }

  public void fill(DocumentContext documentContext) {
    index.clearReferences(documentContext.getUri());
    var documentContextAst = documentContext.getAst();
    new MethodSymbolReferenceIndexFinder(documentContext).visitFile(documentContextAst);
    new VariableSymbolReferenceIndexFinder(documentContext).visitFile(documentContextAst);
  }

  @RequiredArgsConstructor
  private class MethodSymbolReferenceIndexFinder extends BSLParserBaseVisitor<ParserRuleContext> {

    private final DocumentContext documentContext;
    private Set<String> commonModuleMdoRefFromSubParams = Collections.emptySet();

    @Override
    public ParserRuleContext visitProcDeclaration(BSLParser.ProcDeclarationContext ctx) {
      commonModuleMdoRefFromSubParams = calcParams(ctx.paramList());
      return super.visitProcDeclaration(ctx);
    }

    @Override
    public ParserRuleContext visitFuncDeclaration(BSLParser.FuncDeclarationContext ctx) {
      commonModuleMdoRefFromSubParams = calcParams(ctx.paramList());
      return super.visitFuncDeclaration(ctx);
    }

    @Override
    public ParserRuleContext visitCallStatement(BSLParser.CallStatementContext ctx) {
      if (ctx.globalMethodCall() != null) {
        // see visitGlobalMethodCall
        return super.visitCallStatement(ctx);
      }

      var mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        return super.visitCallStatement(ctx);
      }

      // Добавляем ссылку на модуль по позиции идентификатора (только для общих модулей)
      addModuleReferenceForCommonModuleIdentifier(ctx.IDENTIFIER());

      Methods.getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));

      return super.visitCallStatement(ctx);
    }

    @Override
    public ParserRuleContext visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
      var mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        return super.visitComplexIdentifier(ctx);
      }

      // Добавляем ссылку на модуль по позиции идентификатора (только для общих модулей)
      addModuleReferenceForCommonModuleIdentifier(ctx.IDENTIFIER());

      Methods.getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));
      return super.visitComplexIdentifier(ctx);
    }

    @Override
    public ParserRuleContext visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
      var mdoRef = documentContext.getMdoRef();
      var moduleType = documentContext.getModuleType();
      var methodName = ctx.methodName().getStart();
      var methodNameText = methodName.getText();

      documentContext.getSymbolTree().getMethodSymbol(methodNameText)
        .ifPresent(methodSymbol -> addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName)));

      return super.visitGlobalMethodCall(ctx);
    }

    @Override
    public ParserRuleContext visitNewExpression(BSLParser.NewExpressionContext ctx) {
      if (NotifyDescription.isNotifyDescription(ctx)) {
        final var doCallContext = ctx.doCall();
        if (doCallContext == null) {
          return super.visitNewExpression(ctx);
        }
        var callParamList = doCallContext.callParamList().callParam();

        if (NotifyDescription.notifyDescriptionContainsHandler(callParamList)) {
          addCallbackMethodCall(
            callParamList.get(NotifyDescription.HANDLER_INDEX),
            getModule(callParamList.get(NotifyDescription.HANDLER_MODULE_INDEX))
          );
        }

        if (NotifyDescription.notifyDescriptionContainsErrorHandler(callParamList)) {
          addCallbackMethodCall(
            callParamList.get(NotifyDescription.HANDLER_ERROR_INDEX),
            getModule(callParamList.get(NotifyDescription.HANDLER_ERROR_MODULE_INDEX))
          );
        }

        return super.visitNewExpression(ctx);
      }

      return super.visitNewExpression(ctx);
    }

    @Override
    public ParserRuleContext visitLValue(BSLParser.LValueContext ctx) {
      final var identifier = ctx.IDENTIFIER();
      if (identifier != null) {
        final List<? extends BSLParser.ModifierContext> modifiers = Optional.ofNullable(ctx.acceptor())
          .map(BSLParser.AcceptorContext::modifier)
          .orElseGet(Collections::emptyList);
        var mdoRef = MdoRefBuilder.getMdoRef(documentContext, identifier, modifiers);
        if (!mdoRef.isEmpty()) {
          Methods.getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));
        }
      }
      return super.visitLValue(ctx);
    }

    private void checkCall(String mdoRef, Token methodName) {
      var methodNameText = Strings.trimQuotes(methodName.getText());
      final var configuration = documentContext.getServerContext().getConfiguration();
      var modules = configuration.mdoModuleTypes(mdoRef);
      for (ModuleType moduleType : modules.keySet()) {
        if (!DEFAULT_MODULE_TYPES.contains(moduleType)
          || (moduleType == ModuleType.CommonModule && commonModuleMdoRefFromSubParams.contains(mdoRef))) {
          continue;
        }
        addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName));
      }
    }

    /**
     * Добавляет ссылку на модуль по позиции идентификатора, только если идентификатор является
     * именем общего модуля. Для вызовов вида Справочники.Имя.Метод() ссылка не добавляется,
     * так как "Справочники" - это тип MDO, а не имя модуля.
     */
    private void addModuleReferenceForCommonModuleIdentifier(@Nullable TerminalNode identifier) {
      if (identifier == null) {
        return;
      }

      var identifierText = identifier.getText();

      documentContext.getServerContext()
        .getConfiguration()
        .findCommonModule(identifierText)
        .ifPresent(commonModule -> {
          index.addModuleReference(
            documentContext.getUri(),
            commonModule.getMdoReference().getMdoRef(),
            ModuleType.CommonModule,
            Ranges.create(identifier)
          );
        });
    }

    private void addMethodCall(String mdoRef, ModuleType moduleType, String methodName, Range range) {
      index.addMethodCall(documentContext.getUri(), mdoRef, moduleType, methodName, range);
    }

    private void addCallbackMethodCall(BSLParser.CallParamContext methodName, String mdoRef) {
      // todo: move this out of method 
      if (mdoRef.isEmpty()) {
        return;
      }
      Methods.getMethodName(methodName).ifPresent((Token methodNameToken) -> {
        if (!mdoRef.equals(documentContext.getMdoRef())) {
          checkCall(mdoRef, methodNameToken);
        }

        addMethodCall(
          mdoRef,
          documentContext.getModuleType(),
          Strings.trimQuotes(methodName.getText()),
          Ranges.create(methodName)
        );
      });
    }

    private String getModule(BSLParser.CallParamContext callParamContext) {
      final var complexIdentifierContext1 = NotifyDescription.getFirstMember(callParamContext)
        .map(BSLParser.MemberContext::complexIdentifier)
        .filter(complexIdentifierContext -> complexIdentifierContext.IDENTIFIER() != null)
        .filter(complexIdentifierContext -> complexIdentifierContext.modifier().isEmpty());
      if (complexIdentifierContext1.isEmpty()) {
        return "";
      }
      return complexIdentifierContext1
        .filter(Predicate.not(Modules::isThisObject))
        .map(complexIdentifier -> MdoRefBuilder.getMdoRef(documentContext, complexIdentifier))
        .orElse(documentContext.getMdoRef());
    }

    private Set<String> calcParams(BSLParser.@Nullable ParamListContext paramList) {
      if (paramList == null) {
        return Collections.emptySet();
      }
      final var configuration = documentContext.getServerContext().getConfiguration();
      return paramList.param().stream()
        .map(BSLParser.ParamContext::IDENTIFIER)
        .filter(Objects::nonNull)
        .map(ParseTree::getText)
        .map(configuration::findCommonModule)
        .filter(Optional::isPresent)
        .flatMap(Optional::stream)
        .map(MD::getMdoRef)
        .collect(Collectors.toSet());
    }
  }

  private class VariableSymbolReferenceIndexFinder extends BSLParserBaseVisitor<ParserRuleContext> {

    private final DocumentContext documentContext;
    private final ModuleReference.ParsedAccessors parsedAccessors;
    @SuppressWarnings("NullAway.Init")
    private SourceDefinedSymbol currentScope;
    private final Map<String, String> variableToCommonModuleMap = new HashMap<>();

    private VariableSymbolReferenceIndexFinder(DocumentContext documentContext) {
      this.documentContext = documentContext;
      this.parsedAccessors = ModuleReference.parseAccessors(
        languageServerConfiguration.getReferencesOptions().getCommonModuleAccessors()
      );
    }

    @Override
    public ParserRuleContext visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {
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
    public ParserRuleContext visitSub(BSLParser.SubContext ctx) {
      currentScope = documentContext.getSymbolTree().getModule();

      // При входе в новый метод очищаем mappings только для локальных переменных.
      // Модульные переменные должны сохраняться между методами.
      clearLocalVariableMappings();

      if (!Trees.nodeContainsErrors(ctx)) {
        documentContext
          .getSymbolTree()
          .getMethodSymbol(ctx)
          .ifPresent(scope -> currentScope = scope);
      }

      var result = super.visitSub(ctx);
      currentScope = documentContext.getSymbolTree().getModule();
      return result;
    }

    /**
     * Очищает mappings для локальных переменных, сохраняя модульные.
     */
    private void clearLocalVariableMappings() {
      var moduleSymbolTree = documentContext.getSymbolTree();
      var module = moduleSymbolTree.getModule();

      // Оставляем только те mappings, которые соответствуют модульным переменным
      variableToCommonModuleMap.keySet().removeIf(variableKey -> {
        // Ищем переменную на уровне модуля
        var moduleVariable = moduleSymbolTree.getVariableSymbol(variableKey, module);
        // Если переменной нет на уровне модуля - это локальная переменная, удаляем mapping
        return moduleVariable.isEmpty();
      });
    }

    @Override
    public ParserRuleContext visitAssignment(BSLParser.AssignmentContext ctx) {
      // Detect pattern: Variable = ОбщегоНазначения.ОбщийМодуль("ModuleName") or Variable = ОбщийМодуль("ModuleName")
      var lValue = ctx.lValue();
      var expression = ctx.expression();

      if (lValue != null && lValue.IDENTIFIER() != null && expression != null) {
        var variableKey = lValue.IDENTIFIER().getText().toLowerCase(Locale.ENGLISH);
        if (ModuleReference.isCommonModuleExpression(expression, parsedAccessors)) {
          var commonModuleOpt = ModuleReference.extractCommonModuleName(expression, parsedAccessors)
            .flatMap(moduleName -> documentContext.getServerContext()
              .getConfiguration()
              .findCommonModule(moduleName));
          if (commonModuleOpt.isPresent()) {
            var mdoRef = commonModuleOpt.get().getMdoReference().getMdoRef();
            variableToCommonModuleMap.put(variableKey, mdoRef);

            index.addModuleReference(
              documentContext.getUri(),
              mdoRef,
              ModuleType.CommonModule,
              Ranges.create(expression)
            );
          } else {
            // Модуль не найден - удаляем старый mapping если был
            variableToCommonModuleMap.remove(variableKey);
          }
        } else {
          // Переменная переназначена на что-то другое - очищаем mapping
          variableToCommonModuleMap.remove(variableKey);
        }
      }

      return super.visitAssignment(ctx);
    }

    @Override
    public ParserRuleContext visitLValue(BSLParser.LValueContext ctx) {
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
    public ParserRuleContext visitCallStatement(BSLParser.CallStatementContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitCallStatement(ctx);
      }

      var variableName = ctx.IDENTIFIER().getText();

      // Check if variable references a common module
      var commonModuleMdoRef = variableToCommonModuleMap.get(variableName.toLowerCase(Locale.ENGLISH));

      if (commonModuleMdoRef != null) {
        // Process method calls on the common module variable
        // Check both modifiers and accessCall
        if (!ctx.modifier().isEmpty()) {
          processCommonModuleMethodCalls(ctx.modifier(), commonModuleMdoRef);
        }
        if (ctx.accessCall() != null) {
          processCommonModuleAccessCall(ctx.accessCall(), commonModuleMdoRef);
        }
      }

      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(
            s.getRootParent(SymbolKind.Method), variableName, Ranges.create(ctx.IDENTIFIER()), true
          )
        );
      return super.visitCallStatement(ctx);
    }

    @Override
    public ParserRuleContext visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitComplexIdentifier(ctx);
      }

      var variableName = ctx.IDENTIFIER().getText();

      // Check if we are inside a callStatement - if so, skip processing here to avoid duplication
      var parentCallStatement = Trees.getRootParent(ctx, BSLParser.RULE_callStatement);
      var isInsideCallStatement = false;
      if (parentCallStatement instanceof BSLParser.CallStatementContext callStmt) {
        isInsideCallStatement = callStmt.IDENTIFIER() != null
          && callStmt.IDENTIFIER().getText().equalsIgnoreCase(variableName);
      }

      // Check if variable references a common module
      var commonModuleMdoRef = variableToCommonModuleMap.get(variableName.toLowerCase(Locale.ENGLISH));
      if (commonModuleMdoRef != null && !ctx.modifier().isEmpty() && !isInsideCallStatement) {
        // Process method calls on the common module variable
        processCommonModuleMethodCalls(ctx.modifier(), commonModuleMdoRef);
      }

      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(
            s.getRootParent(SymbolKind.Method), variableName, Ranges.create(ctx.IDENTIFIER()), true
          )
        );
      return super.visitComplexIdentifier(ctx);
    }

    @Override
    public ParserRuleContext visitForStatement(BSLParser.ForStatementContext ctx) {
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
    public ParserRuleContext visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
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

    private boolean notVariableInitialization(BSLParser.ModuleVarDeclarationContext ctx,
                                              VariableSymbol variableSymbol) {
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
      var methodName = "";

      if (methodSymbol.isPresent()) {
        methodName = methodSymbol.get().getName();
      }

      index.addVariableUsage(
        documentContext.getUri(),
        documentContext.getMdoRef(),
        documentContext.getModuleType(),
        methodName,
        variableName,
        range,
        !usage
      );
    }

    private void processCommonModuleMethodCalls(List<? extends BSLParser.ModifierContext> modifiers, String mdoRef) {
      for (var modifier : modifiers) {
        var accessCall = modifier.accessCall();
        if (accessCall != null) {
          processCommonModuleAccessCall(accessCall, mdoRef);
        }
      }
    }

    private void processCommonModuleAccessCall(BSLParser.AccessCallContext accessCall, String mdoRef) {
      var methodCall = accessCall.methodCall();
      if (methodCall != null && methodCall.methodName() != null) {
        var methodNameToken = methodCall.methodName().IDENTIFIER();
        if (methodNameToken != null) {
          index.addMethodCall(
            documentContext.getUri(),
            mdoRef,
            ModuleType.CommonModule,
            methodNameToken.getText(),
            Ranges.create(methodNameToken)
          );
        }
      }
    }
  }
}
