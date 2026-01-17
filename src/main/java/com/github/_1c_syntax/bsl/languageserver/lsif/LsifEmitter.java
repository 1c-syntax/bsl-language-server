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
package com.github._1c_syntax.bsl.languageserver.lsif;

import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.BelongsToEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.ContainsEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.DefinitionEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.DocumentLinkEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.DocumentSymbolEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.FoldingRangeEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.HoverEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.ItemEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.NextEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.edge.ReferencesEdge;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.DefinitionResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.DocumentLinkResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.DocumentSymbolResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.DocumentVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.FoldingRangeResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.HoverResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.MetaDataVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.ProjectVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.RangeVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.ReferenceResultVertex;
import com.github._1c_syntax.bsl.languageserver.lsif.dto.vertex.ResultSetVertex;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.Range;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Генератор LSIF-вывода.
 * <p>
 * Записывает LSIF-элементы (вершины и рёбра) в файл в формате NDJSON.
 */
@Slf4j
public class LsifEmitter implements Closeable {

  private final AtomicLong idGenerator = new AtomicLong(1);
  private final BufferedWriter writer;
  private final ObjectMapper objectMapper;

  public LsifEmitter(Path outputFile) throws IOException {
    this.writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
    this.objectMapper = JsonMapper.builder().build();
  }

  /**
   * Генерирует следующий уникальный ID.
   */
  public long nextId() {
    return idGenerator.getAndIncrement();
  }

  /**
   * Записывает вершину metaData.
   */
  public long emitMetaData(String version, String projectRoot, String toolName, String toolVersion) {
    var id = nextId();
    var vertex = MetaDataVertex.builder()
      .id(id)
      .version(version)
      .projectRoot(projectRoot)
      .positionEncoding("utf-16")
      .toolInfo(MetaDataVertex.ToolInfo.builder()
        .name(toolName)
        .version(toolVersion)
        .build())
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает вершину project.
   */
  public long emitProject(String kind) {
    var id = nextId();
    var vertex = ProjectVertex.builder()
      .id(id)
      .kind(kind)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает вершину document.
   */
  public long emitDocument(String uri, String languageId) {
    var id = nextId();
    var vertex = DocumentVertex.builder()
      .id(id)
      .uri(uri)
      .languageId(languageId)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает вершину range.
   */
  public long emitRange(Range range) {
    var id = nextId();
    var vertex = RangeVertex.builder()
      .id(id)
      .start(RangeVertex.Position.builder()
        .line(range.getStart().getLine())
        .character(range.getStart().getCharacter())
        .build())
      .end(RangeVertex.Position.builder()
        .line(range.getEnd().getLine())
        .character(range.getEnd().getCharacter())
        .build())
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает вершину resultSet.
   */
  public long emitResultSet() {
    var id = nextId();
    var vertex = ResultSetVertex.builder()
      .id(id)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает вершину hoverResult.
   */
  public long emitHoverResult(String content) {
    var id = nextId();
    var vertex = HoverResultVertex.builder()
      .id(id)
      .result(HoverResultVertex.HoverContent.builder()
        .contents(HoverResultVertex.Contents.builder()
          .kind("markdown")
          .value(content)
          .build())
        .build())
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает ребро contains.
   */
  public long emitContains(long outV, List<Long> inVs) {
    var id = nextId();
    var edge = ContainsEdge.builder()
      .id(id)
      .outV(outV)
      .inVs(inVs)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает ребро next.
   */
  public long emitNext(long outV, long inV) {
    var id = nextId();
    var edge = NextEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает ребро textDocument/hover.
   */
  public long emitHoverEdge(long outV, long inV) {
    var id = nextId();
    var edge = HoverEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает ребро belongsTo.
   */
  public long emitBelongsTo(long outV, long inV) {
    var id = nextId();
    var edge = BelongsToEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает вершину definitionResult.
   */
  public long emitDefinitionResult() {
    var id = nextId();
    var vertex = DefinitionResultVertex.builder()
      .id(id)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает ребро textDocument/definition.
   */
  public long emitDefinitionEdge(long outV, long inV) {
    var id = nextId();
    var edge = DefinitionEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает вершину referenceResult.
   */
  public long emitReferenceResult() {
    var id = nextId();
    var vertex = ReferenceResultVertex.builder()
      .id(id)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает ребро textDocument/references.
   */
  public long emitReferencesEdge(long outV, long inV) {
    var id = nextId();
    var edge = ReferencesEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает ребро item.
   */
  public long emitItem(long outV, List<Long> inVs, long document, String property) {
    var id = nextId();
    var edge = ItemEdge.builder()
      .id(id)
      .outV(outV)
      .inVs(inVs)
      .document(document)
      .property(property)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает вершину foldingRangeResult.
   */
  public long emitFoldingRangeResult(List<FoldingRange> foldingRanges) {
    var id = nextId();
    var result = foldingRanges.stream()
      .map(fr -> FoldingRangeResultVertex.FoldingRangeInfo.builder()
        .startLine(fr.getStartLine())
        .startCharacter(fr.getStartCharacter() != null ? fr.getStartCharacter() : 0)
        .endLine(fr.getEndLine())
        .endCharacter(fr.getEndCharacter() != null ? fr.getEndCharacter() : 0)
        .kind(fr.getKind())
        .build())
      .toList();
    var vertex = FoldingRangeResultVertex.builder()
      .id(id)
      .result(result)
      .build();
    emit(vertex);
    return id;
  }

  /**
   * Записывает ребро textDocument/foldingRange.
   */
  public long emitFoldingRangeEdge(long outV, long inV) {
    var id = nextId();
    var edge = FoldingRangeEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает вершину documentSymbolResult.
   */
  public long emitDocumentSymbolResult(List<DocumentSymbol> symbols) {
    var id = nextId();
    var result = symbols.stream()
      .map(this::convertDocumentSymbol)
      .toList();
    var vertex = DocumentSymbolResultVertex.builder()
      .id(id)
      .result(result)
      .build();
    emit(vertex);
    return id;
  }

  private DocumentSymbolResultVertex.DocumentSymbolInfo convertDocumentSymbol(DocumentSymbol symbol) {
    List<DocumentSymbolResultVertex.DocumentSymbolInfo> children = symbol.getChildren() != null
      ? symbol.getChildren().stream().map(this::convertDocumentSymbol).toList()
      : List.of();

    return DocumentSymbolResultVertex.DocumentSymbolInfo.builder()
      .name(symbol.getName())
      .kind(symbol.getKind().getValue())
      .range(convertRange(symbol.getRange()))
      .selectionRange(convertRange(symbol.getSelectionRange()))
      .children(children)
      .build();
  }

  private DocumentSymbolResultVertex.RangeInfo convertRange(Range range) {
    return DocumentSymbolResultVertex.RangeInfo.builder()
      .start(DocumentSymbolResultVertex.PositionInfo.builder()
        .line(range.getStart().getLine())
        .character(range.getStart().getCharacter())
        .build())
      .end(DocumentSymbolResultVertex.PositionInfo.builder()
        .line(range.getEnd().getLine())
        .character(range.getEnd().getCharacter())
        .build())
      .build();
  }

  /**
   * Записывает ребро textDocument/documentSymbol.
   */
  public long emitDocumentSymbolEdge(long outV, long inV) {
    var id = nextId();
    var edge = DocumentSymbolEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  /**
   * Записывает вершину documentLinkResult.
   */
  public long emitDocumentLinkResult(List<DocumentLink> documentLinks) {
    var id = nextId();
    var result = documentLinks.stream()
      .map(dl -> DocumentLinkResultVertex.DocumentLinkInfo.builder()
        .range(convertToDocumentLinkRange(dl.getRange()))
        .target(dl.getTarget())
        .build())
      .toList();
    var vertex = DocumentLinkResultVertex.builder()
      .id(id)
      .result(result)
      .build();
    emit(vertex);
    return id;
  }

  private DocumentLinkResultVertex.RangeInfo convertToDocumentLinkRange(Range range) {
    return DocumentLinkResultVertex.RangeInfo.builder()
      .start(DocumentLinkResultVertex.PositionInfo.builder()
        .line(range.getStart().getLine())
        .character(range.getStart().getCharacter())
        .build())
      .end(DocumentLinkResultVertex.PositionInfo.builder()
        .line(range.getEnd().getLine())
        .character(range.getEnd().getCharacter())
        .build())
      .build();
  }

  /**
   * Записывает ребро textDocument/documentLink.
   */
  public long emitDocumentLinkEdge(long outV, long inV) {
    var id = nextId();
    var edge = DocumentLinkEdge.builder()
      .id(id)
      .outV(outV)
      .inV(inV)
      .build();
    emit(edge);
    return id;
  }

  private void emit(Object element) {
    try {
      writer.write(objectMapper.writeValueAsString(element));
      writer.newLine();
    } catch (IOException e) {
      LOGGER.error("Failed to write LSIF element", e);
      throw new RuntimeException("Failed to write LSIF element", e);
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
