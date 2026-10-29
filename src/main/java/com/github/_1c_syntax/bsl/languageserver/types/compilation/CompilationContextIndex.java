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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorContexts;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Объявления и границы препроцессора одной версии дерева; значения не удерживают AST. */
final class CompilationContextIndex {
  /**
   * Объявления по идентичности символьного дерева. Значения не удерживают дерево или документ;
   * слабый ключ позволяет удалить запись после освобождения всех внешних ссылок на старое дерево.
   */
  private final Cache<SymbolTree, Map<String, List<Declaration>>> declarations =
    Caffeine.newBuilder().weakKeys().build();
  /** Границы инструкций и ограничения после них. Значения не удерживают AST или документ. */
  private final Cache<SymbolTree, List<PreprocessorBranch>> preprocessorBranches =
    Caffeine.newBuilder().weakKeys().build();

  private record PreprocessorBranch(Range directive, @Nullable Set<ExecutionContext> contexts) { }


  Map<String, List<Declaration>> declarations(DocumentContext document) {
    return declarations.get(document.getSymbolTreeNoLock(), CompilationContextIndex::collectDeclarations);
  }

  /** Последняя инструкция перед позицией находится двоичным поиском; AST обходится один раз на версию. */
  @Nullable Set<ExecutionContext> preprocessorContextsAt(DocumentContext document, Position position) {
    var branches = preprocessorBranches.get(document.getSymbolTree(), ignored -> collectPreprocessorBranches(document));
    var low = 0;
    var high = branches.size();
    while (low < high) {
      var middle = (low + high) >>> 1;
      var start = branches.get(middle).directive().getStart();
      if (start.getLine() < position.getLine()
          || (start.getLine() == position.getLine() && start.getCharacter() <= position.getCharacter())) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    if (low == 0) {
      return ExecutionContext.ALL;
    }
    var branch = branches.get(low - 1);
    return Ranges.containsPosition(branch.directive(), position) ? null : branch.contexts();
  }

  private static List<PreprocessorBranch> collectPreprocessorBranches(DocumentContext document) {
    var result = new ArrayList<PreprocessorBranch>();
    var preprocessor = new PreprocessorContexts();
    for (var tree : Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_preprocessor)) {
      var node = (BSLParser.PreprocessorContext) tree;
      if (!preprocessor.enter(node)) {
        continue;
      }
      var active = preprocessor.active();
      result.add(new PreprocessorBranch(Ranges.create(node), active == null ? null : Set.copyOf(active)));
    }
    return List.copyOf(result);
  }

  private static Map<String, List<Declaration>> collectDeclarations(SymbolTree tree) {
    Map<String, List<Declaration>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (var method : tree.getMethods()) {
      result.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(new Declaration(
        method.getCompilerDirectiveKind(),
        tree.getMethodPreprocessorContexts(method), method.isExport()));
    }
    result.replaceAll((name, methods) -> List.copyOf(methods));
    return Collections.unmodifiableMap(result);
  }
}
