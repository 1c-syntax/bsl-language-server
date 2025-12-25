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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

import com.github._1c_syntax.bsl.parser.SDBLLexer;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Утилитный класс для определения типов токенов SDBL.
 * <p>
 * Содержит наборы токенов для различных категорий (ключевые слова, функции,
 * типы метаданных и т.д.) и методы для определения семантического типа токена.
 */
public final class SdblTokenTypes {

  private static final Set<Integer> SDBL_KEYWORDS = createSdblKeywords();
  private static final Set<Integer> SDBL_FUNCTIONS = createSdblFunctions();
  private static final Set<Integer> SDBL_METADATA_TYPES = createSdblMetadataTypes();
  private static final Set<Integer> SDBL_LITERALS = createSdblLiterals();
  private static final Set<Integer> SDBL_OPERATORS = createSdblOperators();
  private static final Set<Integer> SDBL_STRINGS = Set.of(SDBLLexer.STR);
  private static final Set<Integer> SDBL_COMMENTS = Set.of(SDBLLexer.LINE_COMMENT);
  private static final Set<Integer> SDBL_EDS = Set.of(
    SDBLLexer.EDS_CUBE,
    SDBLLexer.EDS_TABLE,
    SDBLLexer.EDS_CUBE_DIMTABLE
  );
  private static final Set<Integer> SDBL_NUMBERS = Set.of(SDBLLexer.DECIMAL, SDBLLexer.FLOAT);

  private static final String[] NO_MODIFIERS = new String[0];
  private static final String[] DEFAULT_LIBRARY = new String[]{SemanticTokenModifiers.DefaultLibrary};

  private SdblTokenTypes() {
    // Utility class
  }

  /**
   * Тип и модификаторы токена SDBL.
   *
   * @param type      тип семантического токена
   * @param modifiers модификаторы токена
   */
  public record SdblTokenTypeAndModifiers(String type, String[] modifiers) {
  }

  /**
   * Определяет тип и модификаторы для токена SDBL.
   *
   * @param tokenType тип токена из лексера
   * @return тип и модификаторы или null если токен не распознан
   */
  @Nullable
  public static SdblTokenTypeAndModifiers getTokenTypeAndModifiers(int tokenType) {
    if (SDBL_KEYWORDS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Keyword, NO_MODIFIERS);
    } else if (SDBL_FUNCTIONS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Function, DEFAULT_LIBRARY);
    } else if (SDBL_METADATA_TYPES.contains(tokenType) || SDBL_EDS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Namespace, NO_MODIFIERS);
    } else if (SDBL_LITERALS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Keyword, NO_MODIFIERS);
    } else if (SDBL_OPERATORS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Operator, NO_MODIFIERS);
    } else if (SDBL_STRINGS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.String, NO_MODIFIERS);
    } else if (SDBL_COMMENTS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Comment, NO_MODIFIERS);
    } else if (SDBL_NUMBERS.contains(tokenType)) {
      return new SdblTokenTypeAndModifiers(SemanticTokenTypes.Number, NO_MODIFIERS);
    }
    return null;
  }

  private static Set<Integer> createSdblKeywords() {
    return Set.of(
      SDBLLexer.ALL,
      SDBLLexer.ALLOWED,
      SDBLLexer.AND,
      SDBLLexer.AS,
      SDBLLexer.ASC,
      SDBLLexer.AUTOORDER,
      SDBLLexer.BETWEEN,
      SDBLLexer.BY_EN,
      SDBLLexer.CASE,
      SDBLLexer.CAST,
      SDBLLexer.DESC,
      SDBLLexer.DISTINCT,
      SDBLLexer.DROP,
      SDBLLexer.ELSE,
      SDBLLexer.END,
      SDBLLexer.ESCAPE,
      SDBLLexer.FOR,
      SDBLLexer.FROM,
      SDBLLexer.FULL,
      SDBLLexer.GROUP,
      SDBLLexer.HAVING,
      SDBLLexer.HIERARCHY,
      SDBLLexer.HIERARCHY_FOR_IN,
      SDBLLexer.IN,
      SDBLLexer.INDEX,
      SDBLLexer.INNER,
      SDBLLexer.INTO,
      SDBLLexer.IS,
      SDBLLexer.JOIN,
      SDBLLexer.LEFT,
      SDBLLexer.LIKE,
      SDBLLexer.NOT,
      SDBLLexer.OF,
      SDBLLexer.ONLY,
      SDBLLexer.ON_EN,
      SDBLLexer.OR,
      SDBLLexer.ORDER,
      SDBLLexer.OVERALL,
      SDBLLexer.OUTER,
      SDBLLexer.PERIODS,
      SDBLLexer.PO_RU,
      SDBLLexer.REFS,
      SDBLLexer.RIGHT,
      SDBLLexer.SELECT,
      SDBLLexer.SET,
      SDBLLexer.THEN,
      SDBLLexer.TOP,
      SDBLLexer.TOTALS,
      SDBLLexer.UNION,
      SDBLLexer.UPDATE,
      SDBLLexer.WHEN,
      SDBLLexer.WHERE,
      SDBLLexer.EMPTYREF,
      SDBLLexer.GROUPEDBY,
      SDBLLexer.GROUPING
    );
  }

  private static Set<Integer> createSdblFunctions() {
    return Set.of(
      SDBLLexer.AVG,
      SDBLLexer.BEGINOFPERIOD,
      SDBLLexer.BOOLEAN,
      SDBLLexer.COUNT,
      SDBLLexer.DATE,
      SDBLLexer.DATEADD,
      SDBLLexer.DATEDIFF,
      SDBLLexer.DATETIME,
      SDBLLexer.DAY,
      SDBLLexer.DAYOFYEAR,
      SDBLLexer.EMPTYTABLE,
      SDBLLexer.ENDOFPERIOD,
      SDBLLexer.HALFYEAR,
      SDBLLexer.HOUR,
      SDBLLexer.ISNULL,
      SDBLLexer.MAX,
      SDBLLexer.MIN,
      SDBLLexer.MINUTE,
      SDBLLexer.MONTH,
      SDBLLexer.NUMBER,
      SDBLLexer.QUARTER,
      SDBLLexer.PRESENTATION,
      SDBLLexer.RECORDAUTONUMBER,
      SDBLLexer.REFPRESENTATION,
      SDBLLexer.SECOND,
      SDBLLexer.STRING,
      SDBLLexer.SUBSTRING,
      SDBLLexer.SUM,
      SDBLLexer.TENDAYS,
      SDBLLexer.TYPE,
      SDBLLexer.VALUE,
      SDBLLexer.VALUETYPE,
      SDBLLexer.WEEK,
      SDBLLexer.WEEKDAY,
      SDBLLexer.YEAR,
      SDBLLexer.INT,
      SDBLLexer.ACOS,
      SDBLLexer.ASIN,
      SDBLLexer.ATAN,
      SDBLLexer.COS,
      SDBLLexer.SIN,
      SDBLLexer.TAN,
      SDBLLexer.LOG,
      SDBLLexer.LOG10,
      SDBLLexer.EXP,
      SDBLLexer.POW,
      SDBLLexer.SQRT,
      SDBLLexer.LOWER,
      SDBLLexer.STRINGLENGTH,
      SDBLLexer.TRIMALL,
      SDBLLexer.TRIML,
      SDBLLexer.TRIMR,
      SDBLLexer.UPPER,
      SDBLLexer.ROUND,
      SDBLLexer.STOREDDATASIZE,
      SDBLLexer.UUID,
      SDBLLexer.STRFIND,
      SDBLLexer.STRREPLACE
    );
  }

  private static Set<Integer> createSdblMetadataTypes() {
    return Set.of(
      SDBLLexer.ACCOUNTING_REGISTER_TYPE,
      SDBLLexer.ACCUMULATION_REGISTER_TYPE,
      SDBLLexer.BUSINESS_PROCESS_TYPE,
      SDBLLexer.CALCULATION_REGISTER_TYPE,
      SDBLLexer.CATALOG_TYPE,
      SDBLLexer.CHART_OF_ACCOUNTS_TYPE,
      SDBLLexer.CHART_OF_CALCULATION_TYPES_TYPE,
      SDBLLexer.CHART_OF_CHARACTERISTIC_TYPES_TYPE,
      SDBLLexer.CONSTANT_TYPE,
      SDBLLexer.DOCUMENT_TYPE,
      SDBLLexer.DOCUMENT_JOURNAL_TYPE,
      SDBLLexer.ENUM_TYPE,
      SDBLLexer.EXCHANGE_PLAN_TYPE,
      SDBLLexer.EXTERNAL_DATA_SOURCE_TYPE,
      SDBLLexer.FILTER_CRITERION_TYPE,
      SDBLLexer.INFORMATION_REGISTER_TYPE,
      SDBLLexer.SEQUENCE_TYPE,
      SDBLLexer.TASK_TYPE
    );
  }

  private static Set<Integer> createSdblLiterals() {
    return Set.of(
      SDBLLexer.TRUE,
      SDBLLexer.FALSE,
      SDBLLexer.UNDEFINED,
      SDBLLexer.NULL
    );
  }

  private static Set<Integer> createSdblOperators() {
    return Set.of(
      SDBLLexer.SEMICOLON,
      SDBLLexer.DOT,
      SDBLLexer.PLUS,
      SDBLLexer.MINUS,
      SDBLLexer.MUL,
      SDBLLexer.QUOTIENT,
      SDBLLexer.ASSIGN,
      SDBLLexer.LESS_OR_EQUAL,
      SDBLLexer.LESS,
      SDBLLexer.NOT_EQUAL,
      SDBLLexer.GREATER_OR_EQUAL,
      SDBLLexer.GREATER,
      SDBLLexer.COMMA,
      SDBLLexer.BRACE,
      SDBLLexer.BRACE_START,
      SDBLLexer.NUMBER_SIGH
    );
  }
}

