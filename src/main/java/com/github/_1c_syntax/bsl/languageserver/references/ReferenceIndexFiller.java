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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
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

import java.net.URI;
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
  private final LanguageServerConfiguration configuration;
  private final OScriptLibraryIndex oScriptLibraryIndex;

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
    private final ModuleReference.ParsedAccessors parsedAccessors =
      ModuleReference.parseAccessors(configuration.getReferencesOptions().getCommonModuleAccessors());
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

      // Метод, вызванный у результата getter-а общего модуля: ОбщегоНазначения.ОбщийМодуль("Имя").Метод()
      registerCommonModuleMethodOnGetter(ctx.IDENTIFIER(), null, ctx.modifier(), ctx.accessCall());

      var mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        tryRegisterLibraryModuleCall(ctx.IDENTIFIER(), Methods.getMethodName(ctx));
        return super.visitCallStatement(ctx);
      }

      // Добавляем ссылку на модуль по позиции идентификатора (только для общих модулей)
      addModuleReferenceForCommonModuleIdentifier(ctx.IDENTIFIER());

      Methods.getMethodName(ctx).ifPresent(methodName -> checkCall(mdoRef, methodName));

      return super.visitCallStatement(ctx);
    }

    @Override
    public ParserRuleContext visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
      // Метод, вызванный у результата getter-а общего модуля: ОбщегоНазначения.ОбщийМодуль("Имя").Метод()
      registerCommonModuleMethodOnGetter(ctx.IDENTIFIER(), ctx.globalMethodCall(), ctx.modifier(), null);

      var mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
      if (mdoRef.isEmpty()) {
        tryRegisterLibraryModuleCall(ctx.IDENTIFIER(), Methods.getMethodName(ctx));
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

      // Вызов в пределах того же документа: символ-определение уже доступен,
      // поэтому берём его точный вид. Это важно для отдельного .os-файла вне
      // библиотеки (ModuleType.UNKNOWN), методы которого по семантике OneScript —
      // самостоятельные функции (SymbolKind.Function), что нельзя вывести только из moduleType.
      documentContext.getSymbolTree().getMethodSymbol(methodNameText)
        .ifPresent(methodSymbol -> index.addMethodCall(
          documentContext.getUri(),
          mdoRef,
          moduleType,
          methodNameText,
          Ranges.create(methodName),
          methodSymbol.getSymbolKind()
        ));

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

      tryRegisterLibraryClassReference(ctx);

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
      final var mdoConfiguration = documentContext.getServerContext().getConfiguration();
      var modules = mdoConfiguration.mdoModuleTypes(mdoRef);
      for (ModuleType moduleType : modules.keySet()) {
        if (!DEFAULT_MODULE_TYPES.contains(moduleType)
          || (moduleType == ModuleType.CommonModule && commonModuleMdoRefFromSubParams.contains(mdoRef))) {
          continue;
        }
        addMethodCall(mdoRef, moduleType, methodNameText, Ranges.create(methodName));
      }
    }

    /**
     * Если в выражении {@code Новый MyClass(...)} имя типа соответствует
     * зарегистрированному OneScript library-классу, регистрирует ссылку:
     * <ul>
     *   <li>на метод-конструктор класса ({@code ПриСозданииОбъекта} /
     *   {@code OnObjectCreate}), если он явно объявлен — тогда go-to-def
     *   ведёт сразу на тело конструктора, а hover показывает его сигнатуру;</li>
     *   <li>на сам .os-файл класса (модуль), если конструктора в исходнике нет —
     *   тогда go-to-def ведёт в файл целиком.</li>
     * </ul>
     */
    private void tryRegisterLibraryClassReference(BSLParser.NewExpressionContext ctx) {
      var typeName = ctx.typeName();
      if (typeName == null || typeName.IDENTIFIER() == null) {
        return;
      }
      var name = typeName.IDENTIFIER().getText();
      var libUri = oScriptLibraryIndex.findClassUri(name);
      if (libUri.isEmpty()) {
        return;
      }
      var libMdoRef = libUri.get().toString();
      var moduleType = actualLibraryModuleType(libUri.get(), ModuleType.OScriptClass);
      var range = Ranges.create(typeName.IDENTIFIER());

      var ctor = libraryClassConstructor(libUri.get());
      if (ctor.isPresent()) {
        index.addMethodCall(
          documentContext.getUri(),
          libMdoRef,
          moduleType,
          ctor.get().getName(),
          range
        );
      } else {
        index.addModuleReference(
          documentContext.getUri(),
          libMdoRef,
          moduleType,
          range
        );
      }
    }

    private Optional<ConstructorSymbol> libraryClassConstructor(URI libUri) {
      return Optional.ofNullable(documentContext.getServerContext().getDocument(libUri))
        .map(DocumentContext::getSymbolTree)
        .flatMap(SymbolTree::getConstructor);
    }

    /**
     * Если идентификатор соответствует имени зарегистрированного OneScript
     * library-модуля, регистрирует:
     * <ul>
     *   <li>ссылку на сам идентификатор модуля (go-to-definition на имени модуля),</li>
     *   <li>если в выражении присутствует вызов метода — ссылку на метод по его позиции.</li>
     * </ul>
     */
    private void tryRegisterLibraryModuleCall(@Nullable TerminalNode identifier, Optional<Token> methodName) {
      if (identifier == null) {
        return;
      }
      var libUri = oScriptLibraryIndex.findModuleUri(identifier.getText());
      if (libUri.isEmpty()) {
        return;
      }
      var libMdoRef = libUri.get().toString();
      var moduleType = actualLibraryModuleType(libUri.get(), ModuleType.OScriptModule);

      // Ссылка на сам identifier модуля — нужна для go-to-definition без точки.
      index.addModuleReference(
        documentContext.getUri(),
        libMdoRef,
        moduleType,
        Ranges.create(identifier)
      );

      if (methodName.isPresent()) {
        var methodNameToken = methodName.get();
        addMethodCall(libMdoRef, moduleType, Strings.trimQuotes(methodNameToken.getText()),
          Ranges.create(methodNameToken));
      }
    }

    /**
     * Возвращает фактический {@link ModuleType} документа библиотечного .os-файла.
     * Один .os может быть зарегистрирован одновременно и как класс, и как модуль
     * (см. {@link OScriptLibraryIndex}); чтобы ссылка корректно резолвилась через
     * {@code ServerContext.getDocument(mdoRef, moduleType)}, используем тип
     * фактически загруженного {@link DocumentContext}, а не «теоретический»
     * тип из роли регистрации.
     */
    private ModuleType actualLibraryModuleType(java.net.URI libUri, ModuleType fallback) {
      var dc = documentContext.getServerContext().getDocument(libUri);
      return dc != null ? dc.getModuleType() : fallback;
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
        .findCommonModule(identifierText)
        .ifPresent(commonModule ->
          index.addModuleReference(
            documentContext.getUri(),
            commonModule.getMdoReference().getMdoRef(),
            ModuleType.CommonModule,
            Ranges.create(identifier)
          )
        );
    }

    private void addMethodCall(String mdoRef, ModuleType moduleType, String methodName, Range range) {
      index.addMethodCall(documentContext.getUri(), mdoRef, moduleType, methodName, range);
    }

    /**
     * Регистрирует вызов метода у результата getter-а общего модуля
     * ({@code ОбщегоНазначения.ОбщийМодуль("Имя").Метод(...)} или {@code ОбщийМодуль("Имя").Метод(...)}).
     * <p>
     * Сам getter-метод ({@code ОбщийМодуль}) регистрируется отдельно (через {@code checkCall}),
     * а здесь добавляется ссылка на метод возвращённого общего модуля, чтобы он корректно
     * разрешался и проверялся (см. #3974).
     */
    private void registerCommonModuleMethodOnGetter(
      @Nullable TerminalNode baseIdentifier,
      BSLParser.@Nullable GlobalMethodCallContext baseGlobalCall,
      List<? extends BSLParser.ModifierContext> modifiers,
      BSLParser.@Nullable AccessCallContext trailingCall
    ) {
      ModuleReference.extractMethodCallOnGetterModule(
          baseIdentifier, baseGlobalCall, modifiers, trailingCall, parsedAccessors)
        .ifPresent(call -> documentContext.getServerContext()
          .findCommonModule(call.moduleName())
          .ifPresent(commonModule -> addMethodCall(
            commonModule.getMdoReference().getMdoRef(),
            ModuleType.CommonModule,
            call.methodNameToken().getText(),
            Ranges.create(call.methodNameToken())
          )));
    }

    private void addCallbackMethodCall(BSLParser.CallParamContext methodName, String mdoRef) {
      // todo: move this out of method 
      if (mdoRef.isEmpty()) {
        return;
      }
      Methods.getMethodName(methodName).ifPresent((Token methodNameToken) -> {
        if (!mdoRef.equals(documentContext.getMdoRef())) {
          // Обработчик в другом модуле: ссылку под корректным типом модуля
          // и видом символа вызываемого модуля регистрирует checkCall.
          checkCall(mdoRef, methodNameToken);
          return;
        }

        // Обработчик в текущем документе: символ-определение доступен,
        // берём его точный вид, чтобы вид вызова совпал с видом определения
        // (важно для отдельного .os-файла с ModuleType.UNKNOWN).
        var handlerName = Strings.trimQuotes(methodName.getText());
        var symbolKind = documentContext.getSymbolTree().getMethodSymbol(handlerName)
          .map(SourceDefinedSymbol::getSymbolKind)
          .orElse(SymbolKind.Method);
        index.addMethodCall(
          documentContext.getUri(),
          mdoRef,
          documentContext.getModuleType(),
          handlerName,
          Ranges.create(methodName),
          symbolKind
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
      final var serverContext = documentContext.getServerContext();
      return paramList.param().stream()
        .map(BSLParser.ParamContext::IDENTIFIER)
        .filter(Objects::nonNull)
        .map(ParseTree::getText)
        .map(serverContext::findCommonModule)
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
    private @Nullable SourceDefinedSymbol currentScope;
    private final Map<String, String> variableToCommonModuleMap = new HashMap<>();
    /** variable name (lowercase) → URI .os-файла library-класса, на экземпляр которого переменная инициализирована. */
    private final Map<String, String> variableToLibraryClassUriMap = new HashMap<>();

    private VariableSymbolReferenceIndexFinder(DocumentContext documentContext) {
      this.documentContext = documentContext;
      this.parsedAccessors = ModuleReference.parseAccessors(
        configuration.getReferencesOptions().getCommonModuleAccessors()
      );
    }

    @Override
    public ParserRuleContext visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {
      findVariableSymbol(ctx.var_name().getText()).ifPresent(s -> {
        if (notVariableInitialization(ctx, s)) {

          addVariableUsage(
            s.getRootParent(MethodSymbol.class),
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
      variableToCommonModuleMap.keySet().removeIf((String variableKey) -> {
        // Ищем переменную на уровне модуля
        var moduleVariable = moduleSymbolTree.getVariableSymbol(variableKey, module);
        // Если переменной нет на уровне модуля - это локальная переменная, удаляем mapping
        return moduleVariable.isEmpty();
      });
      variableToLibraryClassUriMap.keySet().removeIf((String variableKey) ->
        moduleSymbolTree.getVariableSymbol(variableKey, module).isEmpty()
      );
    }

    @Override
    public ParserRuleContext visitAssignment(BSLParser.AssignmentContext ctx) {
      // Detect pattern: Variable = ОбщегоНазначения.ОбщийМодуль("ModuleName") or Variable = ОбщийМодуль("ModuleName")
      // Здесь мы только отслеживаем, что переменная теперь содержит ссылку на общий модуль,
      // чтобы последующие вызовы методов через эту переменную могли быть разрезолвлены.
      // Ссылки на модуль-аксессор (например, ОбщегоНазначения) добавляются в visitComplexIdentifier,
      // когда он используется как самостоятельный идентификатор, а здесь мы лишь ведем mapping
      // переменная → общий модуль для дальнейшего разрешения вызовов методов.
      var lValue = ctx.lValue();
      var expression = ctx.expression();

      if (lValue != null && lValue.IDENTIFIER() != null && expression != null) {
        var variableKey = lValue.IDENTIFIER().getText().toLowerCase(Locale.ENGLISH);
        var libClassUri = extractLibraryClassUriFromExpression(expression);
        if (libClassUri != null) {
          variableToLibraryClassUriMap.put(variableKey, libClassUri);
          variableToCommonModuleMap.remove(variableKey);
        } else if (ModuleReference.isCommonModuleExpression(expression, parsedAccessors)) {
          var commonModuleOpt = ModuleReference.extractCommonModuleName(expression, parsedAccessors)
            .flatMap(moduleName -> documentContext.getServerContext()
              .findCommonModule(moduleName));
          if (commonModuleOpt.isPresent()) {
            var mdoRef = commonModuleOpt.get().getMdoReference().getMdoRef();
            variableToCommonModuleMap.put(variableKey, mdoRef);
          } else {
            // Модуль не найден - удаляем старый mapping если был
            variableToCommonModuleMap.remove(variableKey);
          }
          variableToLibraryClassUriMap.remove(variableKey);
        } else {
          // Переменная переназначена на что-то другое - очищаем mapping
          variableToCommonModuleMap.remove(variableKey);
          variableToLibraryClassUriMap.remove(variableKey);
        }
      }

      return super.visitAssignment(ctx);
    }

    /**
     * Если выражение представляет собой {@code Новый MyLibClass(...)}, где
     * {@code MyLibClass} зарегистрирован как OneScript library-класс, возвращает
     * URI .os-файла этого класса в виде строки.
     */
    private @Nullable String extractLibraryClassUriFromExpression(BSLParser.ExpressionContext expression) {
      var members = expression.member();
      if (members == null || members.isEmpty()) {
        return null;
      }
      var complexId = members.get(0).complexIdentifier();
      if (complexId == null) {
        return null;
      }
      var newExpression = complexId.newExpression();
      if (newExpression == null) {
        return null;
      }
      var typeName = newExpression.typeName();
      if (typeName == null || typeName.IDENTIFIER() == null) {
        return null;
      }
      return oScriptLibraryIndex.findClassUri(typeName.IDENTIFIER().getText())
        .map(java.net.URI::toString)
        .orElse(null);
    }

    @Override
    public ParserRuleContext visitLValue(BSLParser.LValueContext ctx) {
      if (ctx.IDENTIFIER() == null) {
        return super.visitLValue(ctx);
      }

      findVariableSymbol(ctx.IDENTIFIER().getText()).ifPresent((VariableSymbol s) -> {
        if (notVariableInitialization(ctx, s)) {
          addVariableUsage(
            s.getRootParent(MethodSymbol.class),
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
      var variableKey = variableName.toLowerCase(Locale.ENGLISH);

      // Check if variable references a common module
      var commonModuleMdoRef = variableToCommonModuleMap.get(variableKey);

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

      var libClassUri = variableToLibraryClassUriMap.get(variableKey);
      if (libClassUri != null) {
        if (!ctx.modifier().isEmpty()) {
          processLibraryClassMethodCalls(ctx.modifier(), libClassUri);
        }
        if (ctx.accessCall() != null) {
          processLibraryClassAccessCall(ctx.accessCall(), libClassUri);
        }
      }

      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(
            s.getRootParent(MethodSymbol.class), variableName, Ranges.create(ctx.IDENTIFIER()), true
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
      var variableKey = variableName.toLowerCase(Locale.ENGLISH);

      // Check if we are inside a callStatement - if so, skip processing here to avoid duplication
      var parentCallStatement = Trees.getRootParent(ctx, BSLParser.RULE_callStatement);
      var isInsideCallStatement = false;
      if (parentCallStatement instanceof BSLParser.CallStatementContext callStmt) {
        isInsideCallStatement = callStmt.IDENTIFIER() != null
          && callStmt.IDENTIFIER().getText().equalsIgnoreCase(variableName);
      }

      // Check if variable references a common module
      var commonModuleMdoRef = variableToCommonModuleMap.get(variableKey);
      if (commonModuleMdoRef != null && !ctx.modifier().isEmpty() && !isInsideCallStatement) {
        // Process method calls on the common module variable
        processCommonModuleMethodCalls(ctx.modifier(), commonModuleMdoRef);
      }

      var libClassUri = variableToLibraryClassUriMap.get(variableKey);
      if (libClassUri != null && !ctx.modifier().isEmpty() && !isInsideCallStatement) {
        processLibraryClassMethodCalls(ctx.modifier(), libClassUri);
      }

      findVariableSymbol(variableName)
        .ifPresent(s -> addVariableUsage(
            s.getRootParent(MethodSymbol.class), variableName, Ranges.create(ctx.IDENTIFIER()), true
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
            s.getRootParent(MethodSymbol.class),
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
            s.getRootParent(MethodSymbol.class),
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

    private void processLibraryClassMethodCalls(List<? extends BSLParser.ModifierContext> modifiers, String libClassUri) {
      for (var modifier : modifiers) {
        var accessCall = modifier.accessCall();
        if (accessCall != null) {
          processLibraryClassAccessCall(accessCall, libClassUri);
        }
      }
    }

    private void processLibraryClassAccessCall(BSLParser.AccessCallContext accessCall, String libClassUri) {
      var methodCall = accessCall.methodCall();
      if (methodCall != null && methodCall.methodName() != null) {
        var methodNameToken = methodCall.methodName().IDENTIFIER();
        if (methodNameToken != null) {
          index.addMethodCall(
            documentContext.getUri(),
            libClassUri,
            ModuleType.OScriptClass,
            methodNameToken.getText(),
            Ranges.create(methodNameToken)
          );
        }
      }
    }
  }
}
