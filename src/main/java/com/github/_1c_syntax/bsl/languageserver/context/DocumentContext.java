/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.context.computer.CognitiveComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.Computer;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.MethodSymbolComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.RegionSymbolComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.utils.Absolute;
import com.github._1c_syntax.bsl.languageserver.utils.Lazy;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.Tokenizer;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.antlr.v4.runtime.tree.Tree;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.antlr.v4.runtime.Token.DEFAULT_CHANNEL;

public class DocumentContext {

  private String content;
  private ServerContext context;
  private Lazy<String[]> contentList = new Lazy<>(this::computeContentList);
  private Tokenizer tokenizer;
  private Lazy<MetricStorage> metrics = new Lazy<>(this::computeMetrics);
  private Lazy<CognitiveComplexityComputer.Data> cognitiveComplexityData = new Lazy<>(this::computeCognitiveComplexity);
  private Lazy<List<MethodSymbol>> methods = new Lazy<>(this::computeMethods);
  private Lazy<Map<BSLParserRuleContext, MethodSymbol>> nodeToMethodsMap = new Lazy<>(this::computeNodeToMethodsMap);
  private Lazy<List<RegionSymbol>> regions = new Lazy<>(this::computeRegions);
  private Lazy<List<RegionSymbol>> regionsFlat = new Lazy<>(this::computeRegionsFlat);
  private Lazy<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceData = new Lazy<>(this::computeDiagnosticIgnorance);
  private Lazy<ModuleType> moduleType = new Lazy<>(this::computeModuleType);
  private boolean adjustingRegions;
  private boolean regionsAdjusted;
  private final URI uri;
  private final FileType fileType;

  public DocumentContext(URI uri, String content, ServerContext context) {
    final Path absolutePath = Absolute.path(uri);
    this.uri = absolutePath.toUri();
    this.content = content;
    this.context = context;
    this.tokenizer = new Tokenizer(content);

    FileType fileTypeFromUri;
    try {
      fileTypeFromUri = FileType.valueOf(
        FilenameUtils.getExtension(absolutePath.toString()).toUpperCase(Locale.ENGLISH)
      );
    } catch (IllegalArgumentException ignored) {
      fileTypeFromUri = FileType.BSL;
    }

    fileType = fileTypeFromUri;
  }

  public ServerContext getServerContext() {
    return context;
  }

  public String getContent() {
    requireNonNull(content);
    return content;
  }

  public String[] getContentList() {
    return contentList.getOrCompute();
  }

  public BSLParser.FileContext getAst() {
    requireNonNull(content);
    return tokenizer.getAst();
  }

  public List<MethodSymbol> getMethods() {
    final List<MethodSymbol> methodsUnboxed = methods.getOrCompute();
    if (!regionsAdjusted && !adjustingRegions) {
      adjustingRegions = true;
      adjustRegions();
    }
    return new ArrayList<>(methodsUnboxed);
  }

  public Optional<MethodSymbol> getMethodSymbol(BSLParserRuleContext ctx) {
    BSLParserRuleContext methodNode;
    if (ctx instanceof BSLParser.SubContext) {
      methodNode = ((BSLParser.SubContext) ctx).function();
      if (methodNode == null) {
        methodNode = ((BSLParser.SubContext) ctx).procedure();
      }
    } else {
      methodNode = ctx;
    }

    return Optional.ofNullable(getNodeToMethodsMap().get(methodNode));
  }

  public List<RegionSymbol> getRegions() {
    final List<RegionSymbol> regionsUnboxed = regions.getOrCompute();
    if (!regionsAdjusted && !adjustingRegions) {
      adjustingRegions = true;
      adjustRegions();
    }
    return new ArrayList<>(regionsUnboxed);
  }

  public List<RegionSymbol> getRegionsFlat() {
    final List<RegionSymbol> regionsFlatUnboxed = regionsFlat.getOrCompute();
    return new ArrayList<>(regionsFlatUnboxed);
  }

  public List<Token> getTokens() {
    requireNonNull(content);
    return tokenizer.getTokens();
  }

  public List<Token> getTokensFromDefaultChannel() {
    return getTokens().stream().filter(token -> token.getChannel() == DEFAULT_CHANNEL).collect(Collectors.toList());
  }

  public List<Token> getComments() {
    return getTokens().stream()
      .filter(token -> token.getType() == BSLLexer.LINE_COMMENT)
      .collect(Collectors.toList());
  }

  public String getText(Range range) {
    Position start = range.getStart();
    Position end = range.getEnd();

    StringBuilder sb = new StringBuilder();

    String[] contentListUnboxed = getContentList();
    String startString = contentListUnboxed[start.getLine()];
    if (start.getLine() == end.getLine()) {
      sb.append(startString, start.getCharacter(), end.getCharacter());
    } else {
      sb.append(startString.substring(start.getCharacter()));
    }

    for (int i = start.getLine() + 1; i <= end.getLine() - 1; i++) {
      sb.append(contentListUnboxed[i]);
    }

    if (start.getLine() != end.getLine()) {
      sb.append(contentListUnboxed[end.getLine()], 0, end.getCharacter());
    }

    return sb.toString();
  }

  public MetricStorage getMetrics() {
    return metrics.getOrCompute();
  }

  public URI getUri() {
    return uri;
  }

  public FileType getFileType() {
    return fileType;
  }

  public CognitiveComplexityComputer.Data getCognitiveComplexityData() {
    return cognitiveComplexityData.getOrCompute();
  }

  public DiagnosticIgnoranceComputer.Data getDiagnosticIgnorance() {
    return diagnosticIgnoranceData.getOrCompute();
  }

  public ModuleType getModuleType() {
    return moduleType.getOrCompute();
  }

  public void rebuild(String content) {
    clear();
    this.content = content;
    tokenizer = new Tokenizer(content);
  }

  public void clearASTData() {
    content = null;
    contentList.clear();
    tokenizer = null;

    nodeToMethodsMap.clear();

    if (regions.isPresent()) {
      getRegions().forEach(Symbol::clearASTData);
    }
    if (regionsFlat.isPresent()) {
      getRegionsFlat().forEach(Symbol::clearASTData);
    }
    if (methods.isPresent()) {
      getMethods().forEach(Symbol::clearASTData);
    }

    regionsAdjusted = false;
  }

  private void clear() {
    clearASTData();

    metrics.clear();
    cognitiveComplexityData.clear();
    methods.clear();
    regions.clear();
    regionsFlat.clear();
    diagnosticIgnoranceData.clear();
  }

  private Map<BSLParserRuleContext, MethodSymbol> getNodeToMethodsMap() {
    return nodeToMethodsMap.getOrCompute();
  }

  private String[] computeContentList() {
    return getContent().split("\n");
  }

  private List<RegionSymbol> computeRegions() {
    Computer<List<RegionSymbol>> regionSymbolComputer = new RegionSymbolComputer(this);
    return regionSymbolComputer.compute();
  }

  private List<RegionSymbol> computeRegionsFlat() {
    return getRegions().stream()
      .map((RegionSymbol regionSymbol) -> {
        List<RegionSymbol> list = new ArrayList<>();
        list.add(regionSymbol);
        list.addAll(regionSymbol.getChildren());

        return list;
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<MethodSymbol> computeMethods() {
    Computer<List<MethodSymbol>> methodSymbolComputer = new MethodSymbolComputer(this);
    return methodSymbolComputer.compute();
  }

  private Map<BSLParserRuleContext, MethodSymbol> computeNodeToMethodsMap() {
    final Map<BSLParserRuleContext, MethodSymbol> nodeToMethodsMapTemp = new HashMap<>();
    getMethods().forEach(methodSymbol -> nodeToMethodsMapTemp.put(methodSymbol.getNode(), methodSymbol));

    return nodeToMethodsMapTemp;
  }

  private ModuleType computeModuleType() {
    ModuleType type = context.getConfiguration().getModuleType(uri);
    if (type == null) {
      type = ModuleType.ObjectModule;
    }
    return type;
  }

  private CognitiveComplexityComputer.Data computeCognitiveComplexity() {
    Computer<CognitiveComplexityComputer.Data> cognitiveComplexityComputer = new CognitiveComplexityComputer(this);
    return cognitiveComplexityComputer.compute();
  }

  private void adjustRegions() {
    getMethods().forEach((MethodSymbol methodSymbol) ->
      methodSymbol.getRegion().ifPresent(region ->
        region.getMethods().add(methodSymbol)
      )
    );
    regionsAdjusted = true;
  }

  private MetricStorage computeMetrics() {
    MetricStorage metricsTemp = new MetricStorage();
    final List<MethodSymbol> methodsUnboxed = getMethods();

    metricsTemp.setFunctions(Math.toIntExact(methodsUnboxed.stream().filter(MethodSymbol::isFunction).count()));
    metricsTemp.setProcedures(methodsUnboxed.size() - metricsTemp.getFunctions());

    int ncloc = (int) getTokensFromDefaultChannel().stream()
      .map(Token::getLine)
      .distinct()
      .count();

    metricsTemp.setNcloc(ncloc);

    int[] nclocData = getTokensFromDefaultChannel().stream()
      .mapToInt(Token::getLine)
      .distinct().toArray();
    metricsTemp.setNclocData(nclocData);

    metricsTemp.setCovlocData(computeCovlocData());

    int lines;
    final List<Token> tokensUnboxed = getTokens();
    if (tokensUnboxed.isEmpty()) {
      lines = 0;
    } else {
      lines = tokensUnboxed.get(tokensUnboxed.size() - 1).getLine();
    }
    metricsTemp.setLines(lines);

    int comments;
    final List<Token> commentsUnboxed = getComments();
    if (commentsUnboxed.isEmpty()) {
      comments = 0;
    } else {
      comments = (int) commentsUnboxed.stream().map(Token::getLine).distinct().count();
    }
    metricsTemp.setComments(comments);

    int statements = Trees.findAllRuleNodes(getAst(), BSLParser.RULE_statement).size();
    metricsTemp.setStatements(statements);

    metricsTemp.setCognitiveComplexity(getCognitiveComplexityData().getFileComplexity());

    return metricsTemp;
  }

  private int[] computeCovlocData() {

    return Trees.getDescendants(getAst()).stream()
      .filter(node -> !(node instanceof TerminalNodeImpl))
      .filter(this::mustCovered)
      .mapToInt(node -> ((BSLParserRuleContext) node).getStart().getLine())
      .distinct().toArray();

  }

  private boolean mustCovered(Tree node) {

    return node instanceof BSLParser.StatementContext
      || node instanceof BSLParser.GlobalMethodCallContext
      || node instanceof BSLParser.Var_nameContext;
  }

  private DiagnosticIgnoranceComputer.Data computeDiagnosticIgnorance() {
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer = new DiagnosticIgnoranceComputer(this);
    return diagnosticIgnoranceComputer.compute();
  }

}
