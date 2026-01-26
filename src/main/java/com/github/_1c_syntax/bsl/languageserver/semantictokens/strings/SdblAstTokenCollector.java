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
package com.github._1c_syntax.bsl.languageserver.semantictokens.strings;

import com.github._1c_syntax.bsl.parser.SDBLLexer;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;

import java.util.Map;
import java.util.Set;

/**
 * Visitor для сбора AST-based переопределений токенов SDBL.
 * <p>
 * Собирает информацию о типах токенов на основе контекста AST,
 * что позволяет более точно определить семантику токенов
 * (алиасы, имена таблиц, колонки, параметры и т.д.).
 */
public class SdblAstTokenCollector extends SDBLParserBaseVisitor<Void> {

  private static final String[] NO_MODIFIERS = new String[0];
  private static final String[] DECLARATION_MODIFIER = new String[]{SemanticTokenModifiers.Declaration};
  private static final String[] READONLY_MODIFIER = new String[]{SemanticTokenModifiers.Readonly};

  private final Map<TokenPosition, AstTokenInfo> astTokenOverrides;
  private final Set<TokenPosition> skipPositions;

  /**
   * Создаёт коллектор AST-токенов.
   *
   * @param astTokenOverrides Map для заполнения переопределениями типов токенов
   * @param skipPositions     Set позиций токенов для пропуска при обработке
   */
  public SdblAstTokenCollector(Map<TokenPosition, AstTokenInfo> astTokenOverrides, Set<TokenPosition> skipPositions) {
    this.astTokenOverrides = astTokenOverrides;
    this.skipPositions = skipPositions;
  }

  private void addTokenOverride(Token token, String type, String[] modifiers) {
    int line = token.getLine() - 1;
    int start = token.getCharPositionInLine();
    int length = (int) token.getText().codePoints().count();
    astTokenOverrides.put(new TokenPosition(line, start, length), new AstTokenInfo(type, modifiers));
  }

  @Override
  public Void visitQuery(SDBLParser.QueryContext ctx) {
    var temporaryTableName = ctx.temporaryTableName;
    if (temporaryTableName != null) {
      addContextTokens(temporaryTableName, SemanticTokenTypes.Variable, DECLARATION_MODIFIER);
    }
    return super.visitQuery(ctx);
  }

  private void addContextTokens(ParserRuleContext ctx, String type, String[] modifiers) {
    addTokenOverride(ctx.getStart(), type, modifiers);
  }

  @Override
  public Void visitDataSource(SDBLParser.DataSourceContext ctx) {
    var alias = ctx.alias();
    if (alias != null && alias.identifier() != null) {
      addTokenOverride(alias.identifier().getStart(), SemanticTokenTypes.Variable, DECLARATION_MODIFIER);
    }
    return super.visitDataSource(ctx);
  }

  @Override
  public Void visitSelectedField(SDBLParser.SelectedFieldContext ctx) {
    var alias = ctx.alias();
    if (alias != null && alias.identifier() != null) {
      addTokenOverride(alias.identifier().getStart(), SemanticTokenTypes.Variable, DECLARATION_MODIFIER);
    }
    return super.visitSelectedField(ctx);
  }

  @Override
  public Void visitMdo(SDBLParser.MdoContext ctx) {
    var tableName = ctx.tableName;
    if (tableName != null) {
      addTokenOverride(tableName.getStart(), SemanticTokenTypes.Class, NO_MODIFIERS);
    }
    return super.visitMdo(ctx);
  }

  @Override
  public Void visitVirtualTable(SDBLParser.VirtualTableContext ctx) {
    var virtualTableNameToken = ctx.virtualTableName;
    if (virtualTableNameToken != null) {
      addTokenOverride(virtualTableNameToken, SemanticTokenTypes.Method, NO_MODIFIERS);
    }
    return super.visitVirtualTable(ctx);
  }

  @Override
  public Void visitTable(SDBLParser.TableContext ctx) {
    var tableName = ctx.tableName;
    if (tableName != null) {
      addTokenOverride(tableName.getStart(), SemanticTokenTypes.Variable, NO_MODIFIERS);
    }

    var objectTableName = ctx.objectTableName;
    if (objectTableName != null) {
      addTokenOverride(objectTableName.getStart(), SemanticTokenTypes.Class, NO_MODIFIERS);
    }

    return super.visitTable(ctx);
  }

  @Override
  public Void visitColumn(SDBLParser.ColumnContext ctx) {
    var identifiers = ctx.identifier();
    if (identifiers != null && !identifiers.isEmpty()) {
      if (identifiers.size() == 1) {
        addTokenOverride(identifiers.get(0).getStart(), SemanticTokenTypes.Variable, NO_MODIFIERS);
      } else {
        addTokenOverride(identifiers.get(0).getStart(), SemanticTokenTypes.Variable, NO_MODIFIERS);
        addTokenOverride(identifiers.get(identifiers.size() - 1).getStart(), SemanticTokenTypes.Property, NO_MODIFIERS);
      }
    }
    return super.visitColumn(ctx);
  }

  @Override
  public Void visitParameter(SDBLParser.ParameterContext ctx) {
    var ampersand = ctx.AMPERSAND();
    var parameterName = ctx.name;
    if (ampersand != null && parameterName != null) {
      // Для параметра добавляем override по токену &
      // с увеличенной длиной, охватывающей весь параметр (& + имя)
      var ampersandToken = ampersand.getSymbol();
      int line = ampersandToken.getLine() - 1;
      int start = ampersandToken.getCharPositionInLine();
      int ampersandLength = (int) ampersandToken.getText().codePoints().count();
      int nameLength = (int) parameterName.getText().codePoints().count();
      int totalLength = ampersandLength + nameLength;

      // Добавляем override для токена & с увеличенной длиной (весь параметр)
      astTokenOverrides.put(new TokenPosition(line, start, ampersandLength),
        new AstTokenInfo(SemanticTokenTypes.Parameter, READONLY_MODIFIER, totalLength));

      // Добавляем позицию токена-имени параметра в skipPositions, чтобы его не обрабатывать отдельно
      skipPositions.add(new TokenPosition(line, start + ampersandLength, nameLength));
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
        addTokenOverride(mdoName.getStart(), SemanticTokenTypes.Enum, NO_MODIFIERS);
      } else {
        addTokenOverride(mdoName.getStart(), SemanticTokenTypes.Class, NO_MODIFIERS);
      }

      if (predefinedName != null) {
        addTokenOverride(predefinedName.getStart(), SemanticTokenTypes.EnumMember, NO_MODIFIERS);
      } else if (emptyRef != null) {
        addTokenOverride(emptyRef, SemanticTokenTypes.EnumMember, NO_MODIFIERS);
      }
    } else if (systemName != null && predefinedName != null) {
      addTokenOverride(systemName.getStart(), SemanticTokenTypes.Enum, NO_MODIFIERS);
      addTokenOverride(predefinedName.getStart(), SemanticTokenTypes.EnumMember, NO_MODIFIERS);
    }

    var routePointName = ctx.routePointName;
    if (routePointName != null) {
      addTokenOverride(routePointName.getStart(), SemanticTokenTypes.EnumMember, NO_MODIFIERS);
    }

    return super.visitValueFunction(ctx);
  }
}

