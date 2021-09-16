/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticCodeDescription;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider.SOURCE;

public class DiagnosticStorage {

  private final BSLDiagnostic diagnostic;
  private final Queue<Diagnostic> diagnosticList = new ConcurrentLinkedQueue<>();

  DiagnosticStorage(BSLDiagnostic diagnostic) {
    this.diagnostic = diagnostic;
  }

  public List<Diagnostic> getDiagnostics() {
    return new ArrayList<>(diagnosticList);
  }

  public void clearDiagnostics() {
    diagnosticList.clear();
  }

  protected Optional<Diagnostic> addDiagnostic(BSLParserRuleContext node) {
    if (node.exception != null) {
      return Optional.empty();
    }

    return addDiagnostic(
      Ranges.create(node)
    );
  }

  protected Optional<Diagnostic> addDiagnostic(BSLParserRuleContext node, String diagnosticMessage) {
    if (node.exception != null) {
      return Optional.empty();
    }

    return addDiagnostic(
      Ranges.create(node),
      diagnosticMessage
    );
  }

  protected Optional<Diagnostic> addDiagnostic(int startLine, int startChar, int endLine, int endChar) {
    return addDiagnostic(
      Ranges.create(startLine, startChar, endLine, endChar)
    );
  }

  protected Optional<Diagnostic> addDiagnostic(Range range) {
    return addDiagnostic(
      range,
      diagnostic.getInfo().getMessage()
    );
  }

  protected Optional<Diagnostic> addDiagnostic(Range range, String diagnosticMessage) {
    return addDiagnostic(
      range,
      diagnosticMessage,
      null
    );
  }

  protected Optional<Diagnostic> addDiagnostic(Token token) {
    return addDiagnostic(
      Ranges.create(token)
    );
  }

  protected Optional<Diagnostic> addDiagnostic(Token startToken, Token endToken) {
    return addDiagnostic(
      Ranges.create(startToken, endToken)
    );
  }

  protected Optional<Diagnostic> addDiagnostic(Token token, String diagnosticMessage) {
    return addDiagnostic(
      Ranges.create(token),
      diagnosticMessage
    );
  }

  protected Optional<Diagnostic> addDiagnostic(TerminalNode terminalNode) {
    return addDiagnostic(terminalNode.getSymbol());
  }

  protected Optional<Diagnostic> addDiagnostic(TerminalNode terminalNode, String diagnosticMessage) {
    return addDiagnostic(terminalNode.getSymbol(), diagnosticMessage);
  }

  protected Optional<Diagnostic> addDiagnostic(TerminalNode startTerminalNode, TerminalNode stopTerminalNode) {
    return addDiagnostic(startTerminalNode.getSymbol(), stopTerminalNode.getSymbol());
  }

  protected Optional<Diagnostic> addDiagnostic(BSLParserRuleContext node, List<DiagnosticRelatedInformation> relatedInformation) {
    if (node.exception != null) {
      return Optional.empty();
    }

    return addDiagnostic(
      node,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public Optional<Diagnostic> addDiagnostic(Token token, List<DiagnosticRelatedInformation> relatedInformation) {
    return addDiagnostic(
      token,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public Optional<Diagnostic> addDiagnostic(
    BSLParserRuleContext node,
    String diagnosticMessage,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {

    if (node.exception != null) {
      return Optional.empty();
    }

    return addDiagnostic(
      Ranges.create(node),
      diagnosticMessage,
      relatedInformation
    );
  }

  public Optional<Diagnostic> addDiagnostic(
    Token token,
    String diagnosticMessage,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    return addDiagnostic(
      Ranges.create(token),
      diagnosticMessage,
      relatedInformation
    );
  }

  public Optional<Diagnostic> addDiagnostic(
    Range range,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    return addDiagnostic(
      range,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public Optional<Diagnostic> addDiagnostic(
    Range range,
    String diagnosticMessage,
    @Nullable List<DiagnosticRelatedInformation> relatedInformation
  ) {

    var dgs = createDiagnostic(
      diagnostic,
      range,
      diagnosticMessage,
      relatedInformation
    );

    diagnosticList.add(dgs);

    return Optional.of(dgs);
  }

  public void addDiagnostic(ParseTree tree) {
    if (tree instanceof BSLParserRuleContext) {
      addDiagnostic((BSLParserRuleContext) tree);
    } else if (tree instanceof TerminalNode) {
      addDiagnostic((TerminalNode) tree);
    } else {
      throw new IllegalArgumentException("Unsupported parameter type " + tree);
    }
  }

  private static Diagnostic createDiagnostic(
    BSLDiagnostic bslDiagnostic,
    Range range,
    String diagnosticMessage,
    @Nullable List<DiagnosticRelatedInformation> relatedInformation
  ) {
    var info = bslDiagnostic.getInfo();

    Diagnostic diagnostic = new Diagnostic(
      range,
      diagnosticMessage,
      info.getLSPSeverity(),
      SOURCE
    );

    diagnostic.setCode(info.getCode());
    diagnostic.setTags(info.getLSPTags());

    var codeDescription = new DiagnosticCodeDescription(info.getDiagnosticCodeDescriptionHref());
    diagnostic.setCodeDescription(codeDescription);

    if (relatedInformation != null) {
      diagnostic.setRelatedInformation(relatedInformation);
    }
    return diagnostic;
  }

}
