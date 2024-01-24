/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import jakarta.annotation.PostConstruct;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Leon Chagelishvili &lt;lChagelishvily@gmail.com&gt;
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
public class IfElseDuplicatedCodeBlockDiagnostic extends AbstractVisitorDiagnostic {

  private String relatedMessage;
  private final Set<BSLParser.CodeBlockContext> checkedBlocks = new HashSet<>();

  @PostConstruct
  public void init() {
    relatedMessage = this.info.getResourceString("identicalCodeBlockRelatedMessage");
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    checkedBlocks.clear();
    List<BSLParser.CodeBlockContext> codeBlocks = new ArrayList<>();

    codeBlocks.add(ctx.ifBranch().codeBlock());

    ctx.elsifBranch().stream()
      .map(BSLParser.ElsifBranchContext::codeBlock)
      .collect(Collectors.toCollection(() -> codeBlocks));


    var elseBranch = ctx.elseBranch();
    if (elseBranch != null) {
      codeBlocks.add(elseBranch.codeBlock());
    }

    findDuplicatedCodeBlock(codeBlocks);
    return super.visitIfStatement(ctx);
  }

  private void findDuplicatedCodeBlock(List<BSLParser.CodeBlockContext> codeBlockContexts) {
    for (int i = 0; i < codeBlockContexts.size() - 1; i++) {
      if (!checkedBlocks.contains(codeBlockContexts.get(i))) {
        checkCodeBlock(codeBlockContexts, i);
      }
    }
  }

  private void checkCodeBlock(List<BSLParser.CodeBlockContext> codeBlockContexts, int i) {
    var currentCodeBlock = codeBlockContexts.get(i);

    var identicalCodeBlocks = codeBlockContexts.stream()
      .skip(i)
      .filter(codeBlockContext ->
        !codeBlockContext.equals(currentCodeBlock)
          && !(currentCodeBlock.children == null && codeBlockContext.children == null)
          && DiagnosticHelper.equalNodes(currentCodeBlock, codeBlockContext))
      .toList();

    if (identicalCodeBlocks.isEmpty()) {
      return;
    }

    identicalCodeBlocks.stream().collect(Collectors.toCollection(() -> checkedBlocks));
    List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();

    relatedInformation.add(RelatedInformation.create(
      documentContext.getUri(),
      Ranges.create(currentCodeBlock),
      relatedMessage
    ));

    identicalCodeBlocks.stream()
      .map(codeBlockContext ->
        RelatedInformation.create(
          documentContext.getUri(),
          Ranges.create(codeBlockContext),
          relatedMessage
        )
      )
      .collect(Collectors.toCollection(() -> relatedInformation));

    diagnosticStorage.addDiagnostic(currentCodeBlock, relatedInformation);
  }

}

