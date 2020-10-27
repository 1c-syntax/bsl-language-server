/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.computer.BSLDiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.CognitiveComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexityData;
import com.github._1c_syntax.bsl.languageserver.context.computer.CyclomaticComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.QueryComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.SymbolTreeComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import com.github._1c_syntax.ls_core.context.CoreDocumentContext;
import com.github._1c_syntax.ls_core.context.computer.DiagnosticComputer;
import com.github._1c_syntax.ls_core.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.ls_core.utils.Trees;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import com.github._1c_syntax.mdclasses.metadata.Configuration;
import com.github._1c_syntax.mdclasses.metadata.SupportConfiguration;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.SupportVariant;
import com.github._1c_syntax.utils.Lazy;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.antlr.v4.runtime.tree.Tree;
import org.apache.commons.io.FilenameUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class BSLDocumentContext extends CoreDocumentContext {

  private final FileType fileType;
  private BSLTokenizer tokenizer;

  private final ReentrantLock computeLock = new ReentrantLock();

  private final Lazy<ModuleType> moduleType = new Lazy<>(this::computeModuleType, computeLock);
  private final Lazy<Map<SupportConfiguration, SupportVariant>> supportVariants
    = new Lazy<>(this::computeSupportVariants, computeLock);
  private final Lazy<SymbolTree> symbolTree = new Lazy<>(this::computeSymbolTree, computeLock);
  private final Lazy<ComplexityData> cognitiveComplexityData
    = new Lazy<>(this::computeCognitiveComplexity, computeLock);
  private final Lazy<ComplexityData> cyclomaticComplexityData
    = new Lazy<>(this::computeCyclomaticComplexity, computeLock);
  private final Lazy<MetricStorage> metrics = new Lazy<>(this::computeMetrics, computeLock);

  private final Lazy<List<SDBLTokenizer>> queries = new Lazy<>(this::computeQueries, computeLock);

  public BSLDocumentContext(URI uri, String content, BSLServerContext context, DiagnosticComputer diagnosticComputer) {
    super(uri, content, context, diagnosticComputer);

    this.tokenizer = new BSLTokenizer(content);
    this.fileType = computeFileType(getUri());
  }

  @Override
  public BSLParser.FileContext getAst() {
    requireNonNull(getContent());
    return tokenizer.getAst();
  }

  public SymbolTree getSymbolTree() {
    return symbolTree.getOrCompute();
  }

  @Override
  public List<Token> getTokens() {
    requireNonNull(getContent());
    return tokenizer.getTokens();
  }

  @Override
  protected DiagnosticIgnoranceComputer.Data computeDiagnosticIgnorance() {
    var diagnosticIgnoranceComputer = new BSLDiagnosticIgnoranceComputer(this);
    return diagnosticIgnoranceComputer.compute();
  }

  public List<Token> getComments() {
    return getTokens().stream()
      .filter(token -> token.getType() == BSLLexer.LINE_COMMENT)
      .collect(Collectors.toList());
  }

  public MetricStorage getMetrics() {
    return metrics.getOrCompute();
  }

  public FileType getFileType() {
    return fileType;
  }

  public ComplexityData getCognitiveComplexityData() {
    return cognitiveComplexityData.getOrCompute();
  }

  public ComplexityData getCyclomaticComplexityData() {
    return cyclomaticComplexityData.getOrCompute();
  }

  public ModuleType getModuleType() {
    return moduleType.getOrCompute();
  }

  public Map<SupportConfiguration, SupportVariant> getSupportVariants() {
    return supportVariants.getOrCompute();
  }

  public Optional<MDObjectBase> getMdObject() {
    return Optional.ofNullable(getMDConfiguration().getModulesByObject().get(getUri()));
  }

  public List<SDBLTokenizer> getQueries() {
    return queries.getOrCompute();
  }

  @Override
  public void rebuild(String content) {
    computeLock.lock();
    super.rebuild(content);
    symbolTree.clear();
    tokenizer = new BSLTokenizer(content);
    computeLock.unlock();
  }

  @Override
  public void clearSecondaryData() {
    computeLock.lock();
    super.clearSecondaryData();
    tokenizer = null;
    cognitiveComplexityData.clear();
    cyclomaticComplexityData.clear();
    metrics.clear();
    queries.clear();
    computeLock.unlock();
  }

  private static FileType computeFileType(URI uri) {
    String uriPath = uri.getPath();
    if (uriPath == null) {
      return FileType.BSL;
    }

    FileType fileTypeFromUri;
    try {
      fileTypeFromUri = FileType.valueOf(
        FilenameUtils.getExtension(uriPath).toUpperCase(Locale.ENGLISH)
      );
    } catch (IllegalArgumentException ignored) {
      fileTypeFromUri = FileType.BSL;
    }

    return fileTypeFromUri;
  }

  private SymbolTree computeSymbolTree() {
    return new SymbolTreeComputer(this).compute();
  }

  private ModuleType computeModuleType() {
    return getMDConfiguration().getModuleType(getUri());
  }

  private Map<SupportConfiguration, SupportVariant> computeSupportVariants() {
    return getMDConfiguration().getModuleSupport(getUri());
  }

  private ComplexityData computeCognitiveComplexity() {
    var cognitiveComplexityComputer = new CognitiveComplexityComputer(this);
    return cognitiveComplexityComputer.compute();
  }

  private ComplexityData computeCyclomaticComplexity() {
    var cyclomaticComplexityComputer = new CyclomaticComplexityComputer(this);
    return cyclomaticComplexityComputer.compute();
  }

  private MetricStorage computeMetrics() {
    MetricStorage metricsTemp = new MetricStorage();
    final List<MethodSymbol> methodsUnboxed = getSymbolTree().getMethods();

    metricsTemp.setFunctions(Math.toIntExact(methodsUnboxed.stream().filter(MethodSymbol::isFunction).count()));
    metricsTemp.setProcedures(methodsUnboxed.size() - metricsTemp.getFunctions());

    int[] nclocData = getTokensFromDefaultChannel().stream()
      .mapToInt(Token::getLine)
      .distinct().toArray();
    metricsTemp.setNclocData(nclocData);
    metricsTemp.setNcloc(nclocData.length);

    metricsTemp.setCovlocData(computeCovlocData());

    int lines;
    final List<Token> tokensUnboxed = getTokens();
    if (tokensUnboxed.isEmpty()) {
      lines = 0;
    } else {
      lines = tokensUnboxed.get(tokensUnboxed.size() - 1).getLine();
    }
    metricsTemp.setLines(lines);

    int comments = (int) getComments()
      .stream()
      .map(Token::getLine)
      .distinct()
      .count();
    metricsTemp.setComments(comments);

    int statements = Trees.findAllRuleNodes(getAst(), BSLParser.RULE_statement).size();
    metricsTemp.setStatements(statements);

    metricsTemp.setCognitiveComplexity(getCognitiveComplexityData().getFileComplexity());
    metricsTemp.setCyclomaticComplexity(getCyclomaticComplexityData().getFileComplexity());

    return metricsTemp;
  }

  private int[] computeCovlocData() {

    return Trees.getDescendants(getAst()).stream()
      .filter(Predicate.not(TerminalNodeImpl.class::isInstance))
      .filter(BSLDocumentContext::mustCovered)
      .mapToInt(node -> ((BSLParserRuleContext) node).getStart().getLine())
      .distinct().toArray();

  }

  private static boolean mustCovered(Tree node) {
    return node instanceof BSLParser.StatementContext
      || node instanceof BSLParser.GlobalMethodCallContext
      || node instanceof BSLParser.Var_nameContext;
  }

  private List<SDBLTokenizer> computeQueries() {
    return (new QueryComputer(this)).compute();
  }

  public Configuration getMDConfiguration() {
    return ((BSLServerContext) getServerContext()).getConfiguration();
  }
}
