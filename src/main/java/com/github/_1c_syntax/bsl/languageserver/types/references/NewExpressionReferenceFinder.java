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
package com.github._1c_syntax.bsl.languageserver.types.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.symbol.ConstructorCallSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Finder для имени класса в выражении {@code Новый <ИмяКласса>(...)}.
 *
 * <p>Возвращает {@link Reference} с {@link ConstructorCallSymbol}, который
 * несёт имя типа, ссылку на тип, фактическую арность и полный список
 * конструкторов — этого достаточно для рендеринга hover/signature-help
 * без обращения consumer'ов в {@link TypeService}.
 */
@Component
@Order(150)
@RequiredArgsConstructor
public class NewExpressionReferenceFinder implements ReferenceFinder {

  private final ServerContextProvider serverContextProvider;
  private final TypeService typeService;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    // NoLock-вариант: reference-резолв вызывается из inferencer-горячих
    // путей (visitAssignment у диагностик, hover, completion). Захват
    // per-document RWLock конкурирует с populateContext'ом, что
    // выливается в парк worker-потоков под fair-RWLock.
    return serverContextProvider.getDocumentUnsafeNoLock(uri)
      .flatMap(document -> findReference(document, uri, position));
  }

  private Optional<Reference> findReference(DocumentContext document, URI uri, Position position) {
    BSLParser.FileContext ast;
    try {
      ast = document.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }
    var nex = findInnermostNewExpression(ast, position);
    if (nex.isEmpty()) {
      return Optional.empty();
    }
    var typeNameCtx = nex.get().typeName();
    if (typeNameCtx == null) {
      return Optional.empty();
    }
    if (!encloses(typeNameCtx, position)) {
      return Optional.empty();
    }
    var typeName = typeNameCtx.getText();
    var fileType = document.getFileType();
    var refOpt = typeService.resolve(typeName, fileType);
    if (refOpt.isEmpty()) {
      return Optional.empty();
    }
    var ref = refOpt.get();
    var ctors = typeService.getConstructors(ref, fileType);
    int argCount = countNewExpressionArgs(nex.get());
    var range = tokenRange(typeNameCtx);
    return Optional.of(new Reference(
      document.getSymbolTree().getModule(),
      new ConstructorCallSymbol(typeName, ref, argCount, ctors, typeService.getDescription(ref, fileType)),
      uri,
      range,
      OccurrenceType.REFERENCE
    ));
  }

  private static int countNewExpressionArgs(BSLParser.NewExpressionContext nex) {
    var doCall = nex.doCall();
    if (doCall == null) {
      return 0;
    }
    var list = doCall.callParamList();
    if (list == null) {
      return 0;
    }
    var ps = list.callParam();
    if (ps == null || ps.isEmpty()) {
      return 0;
    }
    int n = ps.size();
    var last = ps.get(n - 1);
    if (last.getChildCount() == 0) {
      n--;
    }
    return n;
  }

  private static Optional<BSLParser.NewExpressionContext> findInnermostNewExpression(
    ParseTree node, Position position
  ) {
    BSLParser.NewExpressionContext best = null;
    if (node instanceof BSLParser.NewExpressionContext nex && encloses(nex, position)) {
      best = nex;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      if (child instanceof ParserRuleContext prc && !encloses(prc, position)) {
        continue;
      }
      var inner = findInnermostNewExpression(child, position);
      if (inner.isPresent()) {
        best = inner.get();
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean encloses(ParserRuleContext ctx, Position position) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    if (start == null || stop == null) {
      return false;
    }
    var range = Ranges.create(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
    return Ranges.containsPosition(range, position);
  }

  private static Range tokenRange(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    return Ranges.create(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
  }
}
