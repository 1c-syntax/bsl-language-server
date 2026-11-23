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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationPositionRepository.AnnotationPosition;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Собирает диапазоны обращений к аннотациям и параметрам, не удерживая узлы AST в результате. */
final class AnnotationPositionCollector extends BSLParserBaseVisitor<Void> {
  private final List<AnnotationPosition> positions = new ArrayList<>();

  List<AnnotationPosition> collect(ParseTree root) {
    positions.clear();
    visit(root);
    return List.copyOf(positions);
  }

  @Override
  public Void visitAnnotation(BSLParser.AnnotationContext annotation) {
    for (var node : Trees.getDescendants(annotation)) {
      if (node instanceof TerminalNode terminal && !(terminal instanceof ErrorNode)
        && terminal.getParent() instanceof ParserRuleContext parent) {
        var position = positionOf(terminal, parent);
        if (position != null) {
          positions.add(position);
        }
      }
    }
    return null;
  }

  private static @Nullable AnnotationPosition positionOf(TerminalNode terminal, ParserRuleContext parent) {
    if (parent instanceof BSLParser.AnnotationNameContext name
        && name.getParent() instanceof BSLParser.AnnotationContext annotation) {
      return new AnnotationPosition(Ranges.create(terminal), Ranges.create(annotation), name.getText(), null);
    }
    var parameter = parameterOf(parent);
    if (parameter == null) {
      return null;
    }
    var name = parameter.annotationParamName();
    var selection = name == null ? parameter.annotationParamValue() : name;
    if (selection == null) {
      return null;
    }
    var annotation = (BSLParser.AnnotationContext) parameter.getParent().getParent();
    return new AnnotationPosition(Ranges.create(terminal), Ranges.create(selection),
      annotation.annotationName().getText(), name == null ? "Значение" : name.getText());
  }

  /** Имя параметра относится к нему напрямую, а токен значения — к ближайшему значению в дереве. */
  private static BSLParser.@Nullable AnnotationParamContext parameterOf(ParserRuleContext parent) {
    if (parent instanceof BSLParser.AnnotationParamNameContext name) {
      return (BSLParser.AnnotationParamContext) name.getParent();
    }
    var value = parent instanceof BSLParser.AnnotationParamValueContext context
      ? context
      : Trees.<BSLParser.AnnotationParamValueContext>getAncestorByRuleIndex(parent, BSLParser.RULE_annotationParamValue);
    return value == null ? null : (BSLParser.AnnotationParamContext) value.getParent();
  }
}
