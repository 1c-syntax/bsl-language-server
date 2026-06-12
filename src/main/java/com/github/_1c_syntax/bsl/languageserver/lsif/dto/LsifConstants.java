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
package com.github._1c_syntax.bsl.languageserver.lsif.dto;

import lombok.experimental.UtilityClass;

/**
 * Константы для LSIF-протокола.
 */
@UtilityClass
public class LsifConstants {

  /**
   * Типы элементов.
   */
  @UtilityClass
  public static class ElementType {
    public static final String VERTEX = "vertex";
    public static final String EDGE = "edge";
  }

  /**
   * Метки вершин.
   */
  @UtilityClass
  public static class VertexLabel {
    public static final String META_DATA = "metaData";
    public static final String PROJECT = "project";
    public static final String DOCUMENT = "document";
    public static final String RANGE = "range";
    public static final String RESULT_SET = "resultSet";
    public static final String HOVER_RESULT = "hoverResult";
    public static final String DEFINITION_RESULT = "definitionResult";
    public static final String REFERENCE_RESULT = "referenceResult";
    public static final String FOLDING_RANGE_RESULT = "foldingRangeResult";
    public static final String DOCUMENT_SYMBOL_RESULT = "documentSymbolResult";
    public static final String DOCUMENT_LINK_RESULT = "documentLinkResult";
    public static final String MONIKER = "moniker";
    public static final String PACKAGE_INFORMATION = "packageInformation";
  }

  /**
   * Метки рёбер.
   */
  @UtilityClass
  public static class EdgeLabel {
    public static final String CONTAINS = "contains";
    public static final String NEXT = "next";
    public static final String BELONGS_TO = "belongsTo";
    public static final String HOVER = "textDocument/hover";
    public static final String DEFINITION = "textDocument/definition";
    public static final String REFERENCES = "textDocument/references";
    public static final String FOLDING_RANGE = "textDocument/foldingRange";
    public static final String DOCUMENT_SYMBOL = "textDocument/documentSymbol";
    public static final String DOCUMENT_LINK = "textDocument/documentLink";
    public static final String MONIKER_EDGE = "moniker";
    public static final String PACKAGE_INFORMATION_EDGE = "packageInformation";
    public static final String ITEM = "item";
  }

  /**
   * Типы моникеров.
   */
  @UtilityClass
  public static class MonikerKind {
    public static final String IMPORT = "import";
    public static final String EXPORT = "export";
    public static final String LOCAL = "local";
  }

  /**
   * Схемы моникеров.
   */
  @UtilityClass
  public static class MonikerScheme {
    public static final String BSL = "bsl";
  }
}
