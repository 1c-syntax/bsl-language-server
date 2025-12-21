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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParserBaseVisitor;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Сапплаер семантических токенов для запросов SDBL (язык запросов 1С).
 * <p>
 * Обрабатывает токены запросов и разделяет строки BSL, содержащие запросы.
 */
@Component
@RequiredArgsConstructor
public class QuerySemanticTokensSupplier implements SemanticTokensSupplier {

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL
  );

  // SDBL (Query Language) token types
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

  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var queries = documentContext.getQueries();

    if (queries.isEmpty()) {
      return entries;
    }

    // Collect all SDBL tokens grouped by line
    var sdblTokensByLine = new HashMap<Integer, List<Token>>();
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        int zeroIndexedLine = token.getLine() - 1;
        sdblTokensByLine.computeIfAbsent(zeroIndexedLine, k -> new ArrayList<>()).add(token);
      }
    }

    if (sdblTokensByLine.isEmpty()) {
      return entries;
    }

    // Find and split BSL strings that contain SDBL tokens
    var stringsToSkip = collectStringsWithQueries(documentContext, sdblTokensByLine);
    addSplitStringTokens(entries, stringsToSkip, sdblTokensByLine);

    // Add all SDBL tokens
    for (var query : queries) {
      for (Token token : query.getTokens()) {
        if (token.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        addSdblToken(entries, token);
      }
    }

    // Add AST-based semantic tokens (aliases, field names, metadata names, etc.)
    for (var query : queries) {
      var visitor = new SdblSemanticTokensVisitor(helper, entries);
      visitor.visit(query.getAst());
    }

    return entries;
  }

  private Set<Token> collectStringsWithQueries(DocumentContext documentContext, HashMap<Integer, List<Token>> sdblTokensByLine) {
    var stringsToSkip = new HashSet<Token>();
    var bslStringTokens = documentContext.getTokensFromDefaultChannel().stream()
      .filter(token -> STRING_TYPES.contains(token.getType()))
      .toList();

    for (Token bslString : bslStringTokens) {
      var stringRange = Ranges.create(bslString);
      int stringLine = stringRange.getStart().getLine();

      var sdblTokensOnLine = sdblTokensByLine.get(stringLine);
      if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
        continue;
      }

      var hasOverlappingTokens = sdblTokensOnLine.stream()
        .anyMatch(sdblToken -> {
          var sdblRange = Ranges.create(sdblToken);
          return Ranges.containsRange(stringRange, sdblRange);
        });

      if (hasOverlappingTokens) {
        stringsToSkip.add(bslString);
      }
    }

    return stringsToSkip;
  }

  private void addSplitStringTokens(
    List<SemanticTokenEntry> entries,
    Set<Token> stringsToSkip,
    HashMap<Integer, List<Token>> sdblTokensByLine
  ) {
    int stringTypeIdx = helper.getTypeIndex(SemanticTokenTypes.String);
    if (stringTypeIdx < 0) {
      return;
    }

    for (Token stringToken : stringsToSkip) {
      var stringRange = Ranges.create(stringToken);
      int stringLine = stringRange.getStart().getLine();

      var sdblTokensOnLine = sdblTokensByLine.get(stringLine);
      if (sdblTokensOnLine == null || sdblTokensOnLine.isEmpty()) {
        continue;
      }

      int stringStart = stringRange.getStart().getCharacter();
      int stringEnd = stringRange.getEnd().getCharacter();

      var overlappingTokens = sdblTokensOnLine.stream()
        .filter(sdblToken -> {
          int sdblStart = sdblToken.getCharPositionInLine();
          int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();
          return sdblStart >= stringStart && sdblEnd <= stringEnd;
        })
        .sorted(Comparator.comparingInt(Token::getCharPositionInLine))
        .toList();

      if (overlappingTokens.isEmpty()) {
        continue;
      }

      // Split the STRING token around SDBL tokens
      int currentPos = stringStart;

      for (Token sdblToken : overlappingTokens) {
        int sdblStart = sdblToken.getCharPositionInLine();
        int sdblEnd = sdblStart + (int) sdblToken.getText().codePoints().count();

        // Add string part before SDBL token
        if (currentPos < sdblStart) {
          entries.add(new SemanticTokenEntry(
            stringLine,
            currentPos,
            sdblStart - currentPos,
            stringTypeIdx,
            0
          ));
        }

        currentPos = sdblEnd;
      }

      // Add final string part after last SDBL token
      if (currentPos < stringEnd) {
        entries.add(new SemanticTokenEntry(
          stringLine,
          currentPos,
          stringEnd - currentPos,
          stringTypeIdx,
          0
        ));
      }
    }
  }

  private void addSdblToken(List<SemanticTokenEntry> entries, Token token) {
    var tokenType = token.getType();
    var semanticTypeAndModifiers = getSdblTokenTypeAndModifiers(tokenType);
    if (semanticTypeAndModifiers != null) {
      // ANTLR uses 1-indexed line numbers, convert to 0-indexed for LSP Range
      int zeroIndexedLine = token.getLine() - 1;
      int start = token.getCharPositionInLine();
      int length = (int) token.getText().codePoints().count();
      var range = new Range(
        new Position(zeroIndexedLine, start),
        new Position(zeroIndexedLine, start + length)
      );
      helper.addRange(entries, range, semanticTypeAndModifiers.type, semanticTypeAndModifiers.modifiers);
    }
  }

  @Nullable
  private SdblTokenTypeAndModifiers getSdblTokenTypeAndModifiers(int tokenType) {
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

  private record SdblTokenTypeAndModifiers(String type, String[] modifiers) {
  }

  /**
   * Visitor for SDBL AST to add semantic tokens based on context.
   */
  private static class SdblSemanticTokensVisitor extends SDBLParserBaseVisitor<Void> {
    private final SemanticTokensHelper helper;
    private final List<SemanticTokenEntry> entries;

    public SdblSemanticTokensVisitor(SemanticTokensHelper helper, List<SemanticTokenEntry> entries) {
      this.helper = helper;
      this.entries = entries;
    }

    @Override
    public Void visitQuery(SDBLParser.QueryContext ctx) {
      var temporaryTableName = ctx.temporaryTableName;
      if (temporaryTableName != null) {
        helper.addContextRange(entries, temporaryTableName, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration);
      }
      return super.visitQuery(ctx);
    }

    @Override
    public Void visitDataSource(SDBLParser.DataSourceContext ctx) {
      var alias = ctx.alias();
      if (alias != null && alias.identifier() != null) {
        helper.addTokenRange(entries, alias.identifier().getStart(), SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration);
      }
      return super.visitDataSource(ctx);
    }

    @Override
    public Void visitSelectedField(SDBLParser.SelectedFieldContext ctx) {
      var alias = ctx.alias();
      if (alias != null && alias.identifier() != null) {
        helper.addTokenRange(entries, alias.identifier().getStart(), SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration);
      }
      return super.visitSelectedField(ctx);
    }

    @Override
    public Void visitMdo(SDBLParser.MdoContext ctx) {
      var tableName = ctx.tableName;
      if (tableName != null) {
        helper.addTokenRange(entries, tableName.getStart(), SemanticTokenTypes.Class);
      }
      return super.visitMdo(ctx);
    }

    @Override
    public Void visitVirtualTable(SDBLParser.VirtualTableContext ctx) {
      var virtualTableNameToken = ctx.virtualTableName;
      if (virtualTableNameToken != null) {
        helper.addTokenRange(entries, virtualTableNameToken, SemanticTokenTypes.Method);
      }
      return super.visitVirtualTable(ctx);
    }

    @Override
    public Void visitTable(SDBLParser.TableContext ctx) {
      var tableName = ctx.tableName;
      if (tableName != null) {
        helper.addTokenRange(entries, tableName.getStart(), SemanticTokenTypes.Variable);
      }

      var objectTableName = ctx.objectTableName;
      if (objectTableName != null) {
        helper.addTokenRange(entries, objectTableName.getStart(), SemanticTokenTypes.Class);
      }

      return super.visitTable(ctx);
    }

    @Override
    public Void visitColumn(SDBLParser.ColumnContext ctx) {
      var identifiers = ctx.identifier();
      if (identifiers != null && !identifiers.isEmpty()) {
        if (identifiers.size() == 1) {
          helper.addTokenRange(entries, identifiers.get(0).getStart(), SemanticTokenTypes.Variable);
        } else {
          helper.addTokenRange(entries, identifiers.get(0).getStart(), SemanticTokenTypes.Variable);
          helper.addTokenRange(entries, identifiers.get(identifiers.size() - 1).getStart(), SemanticTokenTypes.Property);
        }
      }
      return super.visitColumn(ctx);
    }

    @Override
    public Void visitParameter(SDBLParser.ParameterContext ctx) {
      var ampersand = ctx.AMPERSAND();
      var parameterName = ctx.name;
      if (ampersand != null && parameterName != null) {
        helper.addContextRange(entries, ctx, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly);
      }
      return super.visitParameter(ctx);
    }

    @Override
    public Void visitValueFunction(SDBLParser.ValueFunctionContext ctx) {
      var type = ctx.type;
      var mdoName = ctx.mdoName;
      var predefinedName = ctx.predefinedName;
      var emptyRef = ctx.emptyFer;
      var systemName = ctx.systemName;

      if (type != null && mdoName != null) {
        if (type.getType() == SDBLLexer.ENUM_TYPE) {
          helper.addTokenRange(entries, mdoName.getStart(), SemanticTokenTypes.Enum);
        } else {
          helper.addTokenRange(entries, mdoName.getStart(), SemanticTokenTypes.Class);
        }

        if (predefinedName != null) {
          helper.addTokenRange(entries, predefinedName.getStart(), SemanticTokenTypes.EnumMember);
        } else if (emptyRef != null) {
          helper.addTokenRange(entries, emptyRef, SemanticTokenTypes.EnumMember);
        }
      } else if (systemName != null && predefinedName != null) {
        helper.addTokenRange(entries, systemName.getStart(), SemanticTokenTypes.Enum);
        helper.addTokenRange(entries, predefinedName.getStart(), SemanticTokenTypes.EnumMember);
      }

      var routePointName = ctx.routePointName;
      if (routePointName != null) {
        helper.addTokenRange(entries, routePointName.getStart(), SemanticTokenTypes.EnumMember);
      }

      return super.visitValueFunction(ctx);
    }
  }

  // SDBL token type factory methods
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

