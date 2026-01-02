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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сапплаер семантических токенов для препроцессорных директив.
 */
@Component
@RequiredArgsConstructor
public class PreprocessorSemanticTokensSupplier implements SemanticTokensSupplier {

  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var ast = documentContext.getAst();

    addRegionsNamespaces(entries, ast);
    addDirectives(entries, ast);
    addOtherPreprocs(entries, ast);

    return entries;
  }

  // Regions as Namespace: handle all regionStart and regionEnd nodes explicitly
  private void addRegionsNamespaces(List<SemanticTokenEntry> entries, BSLParser.FileContext ast) {
    for (var regionStart : Trees.<BSLParser.RegionStartContext>findAllRuleNodes(ast, BSLParser.RULE_regionStart)) {
      // Namespace only for '#'+keyword part to avoid overlap with region name token
      var preprocessor = Trees.<BSLParser.PreprocessorContext>getAncestorByRuleIndex(regionStart, BSLParser.RULE_preprocessor);
      if (preprocessor != null && regionStart.PREPROC_REGION() != null) {
        helper.addRange(entries,
          Ranges.create(preprocessor.getStart(), regionStart.PREPROC_REGION().getSymbol()),
          SemanticTokenTypes.Namespace);
      } else {
        addNamespaceForPreprocessorNode(entries, regionStart);
      }
      // region name highlighted as Variable (consistent with #Использовать <libName>)
      if (regionStart.regionName() != null) {
        helper.addRange(entries, Ranges.create(regionStart.regionName()), SemanticTokenTypes.Variable);
      }
    }
    for (var regionEnd : Trees.<BSLParser.RegionEndContext>findAllRuleNodes(ast, BSLParser.RULE_regionEnd)) {
      addNamespaceForPreprocessorNode(entries, regionEnd);
    }
  }

  // Use directives as Namespace: #Использовать ...
  // Native directives as Macro: #native
  private void addDirectives(List<SemanticTokenEntry> entries, BSLParser.FileContext ast) {
    for (var use : Trees.<BSLParser.UseContext>findAllRuleNodes(ast, BSLParser.RULE_use)) {
      addNamespaceForUse(entries, use);
    }

    for (var nativeCtx : Trees.<BSLParser.Preproc_nativeContext>findAllRuleNodes(ast, BSLParser.RULE_preproc_native)) {
      var hash = nativeCtx.HASH();
      var nativeKw = nativeCtx.PREPROC_NATIVE();
      if (hash != null) {
        helper.addRange(entries, Ranges.create(hash), SemanticTokenTypes.Macro);
      }
      if (nativeKw != null) {
        helper.addRange(entries, Ranges.create(nativeKw), SemanticTokenTypes.Macro);
      }
    }
  }

  // Other preprocessor directives: Macro for each HASH and PREPROC_* token,
  // excluding region start/end, native, use (handled as Namespace)
  private void addOtherPreprocs(List<SemanticTokenEntry> entries, BSLParser.FileContext ast) {
    for (var preprocessor : Trees.<BSLParser.PreprocessorContext>findAllRuleNodes(ast, BSLParser.RULE_preprocessor)) {
      boolean containsRegion = (preprocessor.regionStart() != null) || (preprocessor.regionEnd() != null);
      if (containsRegion) {
        continue; // region handled as Namespace above
      }

      for (Token token : Trees.getTokens(preprocessor)) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(token.getType());
        if (token.getType() == BSLLexer.HASH || (symbolicName != null && symbolicName.startsWith("PREPROC_"))) {
          helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.Macro);
        }
      }
    }
  }

  private void addNamespaceForPreprocessorNode(List<SemanticTokenEntry> entries, ParserRuleContext preprocessorChildNode) {
    var preprocessor = Trees.<BSLParser.PreprocessorContext>getAncestorByRuleIndex(preprocessorChildNode, BSLParser.RULE_preprocessor);
    if (preprocessor == null) {
      return;
    }
    var hashToken = preprocessor.getStart();
    if (hashToken == null) {
      return;
    }
    var endToken = preprocessorChildNode.getStop();
    helper.addRange(entries, Ranges.create(hashToken, endToken), SemanticTokenTypes.Namespace);
  }

  private void addNamespaceForUse(List<SemanticTokenEntry> entries, BSLParser.UseContext useCtx) {
    var hashNode = useCtx.HASH();
    var useNode = useCtx.PREPROC_USE_KEYWORD();

    if (hashNode != null && useNode != null) {
      helper.addRange(entries, Ranges.create(hashNode, useNode), SemanticTokenTypes.Namespace);
    } else if (hashNode != null) {
      helper.addRange(entries, Ranges.create(hashNode), SemanticTokenTypes.Namespace);
    }
    // else: neither hashNode nor useNode present - nothing to add

    Optional.ofNullable(useCtx.usedLib())
      .map(BSLParser.UsedLibContext::PREPROC_IDENTIFIER)
      .ifPresent(id -> helper.addRange(entries, Ranges.create(id), SemanticTokenTypes.Variable));
  }
}

