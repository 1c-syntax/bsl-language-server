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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.registry.BslContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNUSED
  },
  modules = {
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.FormModule,
    ModuleType.ManagerModule,
    ModuleType.ObjectModule,
    ModuleType.RecordSetModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule,
    ModuleType.UNKNOWN
  }
)
@RequiredArgsConstructor
public class UnusedLocalVariableDiagnostic extends AbstractDiagnostic {
  private static final Map<ModuleType, Pattern> EXPORT_PATHS = Stream.of(
      ModuleType.FormModule, ModuleType.ObjectModule, ModuleType.RecordSetModule)
    .collect(Collectors.toUnmodifiableMap(Function.identity(), UnusedLocalVariableDiagnostic::exportPath));
  private final ReferenceIndex referenceIndex;
  private final TypeService typeService;
  private final BslContextHolder platform;
  private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.LOCAL,
    VariableKind.DYNAMIC
  );

  @Override
  public void check() {
    var inScope = checkedScope();
    var tree = documentContext.getSymbolTree();
    var evalResults = evalResultVariables(tree);
    tree.getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      .filter(variable -> !variable.isExport())
      .filter(variable -> !evalResults.contains(variable))
      .filter(variable -> referenceIndex.getReferencesTo(variable).stream()
        .noneMatch(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE)
      )
      .filter(inScope)
      .forEach(variable -> diagnosticStorage.addDiagnostic(
        variable.getSelectionRange(), info.getMessage(variable.getName()))
      );
  }

  private Set<VariableSymbol> evalResultVariables(SymbolTree tree) {
    // Вычислить требуется вызвать в выражении, даже если его результат не читают.
    var results = new HashSet<VariableSymbol>();
    for (var assignment : Trees.<BSLParser.AssignmentContext>findAllRuleNodes(
      documentContext.getAst(), BSLParser.RULE_assignment)) {
      var target = assignment.lValue();
      if (target == null || target.IDENTIFIER() == null || target.acceptor() != null) {
        continue;
      }
      var call = directCall(assignment.expression());
      if (call == null || call.methodName() == null) {
        continue;
      }
      var name = call.methodName().getText();
      if ((Keywords.EVAL.getRu().equalsIgnoreCase(name) || Keywords.EVAL.getEn().equalsIgnoreCase(name))
        && tree.getMethodSymbol(name).isEmpty()) {
        tree.getVariableSymbolInScope(assignment, target.IDENTIFIER().getText()).ifPresent(results::add);
      }
    }
    return results;
  }

  private static BSLParser.@Nullable GlobalMethodCallContext directCall(
    BSLParser.@Nullable ExpressionContext expression) {
    while (expression != null && expression.member().size() == 1) {
      var member = expression.member(0);
      if (member.unaryModifier() != null || !member.modifier().isEmpty()) {
        return null;
      }
      var identifier = member.complexIdentifier();
      if (identifier != null) {
        return identifier.modifier().isEmpty() ? identifier.globalMethodCall() : null;
      }
      if (member.globalMethodCall() != null) {
        return member.globalMethodCall();
      }
      expression = member.expression();
    }
    return null;
  }

  private Predicate<VariableSymbol> checkedScope() {
    var type = moduleType();
    if (!EXPORT_PATHS.containsKey(type)) {
      return variable -> true;
    }
    // Для простого имени контекстный метод требует и реквизитов владельца, и свойств платформы.
    var owner = documentContext.getMdObject();
    boolean knownContext = owner.isPresent()
      && (type != ModuleType.FormModule || owner.get() instanceof Form form
        && form.getFormType() != FormType.UNKNOWN && !form.getData().isEmpty())
      && platform.get().isPresent() && typeService.selfTypeRef(documentContext).isPresent();
    var tree = documentContext.getSymbolTree();
    var counters = numericLoopCounters(tree);
    var loops = tree.getBasis().loopVariables();
    return variable -> variable.getScope() instanceof MethodSymbol method && !counters.contains(variable)
      && (variable.getKind() == VariableKind.LOCAL || knownContext || loops.contains(variable)
        || type == ModuleType.FormModule && method.isWithoutFormContext());
  }

  private Set<VariableSymbol> numericLoopCounters(SymbolTree tree) {
    var counters = new HashSet<VariableSymbol>();
    // Цикл читает счётчик, даже если его объявили до заголовка Для.
    for (var loop : Trees.<BSLParser.ForStatementContext>findAllRuleNodes(
      documentContext.getAst(), BSLParser.RULE_forStatement)) {
      var identifier = loop.IDENTIFIER();
      if (identifier != null) {
        tree.getVariableSymbolInScope(loop, identifier.getText()).ifPresent(counters::add);
      }
    }
    return counters;
  }

  private ModuleType moduleType() {
    var type = documentContext.getModuleType();
    var path = documentContext.getUri().getPath();
    if (type != ModuleType.UNKNOWN || path == null) {
      return type;
    }
    // Без выгрузки mdclasses возвращает UNKNOWN. Ограничиваем проверку в известных
    // путях Designer/EDT, сохраняя прежнюю область для самостоятельных модулей.
    return EXPORT_PATHS.entrySet().stream()
      .filter(entry -> entry.getValue().matcher(path).find())
      .map(Map.Entry::getKey)
      .findFirst().orElse(type);
  }

  private static Pattern exportPath(ModuleType module) {
    var groups = Arrays.stream(MDOType.values())
      .filter(owner -> ModuleType.byMDOType(owner).contains(module))
      .map(owner -> Pattern.quote(owner.groupName()))
      .collect(Collectors.joining("|"));
    var ext = module == ModuleType.FormModule ? "(?:Ext/Form/)?" : "(?:Ext/)?";
    return Pattern.compile("/(?:" + groups + ")/[^/]+/" + ext + Pattern.quote(module.getFileName()) + "$",
      Pattern.CASE_INSENSITIVE);
  }
}
