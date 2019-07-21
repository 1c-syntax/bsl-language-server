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
package org.github._1c_syntax.bsl.languageserver.context;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.Trees;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbolComputer;
import org.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import org.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbolComputer;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.github._1c_syntax.bsl.parser.UnicodeBOMInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.antlr.v4.runtime.Token.DEFAULT_CHANNEL;
import static org.antlr.v4.runtime.Token.EOF;

public class DocumentContext {

  private String content;
  private String[] contentList;
  private BSLParser.FileContext ast;
  private List<Token> tokens;
  private MetricStorage metrics;
  private List<MethodSymbol> methods;
  private Map<BSLParserRuleContext, MethodSymbol> nodeToMethodsMap = new HashMap<>();
  private List<RegionSymbol> regions;
  private List<RegionSymbol> regionsFlat;
  private final String uri;
  private final FileType fileType;

  public DocumentContext(String uri, String content) {
    this.uri = uri;
    FileType fileTypeFromUri;

    if (uri == null) {
      fileTypeFromUri = FileType.BSL;
    } else {
      try {
        fileTypeFromUri = FileType.valueOf(FilenameUtils.getExtension(uri).toUpperCase(Locale.ENGLISH));
      } catch (IllegalArgumentException e) {
        fileTypeFromUri = FileType.BSL;
      }
    }
    this.fileType = fileTypeFromUri;

    build(content);
  }

  public BSLParser.FileContext getAst() {
    return ast;
  }

  public List<MethodSymbol> getMethods() {
    return new ArrayList<>(methods);
  }

  public Optional<MethodSymbol> getMethodSymbol(BSLParserRuleContext ctx) {
    return Optional.ofNullable(nodeToMethodsMap.get(ctx));
  }

  public List<RegionSymbol> getRegions() {
    return new ArrayList<>(regions);
  }

  public List<RegionSymbol> getRegionsFlat() {
    return new ArrayList<>(regionsFlat);
  }

  public List<Token> getTokens() {
    return new ArrayList<>(tokens);
  }

  public List<Token> getTokensFromDefaultChannel() {
    return tokens.stream().filter(token -> token.getChannel() == DEFAULT_CHANNEL).collect(Collectors.toList());
  }

  public List<Token> getComments() {
    return tokens.stream()
      .filter(token -> token.getType() == BSLLexer.LINE_COMMENT)
      .collect(Collectors.toList());
  }

  public String getText(Range range) {
    Position start = range.getStart();
    Position end = range.getEnd();

    StringBuilder sb = new StringBuilder();

    String startString = contentList[start.getLine()];
    if (start.getLine() == end.getLine()) {
      sb.append(startString, start.getCharacter(), end.getCharacter());
    } else {
      sb.append(startString.substring(start.getCharacter()));
    }

    for(int i = start.getLine() + 1; i <= end.getLine() - 1; i++) {
      sb.append(contentList[i]);
    }

    if (start.getLine() != end.getLine()) {
      sb.append(contentList[end.getLine()], 0, end.getCharacter());
    }

    return sb.toString();
  }

  public MetricStorage getMetrics() {
    return metrics;
  }

  public String getUri() {
    return uri;
  }

  public FileType getFileType() {
    return fileType;
  }

  public void rebuild(String content) {
    build(content);
  }

  private void build(String content) {
    this.content = content;
    this.contentList = content.split("\n");

    // order of computing is important
    computeTokensAndAST();

    computeRegions();
    computeMethods();
    adjustRegions();

    computeMetrics();
  }

  private void computeTokensAndAST() {
    CharStream input;

    try (InputStream inputStream = IOUtils.toInputStream(content, StandardCharsets.UTF_8);
         UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(inputStream)
    ) {

      ubis.skipBOM();

      input = CharStreams.fromStream(ubis, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BSLLexer lexer = new BSLLexer(input);

    lexer.setInputStream(input);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);

    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    tokenStream.fill();

    tokens = new ArrayList<>(tokenStream.getTokens());

    Token lastToken = tokens.get(tokens.size() - 1);
    if (lastToken.getType() == EOF) {
      tokens.remove(tokens.size() - 1);
    }

    BSLParser parser = new BSLParser(tokenStream);
    parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
    ast = parser.file();
  }

  private void computeRegions() {
    RegionSymbolComputer regionSymbolComputer = new RegionSymbolComputer(ast);
    regions = regionSymbolComputer.getRegions();
    regionsFlat = regions.stream()
      .map((RegionSymbol regionSymbol) -> {
        List<RegionSymbol> list = new ArrayList<>();
        list.add(regionSymbol);
        list.addAll(regionSymbol.getChildren());

        return list;
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private void computeMethods() {
    MethodSymbolComputer methodSymbolComputer = new MethodSymbolComputer(this);
    methods = methodSymbolComputer.getMethods();

    nodeToMethodsMap.clear();
    methods.forEach(methodSymbol -> nodeToMethodsMap.put(methodSymbol.getNode(), methodSymbol));
  }

  private void adjustRegions() {
    methods.forEach((MethodSymbol methodSymbol) -> {
      RegionSymbol region = methodSymbol.getRegion();
      if (region != null) {
        region.getMethods().add(methodSymbol);
      }
    });
  }

  private void computeMetrics() {
    metrics = new MetricStorage();
    metrics.setFunctions(Math.toIntExact(methods.stream().filter(MethodSymbol::isFunction).count()));
    metrics.setProcedures(methods.size() - metrics.getFunctions());

    int ncloc = (int) getTokensFromDefaultChannel().stream()
      .map(Token::getLine)
      .distinct()
      .count();

    metrics.setNcloc(ncloc);

    int[] nclocData = getTokensFromDefaultChannel().stream()
      .mapToInt(Token::getLine)
      .distinct().toArray();
    metrics.setNclocData(nclocData);

    int lines;
    if (tokens.isEmpty()) {
      lines = 0;
    } else {
      lines = tokens.get(tokens.size() - 1).getLine();
    }
    metrics.setLines(lines);

    int statements = Trees.findAllRuleNodes(ast, BSLParser.RULE_statement).size();
    metrics.setStatements(statements);
  }

}
