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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.computer.CognitiveComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexityData;
import com.github._1c_syntax.bsl.languageserver.context.computer.Computer;
import com.github._1c_syntax.bsl.languageserver.context.computer.CyclomaticComplexityComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.QueryComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.SymbolTreeComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import com.github._1c_syntax.bsl.support.SupportVariant;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.Lazy;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Locked;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.antlr.v4.runtime.Token.DEFAULT_CHANNEL;

/**
 * Контекст документа - содержит полную информацию об анализируемом файле.
 * <p>
 * Управляет синтаксическим деревом, токенизацией, символьной таблицей,
 * метриками сложности, диагностиками и другими аспектами анализа кода BSL.
 * Является центральным объектом для работы с отдельным файлом модуля.
 */
@Component
@Scope("prototype")
@Slf4j
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//@NullUnmarked
public class DocumentContext implements Comparable<DocumentContext> {

  private static final Pattern CONTENT_SPLIT_PATTERN = Pattern.compile("\r?\n|\r");

  @Getter
  @EqualsAndHashCode.Include
  private final URI uri;

  @Nullable
  private String content;

  @Getter
  private int version;

  @Setter(onMethod = @__({@Autowired}))
  private ServerContext context;
  @Setter(onMethod = @__({@Autowired}))
  private DiagnosticComputer diagnosticComputer;
  @Setter(onMethod = @__({@Autowired}))
  private LanguageServerConfiguration configuration;

  @Setter(onMethod = @__({@Autowired}))
  private ObjectProvider<CognitiveComplexityComputer> cognitiveComplexityComputerProvider;
  @Setter(onMethod = @__({@Autowired}))
  private ObjectProvider<CyclomaticComplexityComputer> cyclomaticComplexityComputerProvider;

  @Nullable
  private BSLTokenizer tokenizer;

  @Getter(onMethod = @__({@Locked("computeLock")}))
  private SymbolTree symbolTree = SymbolTreeComputer.empty(this);

  @Getter
  private final FileType fileType;

  @Getter
  private boolean isComputedDataFrozen;

  private final ReentrantLock computeLock = new ReentrantLock();
  private final ReentrantLock diagnosticsLock = new ReentrantLock();

  private final Lazy<String[]> contentList = new Lazy<>(this::computeContentList, computeLock);
  private final Lazy<ModuleType> moduleType = new Lazy<>(this::computeModuleType, computeLock);
  private final Lazy<ComplexityData> cognitiveComplexityData
    = new Lazy<>(this::computeCognitiveComplexity, computeLock);
  private final Lazy<ComplexityData> cyclomaticComplexityData
    = new Lazy<>(this::computeCyclomaticComplexity, computeLock);
  private final Lazy<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceData
    = new Lazy<>(this::computeDiagnosticIgnorance, computeLock);
  private final Lazy<MetricStorage> metrics = new Lazy<>(this::computeMetrics, computeLock);
  private final Lazy<List<Diagnostic>> diagnostics = new Lazy<>(this::computeDiagnostics, diagnosticsLock);

  private final Lazy<List<SDBLTokenizer>> queries = new Lazy<>(this::computeQueries, computeLock);

  public DocumentContext(URI uri) {
    this.uri = uri;
    this.fileType = computeFileType(uri);
  }

  public ServerContext getServerContext() {
    return context;
  }

  @Locked("computeLock")
  public String getContent() {
    requireNonNull(content);
    return content;
  }

  @Locked("computeLock")
  public String[] getContentList() {
    return contentList.getOrCompute();
  }

  @Locked("computeLock")
  public BSLParser.FileContext getAst() {
    ensureInitialized();
    requireNonNull(tokenizer, "Document tokenizer is not initialized for URI: " + uri);
    return tokenizer.getAst();
  }

  @Locked("computeLock")
  public List<Token> getTokens() {
    ensureInitialized();
    requireNonNull(tokenizer, "Document tokenizer is not initialized for URI: " + uri);
    return tokenizer.getTokens();
  }

  public List<Token> getTokensFromDefaultChannel() {
    return getTokens().stream().filter(token -> token.getChannel() == DEFAULT_CHANNEL).toList();
  }

  public List<Token> getComments() {
    return getTokens().stream()
      .filter(token -> token.getType() == BSLLexer.LINE_COMMENT)
      .toList();
  }

  @Locked("computeLock")
  public String getText(Range range) {
    Position start = range.getStart();
    Position end = range.getEnd();

    String[] contentListUnboxed = getContentList();

    if (start.getLine() > contentListUnboxed.length || end.getLine() > contentListUnboxed.length) {
      throw new ArrayIndexOutOfBoundsException("Range goes beyond the boundaries of the parsed document");
    }

    var startString = contentListUnboxed[start.getLine()];
    var sb = new StringBuilder();

    if (start.getLine() == end.getLine()) {
      sb.append(startString, start.getCharacter(), end.getCharacter());
    } else {
      sb.append(startString.substring(start.getCharacter())).append("\n");
    }

    for (int i = start.getLine() + 1; i <= end.getLine() - 1; i++) {
      sb.append(contentListUnboxed[i]).append("\n");
    }

    if (start.getLine() != end.getLine()) {
      sb.append(contentListUnboxed[end.getLine()], 0, end.getCharacter());
    }

    return sb.toString();
  }

  public Locale getScriptVariantLocale() {
    var mdConfiguration = getServerContext().getConfiguration();

    String languageTag;
    if (mdConfiguration.getConfigurationSource() == ConfigurationSource.EMPTY || fileType == FileType.OS) {
      languageTag = configuration.getLanguage().getLanguageCode();
    } else {
      var scriptVariant = mdConfiguration.getScriptVariant();
      if (scriptVariant != ScriptVariant.UNKNOWN) {
        languageTag = scriptVariant.shortName();
      } else {
        throw new IllegalArgumentException("Unknown scriptVariant " + scriptVariant);
      }
    }
    return Locale.forLanguageTag(languageTag);
  }

  public MetricStorage getMetrics() {
    return metrics.getOrCompute();
  }

  public ComplexityData getCognitiveComplexityData() {
    return cognitiveComplexityData.getOrCompute();
  }

  public ComplexityData getCyclomaticComplexityData() {
    return cyclomaticComplexityData.getOrCompute();
  }

  public DiagnosticIgnoranceComputer.Data getDiagnosticIgnorance() {
    return diagnosticIgnoranceData.getOrCompute();
  }

  public ModuleType getModuleType() {
    return moduleType.getOrCompute();
  }

  public SupportVariant getSupportVariant() {
    return getMdObject().map(MD::getSupportVariant).orElse(SupportVariant.NONE);
  }

  public Optional<MD> getMdObject() {
    return getServerContext().getConfiguration().findChild(getUri());
  }

  /**
   * Возвращает строковое представление ссылки связанного с объектом объекта метаданных 1С либо строку URI для
   * остальных случаев
   *
   * @return Строковое представление ссылки
   */
  public String getMdoRef() {
    return MdoRefBuilder.getMdoRef(this);
  }

  public List<SDBLTokenizer> getQueries() {
    return queries.getOrCompute();
  }

  public List<Diagnostic> getDiagnostics() {
    return diagnostics.getOrCompute();
  }

  public List<Diagnostic> getComputedDiagnostics() {
    return Optional
      .ofNullable(diagnostics.get())
      .orElseGet(Collections::emptyList);
  }

  public void freezeComputedData() {
    isComputedDataFrozen = true;
  }

  public void unfreezeComputedData() {
    isComputedDataFrozen = false;
  }

  protected void rebuild(String content, int version) {
    acquireLocks();

    try {

      boolean versionMatches = version == this.version && version != 0;

      if (versionMatches && (this.content != null)) {
        clearDependantData();
        return;
      }

      if (!isComputedDataFrozen) {
        clearSecondaryData();
      }

      this.content = content;
      if (tokenizer != null) {
        tokenizer.rebuild(content);
      } else {
        tokenizer = new BSLTokenizer(content);
      }
      this.version = version;
      symbolTree = computeSymbolTree();

    } finally {
      releaseLocks();
    }

  }

  protected void rebuild() {
    try {
      var newContent = FileUtils.readFileToString(new File(uri), StandardCharsets.UTF_8);
      rebuild(newContent, 0);
    } catch (IOException e) {
      LOGGER.error("Can't rebuild content from uri", e);
    }
  }

  protected void clearSecondaryData() {
    acquireLocks();

    try {

      content = null;
      contentList.clear();
      tokenizer = null;
      queries.clear();
      clearDependantData();

      if (!isComputedDataFrozen) {
        cognitiveComplexityData.clear();
        cyclomaticComplexityData.clear();
        metrics.clear();
        diagnosticIgnoranceData.clear();
      }
    } finally {
      releaseLocks();
    }
  }

  /**
   * Убедитесь, что локи установлены корректно перед вызовом метода.
   */
  private void clearDependantData() {
    diagnostics.clear();
  }

  /**
   * Ensures that the document has been initialized with content.
   * If the tokenizer is null, triggers a rebuild from the file on disk.
   * Note: This is safe to call from within @Locked methods because computeLock is reentrant.
   */
  private void ensureInitialized() {
    if (tokenizer == null && context != null) {
      context.rebuildDocument(this);
    }
  }

  private void acquireLocks() {
    diagnosticsLock.lock();
    computeLock.lock();
  }

  private void releaseLocks() {
    computeLock.unlock();
    diagnosticsLock.unlock();
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

  private String[] computeContentList() {
    return CONTENT_SPLIT_PATTERN.split(getContent(), -1);
  }

  private SymbolTree computeSymbolTree() {
    return new SymbolTreeComputer(this).compute();
  }


  private ModuleType computeModuleType() {
    return context.getConfiguration().getModuleTypeByURI(uri);
  }

  private ComplexityData computeCognitiveComplexity() {
    Computer<ComplexityData> cognitiveComplexityComputer = cognitiveComplexityComputerProvider.getObject(this);
    return cognitiveComplexityComputer.compute();
  }

  private ComplexityData computeCyclomaticComplexity() {
    Computer<ComplexityData> cyclomaticComplexityComputer = cyclomaticComplexityComputerProvider.getObject(this);
    return cyclomaticComplexityComputer.compute();
  }

  private MetricStorage computeMetrics() {
    var metricsTemp = new MetricStorage();
    final List<MethodSymbol> methodsUnboxed = symbolTree.getMethods();

    metricsTemp.setFunctions(Math.toIntExact(methodsUnboxed.stream().filter(MethodSymbol::isFunction).count()));
    metricsTemp.setProcedures(methodsUnboxed.size() - metricsTemp.getFunctions());

    int[] nclocData = getTokensFromDefaultChannel().stream()
      .mapToInt(Token::getLine)
      .distinct().toArray();
    metricsTemp.setNclocData(nclocData);
    metricsTemp.setNcloc(nclocData.length);

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

    metricsTemp.setCognitiveComplexity(getCognitiveComplexityData().fileComplexity());
    metricsTemp.setCyclomaticComplexity(getCyclomaticComplexityData().fileComplexity());

    return metricsTemp;
  }

  private DiagnosticIgnoranceComputer.Data computeDiagnosticIgnorance() {
    Computer<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceComputer = new DiagnosticIgnoranceComputer(this);
    return diagnosticIgnoranceComputer.compute();
  }

  private List<Diagnostic> computeDiagnostics() {
    return diagnosticComputer.compute(this);
  }

  private List<SDBLTokenizer> computeQueries() {
    return (new QueryComputer(this)).compute();
  }

  @Override
  public int compareTo(DocumentContext other) {
    return Comparator.comparing(DocumentContext::getUri)
      .thenComparing(DocumentContext::getVersion)
      .compare(this, other);
  }
}
