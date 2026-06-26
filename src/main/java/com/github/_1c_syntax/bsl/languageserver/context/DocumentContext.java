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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import com.github._1c_syntax.bsl.support.SupportVariant;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptModuleTypeResolver;
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

  private final ServerContext context;
  @SuppressWarnings("NullAway.Init")
  @Setter(onMethod_ = {@Autowired})
  private DiagnosticComputer diagnosticComputer;

  @SuppressWarnings("NullAway.Init")
  @Setter(onMethod_ = {@Autowired})
  private ObjectProvider<CognitiveComplexityComputer> cognitiveComplexityComputerProvider;
  @SuppressWarnings("NullAway.Init")
  @Setter(onMethod_ = {@Autowired})
  private ObjectProvider<CyclomaticComplexityComputer> cyclomaticComplexityComputerProvider;

  @SuppressWarnings("NullAway.Init")
  @Setter(onMethod_ = {@Autowired})
  private OScriptModuleTypeResolver oScriptModuleTypeResolver;

  @Nullable
  private BSLTokenizer tokenizer;

  @Getter(onMethod_ = {@Locked("computeLock")})
  private SymbolTree symbolTree = SymbolTreeComputer.empty(this);

  @Getter
  private final FileType fileType;

  @Getter
  private boolean isComputedDataFrozen;

  private final ReentrantLock computeLock = new ReentrantLock();
  private final ReentrantLock diagnosticsLock = new ReentrantLock();

  private final Lazy<String[]> contentList = new Lazy<>(this::computeContentList, computeLock);
  private final Lazy<ModuleType> moduleType = new Lazy<>(this::computeModuleType, computeLock);
  // MD-объект и mdoRef документа зависят только от его URI и конфигурации (не от содержимого),
  // а конфигурация инвариантна на всё время жизни DocumentContext (её перезагрузка в
  // ServerContext.clear() выбрасывает все документы). Поэтому намеренно НЕ сбрасываются в
  // clearSecondaryData — считаются один раз на жизнь документа.
  private final Lazy<Optional<MD>> mdObject = new Lazy<>(this::computeMdObject, computeLock);
  private final Lazy<String> mdoRef = new Lazy<>(this::computeMdoRef, computeLock);
  private final Lazy<ComplexityData> cognitiveComplexityData
    = new Lazy<>(this::computeCognitiveComplexity, computeLock);
  private final Lazy<ComplexityData> cyclomaticComplexityData
    = new Lazy<>(this::computeCyclomaticComplexity, computeLock);
  private final Lazy<DiagnosticIgnoranceComputer.Data> diagnosticIgnoranceData
    = new Lazy<>(this::computeDiagnosticIgnorance, computeLock);
  private final Lazy<MetricStorage> metrics = new Lazy<>(this::computeMetrics, computeLock);
  private final Lazy<List<Diagnostic>> diagnostics = new Lazy<>(this::computeDiagnostics, diagnosticsLock);

  private final Lazy<List<SDBLTokenizer>> queries = new Lazy<>(this::computeQueries, computeLock);

  public DocumentContext(URI uri, ServerContext context) {
    this.uri = uri;
    this.context = context;
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
    requireNonNull(tokenizer);
    return tokenizer.getAst();
  }

  @Locked("computeLock")
  public List<Token> getTokens() {
    requireNonNull(tokenizer);
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
    return getScriptVariantLanguage().getLocale();
  }

  /**
   * Язык исходников проекта: для конфигурации с заданным {@code ScriptVariant} —
   * именно он (русский/английский); для OS-файлов и проектов без mdclasses-конфы —
   * {@link LanguageServerConfiguration#getLanguage()}.
   * <p>
   * Этот язык — преобладающий в коде. Completion/format/canonicalization keyword'ов
   * пишут именно в нём, чтобы соответствовать стилю проекта (а не персональным
   * настройкам интерфейса).
   */
  public Language getScriptVariantLanguage() {
    var mdConfiguration = getServerContext().getConfiguration();
    if (mdConfiguration.getConfigurationSource() == ConfigurationSource.EMPTY || fileType == FileType.OS) {
      return getServerContext().getLanguageServerConfiguration().getLanguage();
    }
    var scriptVariant = mdConfiguration.getScriptVariant();
    if (scriptVariant == ScriptVariant.UNKNOWN) {
      // Не удалось определить язык встроенного языка конфигурации —
      // мягкий фолбэк на UI-язык LS (бросать нельзя: метод дёргается в
      // hot-path completion/hover).
      return getServerContext().getLanguageServerConfiguration().getLanguage();
    }
    var shortName = scriptVariant.shortName();
    return "en".equalsIgnoreCase(shortName)
      ? Language.EN
      : Language.RU;
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
    return mdObject.getOrCompute();
  }

  /**
   * Возвращает строковое представление ссылки связанного с объектом объекта метаданных 1С либо строку URI для
   * остальных случаев
   *
   * @return Строковое представление ссылки
   */
  public String getMdoRef() {
    return mdoRef.getOrCompute();
  }

  public List<SDBLTokenizer> getQueries() {
    return queries.getOrCompute();
  }

  public List<Diagnostic> getDiagnostics() {
    return diagnostics.getOrCompute();
  }

  /**
   * Сбрасывает закэшированные диагностики. Нужно, когда внешний триггер
   * (например, регистрация конфигурационных типов) меняет результат
   * вычисления диагностик, а содержимое документа не поменялось.
   */
  public void clearDiagnostics() {
    diagnosticsLock.lock();
    try {
      diagnostics.clear();
    } finally {
      diagnosticsLock.unlock();
    }
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

  protected void rebuildFromFileSystem() {
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
      moduleType.clear();
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
    var fromConfiguration = context.getConfiguration().getModuleTypeByURI(uri);
    if (fromConfiguration != ModuleType.UNKNOWN) {
      return fromConfiguration;
    }
    return oScriptModuleTypeResolver.resolve(uri).orElse(fromConfiguration);
  }

  private Optional<MD> computeMdObject() {
    return getServerContext().getConfiguration().findChild(getUri());
  }

  private String computeMdoRef() {
    return MdoRefBuilder.getMdoRef(this);
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
      lines = tokensUnboxed.getLast().getLine();
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
