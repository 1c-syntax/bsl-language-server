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

import com.github._1c_syntax.bsl.languageserver.cfg.BasicBlockVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.ForLoopVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ForeachLoopVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.TryExceptVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/** Backward liveness of variable bindings in one method or module body. */
final class LostVariableFlow {

  private final DocumentContext document;
  private final BSLParser.CodeBlockContext body;
  private final ControlFlowGraph graph;
  private final Map<String, Optional<VariableSymbol>> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final Set<String> byValue = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
  private final Set<VariableSymbol> external = new HashSet<>();
  private final Set<VariableSymbol> read = new HashSet<>();
  private final Map<VariableSymbol, Integer> writes = new HashMap<>();
  private final Map<CfgVertex, List<Operation>> operations = new HashMap<>();
  private final Map<CfgVertex, Set<VariableSymbol>> liveIn = new HashMap<>();
  private final Map<BSLParser.TryCodeBlockContext, CfgVertex> handlers = new HashMap<>();
  private final Map<CfgVertex, Set<CfgVertex>> dependents = new HashMap<>();

  LostVariableFlow(DocumentContext document, BSLParser.CodeBlockContext body, ControlFlowGraph graph) {
    this.document = document;
    this.body = body;
    this.graph = graph;
    var sub = Trees.<BSLParser.SubContext>getAncestorByRuleIndex(body, BSLParser.RULE_sub);
    if (sub != null) {
      document.getSymbolTree().getMethodSymbol(sub).ifPresent(method -> method.getParameters().stream()
        .filter(parameter -> parameter.isByValue()).forEach(parameter -> byValue.add(parameter.getName())));
    }
  }

  void check(BiConsumer<TerminalNode, String> report) {
    var vertices = new ArrayList<CfgVertex>();
    new DepthFirstIterator<>(graph, graph.getEntryPoint()).forEachRemaining(vertices::add);
    for (var vertex : vertices) {
      if (vertex instanceof TryExceptVertex branch) {
        graph.outgoingEdgesOf(vertex).stream().filter(edge -> edge.getType() == CfgEdgeType.FALSE_BRANCH)
          .findFirst().ifPresent(edge -> handlers.put(branch.getTryStatement().tryCodeBlock(), graph.getEdgeTarget(edge)));
      }
    }
    for (var vertex : vertices) {
      var steps = collect(vertex, report);
      operations.put(vertex, steps);
      for (var edge : graph.outgoingEdgesOf(vertex)) {
        dependents.computeIfAbsent(graph.getEdgeTarget(edge), key -> new HashSet<>()).add(vertex);
      }
      for (var step : steps) {
        if (step.exception != null) {
          dependents.computeIfAbsent(step.exception, key -> new HashSet<>()).add(vertex);
        }
      }
    }
    if (writes.isEmpty()) {
      return;
    }

    for (var steps : operations.values()) {
      for (var step : steps) {
        // By-reference parameters can alias each other or a module variable.
        step.observesExternal |= step.reads.stream().anyMatch(external::contains);
        step.reads.retainAll(writes.keySet());
      }
    }
    external.retainAll(writes.keySet());
    var pending = new ArrayDeque<>(vertices.reversed());
    var queued = new HashSet<>(vertices);
    while (!pending.isEmpty()) {
      var vertex = pending.removeFirst();
      queued.remove(vertex);
      var live = transfer(vertex, null);
      if (!live.equals(liveIn.getOrDefault(vertex, Set.of()))) {
        liveIn.put(vertex, live);
        for (var predecessor : dependents.getOrDefault(vertex, Set.of())) {
          if (queued.add(predecessor)) {
            pending.addLast(predecessor);
          }
        }
      }
    }
    vertices.forEach(vertex -> transfer(vertex, report));
  }

  private Set<VariableSymbol> transfer(CfgVertex vertex, @Nullable BiConsumer<TerminalNode, String> report) {
    var live = new HashSet<VariableSymbol>();
    if (vertex == graph.getExitPoint()) {
      live.addAll(external);
    }
    for (var edge : graph.outgoingEdgesOf(vertex)) {
      var successor = liveIn.getOrDefault(graph.getEdgeTarget(edge), Set.of());
      if (vertex instanceof ForeachLoopVertex loop && edge.getType() == CfgEdgeType.TRUE_BRANCH) {
        // Entering the body assigns the next item. The loop exit preserves the current value.
        var beforeIteration = new HashSet<>(successor);
        resolve(loop.getLoopHeader().IDENTIFIER()).ifPresent(beforeIteration::remove);
        live.addAll(beforeIteration);
      } else {
        live.addAll(successor);
      }
    }
    for (var op : operations.get(vertex).reversed()) {
      if (op.variable != null && op.write != null) {
        var variable = op.variable;
        if (!live.remove(variable) && report != null && (writes.get(variable) > 1 || read.contains(variable))) {
          report.accept(op.write, "diagnosticMessage");
        }
      }
      live.addAll(op.reads);
      if (op.observesExternal) {
        live.addAll(external);
      }
      if (op.dynamic) {
        live.addAll(writes.keySet());
      }
      // An exception during evaluation transfers the value BEFORE the assignment to its handler.
      if (op.exception != null) {
        live.addAll(liveIn.getOrDefault(op.exception, Set.of()));
      }
    }
    return live;
  }

  private List<Operation> collect(CfgVertex vertex, BiConsumer<TerminalNode, String> report) {
    return switch (vertex) {
      case BasicBlockVertex block -> block.statements().stream().map(this::operation).toList();
      case ConditionalVertex condition -> List.of(operation(condition.getExpression()));
      case WhileLoopVertex loop -> List.of(operation(loop.getExpression()));
      case ForLoopVertex loop -> {
        var header = loop.getLoopHeader();
        checkIterator(header, header.IDENTIFIER(), null, report);
        var bounds = header.expression().stream().map(this::operation).toList();
        // A numeric loop implicitly reads its counter before incrementing it.
        if (!bounds.isEmpty()) {
          resolve(header.IDENTIFIER()).ifPresent(bounds.getFirst().reads::add);
        }
        yield bounds;
      }
      case ForeachLoopVertex loop -> {
        var header = loop.getLoopHeader();
        checkIterator(header, header.IDENTIFIER(), header.expression(), report);
        yield header.expression() == null ? List.of() : List.of(operation(header.expression()));
      }
      default -> List.of();
    };
  }

  private void checkIterator(ParserRuleContext header, TerminalNode iterator,
                             BSLParser.@Nullable ExpressionContext collection, BiConsumer<TerminalNode, String> report) {
    if (iterator == null) {
      return;
    }
    var symbol = resolve(iterator);
    if (symbol.isEmpty()) {
      return;
    }
    if (symbol.equals(collectionVariable(collection)) && hasPriorBinding(symbol.get(), iterator)) {
      report.accept(iterator, "collectionIterator");
      return;
    }
    for (var parent = header.getParent(); parent != null && parent != body; parent = parent.getParent()) {
      var outer = switch (parent) {
        case BSLParser.ForStatementContext loop -> loop.IDENTIFIER();
        case BSLParser.ForEachStatementContext loop -> loop.IDENTIFIER();
        default -> null;
      };
      if (outer != null && symbol.equals(resolve(outer))) {
        report.accept(iterator, "nestedIterator");
        return;
      }
    }
    if (symbol.get().getKind() == VariableKind.MODULE) {
      report.accept(iterator, "moduleIterator");
    }
  }

  private Optional<VariableSymbol> collectionVariable(BSLParser.@Nullable ExpressionContext expression) {
    while (expression != null && expression.getChildCount() == 1) {
      if (expression.getStart() == expression.getStop() && expression.getStart().getType() == BSLParser.IDENTIFIER) {
        return resolve(expression.getText());
      }
      var member = expression.member(0);
      if (member == null || member.LPAREN() == null || member.getChildCount() != 3) {
        break;
      }
      expression = member.expression();
    }
    return Optional.empty();
  }

  private static boolean hasPriorBinding(VariableSymbol symbol, TerminalNode iterator) {
    if (symbol.getKind() != VariableKind.DYNAMIC) {
      return true;
    }
    var token = iterator.getSymbol();
    return symbol.getVariableNameLine() < token.getLine() - 1
      || symbol.getVariableNameLine() == token.getLine() - 1
      && symbol.getVariableNameStartCharacter() < token.getCharPositionInLine();
  }

  private Operation operation(ParserRuleContext ctx) {
    var op = new Operation();
    if (ctx instanceof BSLParser.AssignmentContext assignment && assignment.lValue() != null
      && assignment.lValue().getChildCount() == 1) {
      var name = assignment.lValue().IDENTIFIER();
      resolve(name).filter(this::canCheckWrite).ifPresent(variable -> {
        op.write = name;
        op.variable = variable;
        writes.merge(variable, 1, Integer::sum);
      });
    }
    op.visit(ctx);
    read.addAll(op.reads);
    var protectedBlock = Trees.<BSLParser.TryCodeBlockContext>getAncestorByRuleIndex(ctx, BSLParser.RULE_tryCodeBlock);
    op.exception = handlers.get(protectedBlock);
    if (op.exception == null && (op.observesExternal || ctx instanceof BSLParser.AssignmentContext assignment
      && assignment.expression() != null && assignment.expression().getStart() != assignment.expression().getStop())) {
      op.exception = graph.getExitPoint();
    }
    return op;
  }

  private boolean canCheckWrite(VariableSymbol variable) {
    // The symbol tree excludes bare self properties when the owner metadata is available.
    return variable.getKind() != VariableKind.DYNAMIC || switch (document.getModuleType()) {
      case UNKNOWN, CommandModule, CommonModule, ManagerModule, ValueManagerModule, SessionModule -> true;
      default -> document.getMdObject().isPresent();
    };
  }

  private Optional<VariableSymbol> resolve(TerminalNode name) {
    return name == null ? Optional.empty() : resolve(name.getText());
  }

  private Optional<VariableSymbol> resolve(String name) {
    return names.computeIfAbsent(name, key -> {
      var variable = document.getSymbolTree().getVariableSymbolInScope(body, key);
      variable.filter(v -> v.getKind() == VariableKind.MODULE || v.getKind() == VariableKind.GLOBAL
        || v.getKind() == VariableKind.PARAMETER && !byValue.contains(key)).ifPresent(external::add);
      return variable;
    });
  }

  private final class Operation extends BSLParserBaseVisitor<Void> {
    final Set<VariableSymbol> reads = new HashSet<>();
    @Nullable TerminalNode write;
    @Nullable VariableSymbol variable;
    @Nullable CfgVertex exception;
    boolean observesExternal;
    boolean dynamic;

    @Override
    public Void visitTerminal(TerminalNode node) {
      if (node.getSymbol().getType() == BSLParser.IDENTIFIER && node != write
        && (node.getParent() instanceof BSLParser.ComplexIdentifierContext
          || node.getParent() instanceof BSLParser.CallStatementContext || node.getParent() instanceof BSLParser.LValueContext)) {
        resolve(node).ifPresent(reads::add);
      }
      return null;
    }

    @Override
    public Void visitMethodCall(BSLParser.MethodCallContext ctx) {
      observesExternal = true;
      return super.visitMethodCall(ctx);
    }

    @Override
    public Void visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
      observesExternal = true;
      var name = ctx.methodName().getText();
      dynamic |= name.equalsIgnoreCase("Вычислить") || name.equalsIgnoreCase("Eval");
      return super.visitGlobalMethodCall(ctx);
    }

    @Override
    public Void visitNewExpression(BSLParser.NewExpressionContext ctx) {
      observesExternal = true;
      return super.visitNewExpression(ctx);
    }

    @Override
    public Void visitExecuteStatement(BSLParser.ExecuteStatementContext ctx) {
      dynamic = true;
      return super.visitExecuteStatement(ctx);
    }
  }
}
