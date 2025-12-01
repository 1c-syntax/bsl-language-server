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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticCodeDescription;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider.SOURCE;

/**
 * Хранилище диагностик для одного анализатора.
 * <p>
 * Потокобезопасное хранилище для накопления диагностик в процессе анализа.
 * Предоставляет методы добавления диагностик с различными источниками данных:
 * узлы AST, токены, символы, диапазоны.
 */
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

  protected void addDiagnostic(ParserRuleContext node) {
    if (node.exception != null) {
      return;
    }

    addDiagnostic(
      Ranges.create(node)
    );
  }

  protected void addDiagnostic(ParserRuleContext node, DiagnosticAdditionalData data) {
    if (node.exception != null) {
      return;
    }

    addDiagnostic(
      Ranges.create(node),
      data
    );
  }

  protected void addDiagnostic(ParserRuleContext node, String diagnosticMessage) {
    if (node.exception != null) {
      return;
    }

    addDiagnostic(
      Ranges.create(node),
      diagnosticMessage
    );
  }

  protected void addDiagnostic(int startLine, int startChar, int endLine, int endChar) {
    addDiagnostic(
      Ranges.create(startLine, startChar, endLine, endChar)
    );
  }

  protected void addDiagnostic(Range range) {
    addDiagnostic(
      range,
      diagnostic.getInfo().getMessage()
    );
  }

  protected void addDiagnostic(Range range, DiagnosticAdditionalData data) {
    addDiagnostic(
      range,
      data,
      diagnostic.getInfo().getMessage()
    );
  }

  protected void addDiagnostic(Range range, String diagnosticMessage) {
    addDiagnostic(
      range,
      diagnosticMessage,
      null
    );
  }

  protected void addDiagnostic(Range range, DiagnosticAdditionalData data, String diagnosticMessage) {
    addDiagnostic(
      range,
      data,
      diagnosticMessage,
      null
    );
  }

  protected void addDiagnostic(Token token) {
    addDiagnostic(
      Ranges.create(token)
    );
  }

  protected void addDiagnostic(Token startToken, Token endToken) {
    addDiagnostic(
      Ranges.create(startToken, endToken)
    );
  }

  protected void addDiagnostic(Token token, String diagnosticMessage) {
    addDiagnostic(
      Ranges.create(token),
      diagnosticMessage
    );
  }

  protected void addDiagnostic(TerminalNode terminalNode) {
    addDiagnostic(terminalNode.getSymbol());
  }

  protected void addDiagnostic(TerminalNode terminalNode, String diagnosticMessage) {
    addDiagnostic(terminalNode.getSymbol(), diagnosticMessage);
  }

  protected void addDiagnostic(TerminalNode startTerminalNode, TerminalNode stopTerminalNode) {
    addDiagnostic(startTerminalNode.getSymbol(), stopTerminalNode.getSymbol());
  }

  protected void addDiagnostic(ParserRuleContext node, List<DiagnosticRelatedInformation> relatedInformation) {
    if (node.exception != null) {
      return;
    }

    addDiagnostic(
      node,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public void addDiagnostic(Token token, List<DiagnosticRelatedInformation> relatedInformation) {
    addDiagnostic(
      token,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public void addDiagnostic(
    ParserRuleContext node,
    String diagnosticMessage,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {

    if (node.exception != null) {
      return;
    }

    addDiagnostic(
      Ranges.create(node),
      diagnosticMessage,
      relatedInformation
    );
  }

  public void addDiagnostic(
    Token token,
    String diagnosticMessage,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    addDiagnostic(
      Ranges.create(token),
      diagnosticMessage,
      relatedInformation
    );
  }

  public void addDiagnostic(
    Range range,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    addDiagnostic(
      range,
      diagnostic.getInfo().getMessage(),
      relatedInformation
    );
  }

  public void addDiagnostic(
    Range range,
    String diagnosticMessage,
    @Nullable List<DiagnosticRelatedInformation> relatedInformation
  ) {

    if (Ranges.isEmpty(range)) {
      return;
    }

    diagnosticList.add(createDiagnostic(
      diagnostic,
      range,
      null,
      diagnosticMessage,
      relatedInformation
    ));
  }

  public void addDiagnostic(
    Range range,
    DiagnosticAdditionalData data,
    String diagnosticMessage,
    @Nullable List<DiagnosticRelatedInformation> relatedInformation
  ) {

    if (Ranges.isEmpty(range)) {
      return;
    }

    diagnosticList.add(createDiagnostic(
      diagnostic,
      range,
      data,
      diagnosticMessage,
      relatedInformation
    ));
  }

  public void addDiagnostic(ParseTree tree) {
    if (tree instanceof ParserRuleContext parserRuleContext) {
      addDiagnostic(parserRuleContext);
    } else if (tree instanceof TerminalNode terminalNode) {
      addDiagnostic(terminalNode);
    } else {
      throw new IllegalArgumentException("Unsupported parameter type " + tree);
    }
  }

  /**
   * Добавляет диагностику по ссылке на символ, используя в качестве области - область символа
   *
   * @param sourceDefinedSymbol ссылка на метод
   */
  protected void addDiagnostic(SourceDefinedSymbol sourceDefinedSymbol) {
    addDiagnostic(sourceDefinedSymbol.getSelectionRange());
  }

  /**
   * Создает доп данные для диагностики на основании строки
   *
   * @param string Некая строка для помещения в доп данные диагностики
   * @return Допданные диагностики
   */
  public static DiagnosticAdditionalData createAdditionalData(String string) {
    return new DiagnosticAdditionalData(string);
  }

  private static Diagnostic createDiagnostic(
    BSLDiagnostic bslDiagnostic,
    Range range,
    @Nullable DiagnosticAdditionalData data,
    String diagnosticMessage,
    @Nullable List<DiagnosticRelatedInformation> relatedInformation
  ) {
    var info = bslDiagnostic.getInfo();

    var diagnostic = new Diagnostic(
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

    if (data != null) {
      diagnostic.setData(data);
    }
    return diagnostic;
  }

  /**
   * Служебный класс для хранения вспомогательной информации диагностики, которая может использоваться
   * например в квикфиксах.
   * Пока реализация примитивная под конкретную задачу
   *
   * @param string Некая строка
   */
  public record DiagnosticAdditionalData(String string) {

  }
}
