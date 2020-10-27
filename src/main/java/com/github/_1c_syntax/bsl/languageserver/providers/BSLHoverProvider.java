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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.parser.BSLParser.SubNameContext;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.ls_core.context.DocumentContext;
import com.github._1c_syntax.ls_core.providers.HoverProvider;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Primary
public final class BSLHoverProvider implements HoverProvider {

  @Override
  public Optional<Hover> getHover(HoverParams params, DocumentContext documentContext) {

    SubNameFinder finder = new SubNameFinder(params.getPosition());
    finder.visit(documentContext.getAst());

    Token subName = finder.getSubName();
    if (subName == null) {
      return Optional.empty();
    }

    Hover hover = new Hover();
    MarkupContent content = new MarkupContent();
    content.setValue(subName.getText());
    hover.setContents(content);
    hover.setRange(
      new Range(
        new Position(subName.getLine() - 1, subName.getCharPositionInLine()),
        new Position(subName.getLine() - 1, subName.getCharPositionInLine() + subName.getText().length())
      )
    );

    return Optional.of(hover);

  }

  private static final class SubNameFinder extends BSLParserBaseVisitor<ParseTree> {

    private Token subName;
    private final Position position;

    private SubNameFinder(Position position) {
      this.position = position;
    }

    @Override
    public ParseTree visitSubName(SubNameContext ctx) {

      Token token = ctx.start;
      if (token.getLine() == position.getLine() + 1
        && token.getCharPositionInLine() <= position.getCharacter()
        && position.getCharacter() <= token.getCharPositionInLine() + token.getText().length()) {
        subName = token;
      }
      return ctx;
    }

    Token getSubName() {
      return subName;
    }
  }
}
