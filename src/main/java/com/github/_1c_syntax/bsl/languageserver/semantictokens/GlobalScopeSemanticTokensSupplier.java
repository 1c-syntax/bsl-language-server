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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер семантических токенов для идентификаторов, разрешающихся через
 * global scope ({@link GlobalScopeProvider}). Тип/модификатор токена выбираются
 * по {@link com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind}
 * найденного символа:
 * <ul>
 *   <li>{@code PLATFORM_GLOBAL_PROPERTY} ({@code Справочники}, {@code Метаданные},
 *       {@code ОбщегоНазначения}) → {@code Class + DefaultLibrary};</li>
 *   <li>{@code PLATFORM_GLOBAL_ENUM} ({@code КодировкаТекста}) →
 *       {@code Enum + DefaultLibrary}; значение перечисления первого уровня
 *       ({@code .UTF8}) → {@code EnumMember};</li>
 *   <li>{@code LIBRARY_MODULE} ({@code ФС}) → {@code Namespace}.</li>
 * </ul>
 * Идентификаторы, перекрытые локальной переменной/параметром, пропускаются —
 * локальный символ имеет приоритет.
 */
@Component
@RequiredArgsConstructor
public class GlobalScopeSemanticTokensSupplier implements SemanticTokensSupplier {

  private final GlobalScopeProvider globalScopeProvider;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var ast = documentContext.getAst();
    if (ast == null) {
      return entries;
    }
    var fileType = documentContext.getFileType();
    var symbolTree = documentContext.getSymbolTree();

    // Идентификаторы в expression-позиции: `... = ПервыйОбщийМодуль.X(); А = Справочники.Y;`.
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_complexIdentifier)) {
      if (node instanceof BSLParser.ComplexIdentifierContext chain) {
        processIdentifier(entries, chain.IDENTIFIER(), chain.modifier(), fileType, symbolTree);
      }
    }
    // Идентификаторы в statement-позиции: `ПервыйОбщийМодуль.X();` без присваивания.
    // CallStatement — отдельный rule грамматики, с собственным IDENTIFIER+modifier-цепочкой.
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_callStatement)) {
      if (node instanceof BSLParser.CallStatementContext callStmt) {
        processIdentifier(entries, callStmt.IDENTIFIER(), callStmt.modifier(), fileType, symbolTree);
      }
    }

    return entries;
  }

  private void processIdentifier(List<SemanticTokenEntry> entries,
                                 TerminalNode identifier,
                                 List<? extends BSLParser.ModifierContext> modifiers,
                                 FileType fileType,
                                 SymbolTree symbolTree) {
    if (identifier == null) {
      return;
    }
    var name = identifier.getText();
    if (name == null || name.isBlank()) {
      return;
    }
    // Локальные имена (переменные/параметры) перекрывают глобальные — пропускаем.
    if (symbolTree != null && isLocalName(symbolTree, name)) {
      return;
    }
    var synthetic = globalScopeProvider.findGlobalEntry(name, fileType)
      .filter(entry -> entry.role() == GlobalSymbolScope.Role.VALUE)
      .map(GlobalSymbolScope.Entry::symbol)
      .filter(SyntheticSymbol.class::isInstance)
      .map(SyntheticSymbol.class::cast);
    if (synthetic.isEmpty()) {
      return;
    }
    var kind = synthetic.get().getSyntheticKind();
    switch (kind) {
      case PLATFORM_GLOBAL_PROPERTY -> {
        if (isCommonModuleBacked(synthetic.get())) {
          // Имя — это общий модуль конфигурации; рисуем как namespace.
          helper.addRange(entries, Ranges.create(identifier), SemanticTokenTypes.Namespace);
        } else {
          helper.addRange(entries, Ranges.create(identifier),
            SemanticTokenTypes.Class, SemanticTokenModifiers.DefaultLibrary);
        }
      }
      case PLATFORM_GLOBAL_ENUM -> {
        helper.addRange(entries, Ranges.create(identifier),
          SemanticTokenTypes.Enum, SemanticTokenModifiers.DefaultLibrary);
        // Первое значение в цепочке — это enum-value (например, КодировкаТекста.UTF8).
        if (modifiers != null && !modifiers.isEmpty()) {
          var firstAccess = modifiers.getFirst().accessProperty();
          if (firstAccess != null && firstAccess.IDENTIFIER() != null) {
            helper.addRange(entries, Ranges.create(firstAccess.IDENTIFIER()),
              SemanticTokenTypes.EnumMember);
          }
        }
      }
      case LIBRARY_MODULE -> helper.addRange(entries, Ranges.create(identifier),
        SemanticTokenTypes.Namespace);
      default -> { /* остальные SyntheticKind'ы — не наш домен */ }
    }
  }

  private static boolean isLocalName(SymbolTree symbolTree, String name) {
    return symbolTree.getVariableSymbol(name, symbolTree.getModule()).isPresent();
  }

  /**
   * Проверить, что synthetic-имя в global scope соответствует общему модулю
   * конфигурации (backing — {@link SourceDefinedSymbol} с {@link ModuleType#CommonModule}).
   */
  private static boolean isCommonModuleBacked(SyntheticSymbol synthetic) {
    return synthetic.getSourceSymbol()
      .filter(SourceDefinedSymbol.class::isInstance)
      .map(SourceDefinedSymbol.class::cast)
      .map(s -> s.getOwner().getModuleType() == ModuleType.CommonModule)
      .orElse(false);
  }
}
