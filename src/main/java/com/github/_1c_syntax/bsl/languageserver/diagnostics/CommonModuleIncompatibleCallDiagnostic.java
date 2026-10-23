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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.PreprocessorContexts.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Вызов общего модуля там, где модуль недоступен, и вызов метода своего общего модуля там, где метод не объявлен.
 * <br/>
 * Общий модуль виден на сервере, если у него установлен флажок «Сервер», и на клиенте, если установлен флажок
 * «Клиент (управляемое приложение)» или «Вызов сервера»; вызов невидимого модуля не компилируется. Метод общего
 * модуля, объявленный внутри инструкции препроцессора {@code #Если}, есть только в тех контекстах, где условие
 * истинно; вызов такого метода из того же модуля проверяется по его тексту. Контексты вызова берутся из директивы
 * вызывающего метода (модули форм и команд), флажков общего модуля или вида модуля (модули объектов и менеджеров -
 * сервер) и окружающих {@code #Если} (см. {@link PreprocessorContexts}).
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.FormModule,
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.ObjectModule,
    ModuleType.ManagerModule
  },
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  }
)
@RequiredArgsConstructor
public class CommonModuleIncompatibleCallDiagnostic extends AbstractListenerDiagnostic {

  private static final Set<CompilerDirectiveKind> FORM_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT
  );

  private static final Set<CompilerDirectiveKind> COMMAND_MODULE_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT,
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_CLIENT_AT_SERVER
  );

  private final ReferenceIndex referenceIndex;

  private final PreprocessorContexts preprocessor = new PreprocessorContexts();

  /**
   * Контексты методов модуля без директивы; пусто - модуль не проверяется.
   */
  private Set<ExecutionContext> moduleContexts = EnumSet.noneOf(ExecutionContext.class);

  /**
   * Контексты, в которых компилируется текущий метод; null - вызовы текущего метода не проверяются.
   */
  private @Nullable Set<ExecutionContext> methodContexts;

  /**
   * Контексты компиляции вызовов этого модуля по началу имени вызываемого метода.
   */
  private final Map<Position, Set<ExecutionContext>> callContexts = new HashMap<>();

  /**
   * Контексты, в которых объявлены методы этого модуля, по имени метода: объединение по всем объявлениям - метод
   * может быть объявлен в разных ветках `#Если`. Пусто - не вычисляется.
   */
  private final Map<String, Optional<Set<ExecutionContext>>> declarationContexts =
    new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * Вызовы без имени модуля, которых нет среди методов этого модуля, по началу имени: кандидаты в методы
   * глобальных общих модулей. Индекс ссылок такие вызовы с глобальными модулями не связывает.
   */
  private final Map<Position, String> globalCalls = new HashMap<>();

  @Override
  public void enterFile(BSLParser.FileContext ctx) {
    preprocessor.clear();
    callContexts.clear();
    declarationContexts.clear();
    globalCalls.clear();
    methodContexts = null;
    moduleContexts = moduleContexts();
  }

  @Override
  public void exitFile(BSLParser.FileContext ctx) {
    referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method)
      .forEach(this::checkReference);
    globalCalls.forEach(this::checkGlobalCall);
  }

  @Override
  public void enterSub(BSLParser.SubContext ctx) {
    methodContexts = documentContext.getSymbolTree().getMethodSymbol(ctx)
      .map(this::methodContexts)
      .orElse(null);
  }

  @Override
  public void exitSub(BSLParser.SubContext ctx) {
    methodContexts = null;
  }

  @Override
  public void exitProcDeclaration(BSLParser.ProcDeclarationContext ctx) {
    rememberDeclarationContexts(ctx.subName());
  }

  @Override
  public void exitFuncDeclaration(BSLParser.FuncDeclarationContext ctx) {
    rememberDeclarationContexts(ctx.subName());
  }

  @Override
  public void enterPreproc_if(BSLParser.Preproc_ifContext ctx) {
    preprocessor.enterIf(ctx);
  }

  @Override
  public void enterPreproc_elsif(BSLParser.Preproc_elsifContext ctx) {
    preprocessor.enterElsif(ctx);
  }

  @Override
  public void enterPreproc_else(BSLParser.Preproc_elseContext ctx) {
    preprocessor.enterElse();
  }

  @Override
  public void enterPreproc_endif(BSLParser.Preproc_endifContext ctx) {
    preprocessor.enterEndif();
  }

  @Override
  public void enterGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    rememberCallContexts(ctx.methodName());
    var methodName = ctx.methodName().getText();
    if (documentContext.getSymbolTree().getMethodSymbol(methodName).isEmpty()) {
      globalCalls.put(startOf(ctx.methodName()), methodName);
    }
  }

  @Override
  public void enterMethodCall(BSLParser.MethodCallContext ctx) {
    rememberCallContexts(ctx.methodName());
  }

  private void rememberCallContexts(BSLParser.@Nullable MethodNameContext methodName) {
    var method = methodContexts;
    var active = preprocessor.active();
    if (methodName == null || method == null || active == null) {
      return;
    }
    var contexts = EnumSet.copyOf(method);
    contexts.retainAll(active);
    if (!contexts.isEmpty()) {
      callContexts.put(startOf(methodName), contexts);
    }
  }

  private void rememberDeclarationContexts(BSLParser.@Nullable SubNameContext subName) {
    if (subName == null) {
      return;
    }
    var active = preprocessor.active();
    declarationContexts.merge(subName.getText(), Optional.ofNullable(active), (Optional<Set<ExecutionContext>> known,
      Optional<Set<ExecutionContext>> added) -> {
      if (known.isEmpty() || added.isEmpty()) {
        return Optional.empty();
      }
      var union = EnumSet.copyOf(known.get());
      union.addAll(added.get());
      return Optional.of(union);
    });
  }

  private void checkReference(Reference reference) {
    var contexts = callContexts.get(reference.selectionRange().getStart());
    if (contexts == null || !(reference.symbol() instanceof MethodSymbol method)) {
      return;
    }
    var owner = method.getOwner();
    if (owner.getModuleType() != ModuleType.CommonModule) {
      return;
    }
    var commonModule = owner.getMdObject()
      .filter(CommonModule.class::isInstance)
      .map(CommonModule.class::cast);
    if (commonModule.isEmpty()) {
      return;
    }

    if (owner.getUri().equals(documentContext.getUri())) {
      // Текст своего модуля под рукой: метод под #Если есть только там, где условие истинно.
      var declared = declarationContexts.getOrDefault(method.getName(), Optional.empty());
      if (declared.isPresent() && !declared.get().containsAll(contexts)) {
        diagnosticStorage.addDiagnostic(reference.selectionRange(),
          info.getMessage(method.getName(), commonModule.get().getName()));
      }
      return;
    }

    if (contexts.stream().anyMatch(context -> !isModuleVisible(commonModule.get(), context))) {
      diagnosticStorage.addDiagnostic(reference.selectionRange(),
        info.getResourceString("moduleUnavailableMessage", commonModule.get().getName()));
    }
  }

  private void checkGlobalCall(Position position, String methodName) {
    var contexts = callContexts.get(position);
    if (contexts == null) {
      return;
    }
    var serverContext = documentContext.getServerContext();
    serverContext.getConfiguration().getCommonModules().stream()
      .filter(CommonModule::isGlobal)
      .filter(module -> serverContext.getDocument(module.getMdoRef(), ModuleType.CommonModule)
        .flatMap(document -> document.getSymbolTree().getMethodSymbol(methodName))
        .filter(MethodSymbol::isExport)
        .isPresent())
      .findFirst()
      .filter(module -> contexts.stream().anyMatch(context -> !isModuleVisible(module, context)))
      .ifPresent(module -> diagnosticStorage.addDiagnostic(
        new Range(position, new Position(position.getLine(), position.getCharacter() + methodName.length())),
        info.getResourceString("moduleUnavailableMessage", module.getName())));
  }

  /**
   * Виден ли общий модуль в контексте компиляции: на сервере - с флажком «Сервер», на клиенте - с флажком
   * «Клиент (управляемое приложение)» или «Вызов сервера».
   */
  private static boolean isModuleVisible(CommonModule module, ExecutionContext context) {
    if (context == ExecutionContext.SERVER) {
      return module.isServer();
    }
    return module.isClientManagedApplication() || module.isServerCall();
  }

  private Set<ExecutionContext> moduleContexts() {
    var moduleType = documentContext.getModuleType();
    if (moduleType == ModuleType.ObjectModule || moduleType == ModuleType.ManagerModule) {
      return PreprocessorContexts.SERVER_CONTEXTS;
    }
    if (moduleType == ModuleType.CommonModule) {
      var contexts = EnumSet.noneOf(ExecutionContext.class);
      documentContext.getMdObject()
        .filter(CommonModule.class::isInstance)
        .map(CommonModule.class::cast)
        .ifPresent((CommonModule module) -> {
          if (module.isServer()) {
            contexts.addAll(PreprocessorContexts.SERVER_CONTEXTS);
          }
          if (module.isClientManagedApplication()) {
            contexts.addAll(PreprocessorContexts.CLIENT_CONTEXTS);
          }
        });
      return contexts;
    }
    return EnumSet.noneOf(ExecutionContext.class);
  }

  /**
   * Контексты компиляции метода: у модулей форм и команд - по директиве, у остальных - контексты модуля для
   * метода без директивы. null - метод не проверяется.
   */
  private @Nullable Set<ExecutionContext> methodContexts(MethodSymbol method) {
    var moduleType = documentContext.getModuleType();
    var directive = method.getCompilerDirectiveKind();
    if (moduleType == ModuleType.FormModule || moduleType == ModuleType.CommandModule) {
      if (isOrdinaryForm()) {
        return null;
      }
      var allowed = moduleType == ModuleType.FormModule ? FORM_MODULE_DIRECTIVES : COMMAND_MODULE_DIRECTIVES;
      var kind = directive.orElse(CompilerDirectiveKind.AT_SERVER);
      if (!allowed.contains(kind)) {
        return null;
      }
      return switch (kind) {
        case AT_CLIENT -> PreprocessorContexts.CLIENT_CONTEXTS;
        case AT_SERVER, AT_SERVER_NO_CONTEXT -> PreprocessorContexts.SERVER_CONTEXTS;
        case AT_CLIENT_AT_SERVER, AT_CLIENT_AT_SERVER_NO_CONTEXT -> PreprocessorContexts.ALL_CONTEXTS;
      };
    }
    if (directive.isPresent() || moduleContexts.isEmpty()) {
      return null;
    }
    return moduleContexts;
  }

  private boolean isOrdinaryForm() {
    return documentContext.getModuleType() == ModuleType.FormModule
      && documentContext.getMdObject()
      .filter(Form.class::isInstance)
      .map(Form.class::cast)
      .map(form -> form.getFormType() != FormType.MANAGED)
      .orElse(false);
  }

  private static Position startOf(ParserRuleContext ctx) {
    var token = ctx.getStart();
    return new Position(token.getLine() - 1, token.getCharPositionInLine());
  }
}
