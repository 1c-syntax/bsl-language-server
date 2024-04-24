/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.bsl.Constructors;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class TypeResolver {

  private final ServerContext serverContext;
  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  // TODO: Create LRU cache for calculated types.

  // TODO: Use reference instead of symbol. Refactor hover provider to pass references to markup content builders.
  public List<Type> findTypes(SourceDefinedSymbol symbol) {
    return calculateTypes(symbol);
  }

  public List<Type> findTypes(URI uri, Position position) {
    return referenceResolver.findReference(uri, position)
      .stream()
      .flatMap(reference -> calculateTypes(uri, reference).stream())
      .distinct()
      .toList();
  }

  private List<Type> calculateTypes(SourceDefinedSymbol symbol) {

    // variable description resolver
    if (symbol instanceof Describable describableSymbol) {
      var maybeDescription = describableSymbol.getDescription();
      if (maybeDescription.isPresent()) {
        var description = maybeDescription.get();
        if (description instanceof VariableDescription variableDescription) {
          // TODO: use new type information from new bsp-parser
          var purposeDescription = variableDescription.getPurposeDescription();
          var typeName = Pattern.compile("^(\\S+)").matcher(purposeDescription).results()
            .findFirst()
            .map(MatchResult::group)
            .orElse("");

          if (!typeName.isEmpty()) {
            return List.of(new Type(typeName));
          }
        }
      }
    }

    // reference-based type resolver
    var uri = symbol.getOwner().getUri();
    var ast = symbol.getOwner().getAst();
    if (ast == null) {
      return Collections.emptyList();
    }

    var position = symbol.getSelectionRange().getStart();

    var typesOfCurrentReference = calculateTypes(uri, ast, position);

    var typesOfOtherReferences = referenceIndex.getReferencesTo(symbol).stream()
      .filter(referenceTo -> referenceTo.getOccurrenceType() == OccurrenceType.DEFINITION)
      .map(referenceTo -> calculateTypes(uri, ast, referenceTo.getSelectionRange().getStart()))
      .flatMap(Collection::stream)
      .toList();

    return Stream.concat(typesOfCurrentReference.stream(), typesOfOtherReferences.stream())
      .distinct()
      .toList();
  }

  private List<Type> calculateTypes(URI uri, Reference reference) {

    // source defined symbol resolver
    if (reference.isSourceDefinedSymbolReference()) {
      return calculateTypes(reference.getSourceDefinedSymbol().orElseThrow());
    }

    // expression tree resolver
    if (reference.getOccurrenceType() == OccurrenceType.DEFINITION) {
      var document = serverContext.getDocument(uri);
      var ast = document.getAst();
      if (ast == null) {
        return Collections.emptyList();
      }
      var position = reference.getSelectionRange().getStart();
      return calculateTypes(uri, ast, position);
    }

    // no-op
    return Collections.emptyList();
  }

  private List<Type> calculateTypes(URI uri, BSLParser.FileContext ast, Position position) {
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(TerminalNode::getParent)
      .map(ruleNode -> Trees.getRootParent(ruleNode, BSLParser.RULE_assignment))
      .map(BSLParser.AssignmentContext.class::cast)
      .map(BSLParser.AssignmentContext::expression)
      .map(expression -> calculateTypes(uri, expression))
      .orElseGet(Collections::emptyList);
  }

  private List<Type> calculateTypes(URI uri, BSLParser.ExpressionContext expression) {

    // only simple cases for now. Use ExpressionTree in the future.
    if (!expression.operation().isEmpty()) {
      return Collections.emptyList();
    }

    // new-resolver
    var typeName = newTypeName(expression);
    if (!typeName.isEmpty()) {
      Type type = new Type(typeName);
      return List.of(type);
    }

    // globalMethodCall resolver
    var typeNames = returnedValue(uri, expression);
    if (!typeNames.isEmpty()) {
      return typeNames;
    }

    // const-value resolver
    var constValueContext = expression.member(0).constValue();
    if (constValueContext == null) {
      return Collections.emptyList();
    }

    Type type = null;
    if (constValueContext.DATETIME() != null) {
      type = new Type("Дата");
    } else if (constValueContext.FALSE() != null || constValueContext.TRUE() != null) {
      type = new Type("Булево");
    } else if (constValueContext.NULL() != null) {
      type = new Type("null");
    } else if (constValueContext.numeric() != null) {
      type = new Type("Число");
    } else if (constValueContext.string() != null) {
      type = new Type("Строка");
    } else if (constValueContext.UNDEFINED() != null) {
      type = new Type("Неопределено");
    }

    if (type != null) {
      return List.of(type);
    }

    return Collections.emptyList();

  }

  private String newTypeName(BSLParser.ExpressionContext expression) {
    var typeName = "";
    var newCtx = Trees.getNextNode(expression, expression, BSLParser.RULE_newExpression);
    if (newCtx instanceof BSLParser.NewExpressionContext newExpression) {
      typeName = Constructors.typeName(newExpression).orElse("");
    }
    return typeName;
  }

  private List<Type> returnedValue(URI uri, BSLParser.ExpressionContext expression) {
    var complexIdentifier = expression.member(0).complexIdentifier();

    if (complexIdentifier == null) {
      return Collections.emptyList();
    }

    if (!complexIdentifier.modifier().isEmpty()) {
      return Collections.emptyList();
    }

    var globalMethodCall = complexIdentifier.globalMethodCall();

    if (globalMethodCall == null) {
      return Collections.emptyList();
    }

    var calledMethod = referenceResolver.findReference(uri, Ranges.create(globalMethodCall.methodName()).getStart());

    return calledMethod.filter(Reference::isSourceDefinedSymbolReference)
      .flatMap(Reference::getSourceDefinedSymbol)
      .filter(Describable.class::isInstance)
      .map(Describable.class::cast)
      .flatMap(Describable::getDescription)
      .filter(MethodDescription.class::isInstance)
      .map(MethodDescription.class::cast)
      .map(MethodDescription::getReturnedValue)
      .stream()
      .flatMap(List::stream)
      .map(TypeDescription::getName)
      .map(Type::new)
      .toList();

  }

}
