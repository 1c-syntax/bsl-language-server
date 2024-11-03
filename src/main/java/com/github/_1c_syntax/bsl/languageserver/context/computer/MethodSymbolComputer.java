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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition.ParameterType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class MethodSymbolComputer
  extends BSLParserBaseVisitor<ParseTree>
  implements Computer<List<MethodSymbol>> {

  private static final Set<Integer> SPECIAL_COMPILER_DIRECTIVES_TOKEN_TYPES = Set.of(
    BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL);

  private final DocumentContext documentContext;
  private final Set<MethodSymbol> methods = new HashSet<>();

  public MethodSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public List<MethodSymbol> compute() {
    methods.clear();
    visitFile(documentContext.getAst());
    return new ArrayList<>(methods);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    BSLParser.FuncDeclarationContext declaration = ctx.funcDeclaration();

    TerminalNode startNode = declaration.FUNCTION_KEYWORD();
    TerminalNode stopNode = ctx.ENDFUNCTION_KEYWORD();

    if (startNode == null
      || startNode instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    if (!declaration.annotation().isEmpty()) {
      startNode = declaration.annotation().get(0).AMPERSAND();
    }

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      declaration.FUNCTION_KEYWORD().getSymbol(),
      declaration.subName().getStart(),
      declaration.paramList(),
      true,
      declaration.EXPORT_KEYWORD() != null,
      getCompilerDirective(declaration.compilerDirective()),
      createAnnotations(declaration.annotation()));

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    TerminalNode startNode = declaration.PROCEDURE_KEYWORD();
    TerminalNode stopNode = ctx.ENDPROCEDURE_KEYWORD();

    if (startNode == null
      || startNode instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    if (!declaration.annotation().isEmpty()) {
      startNode = declaration.annotation().get(0).AMPERSAND();
    }

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      declaration.PROCEDURE_KEYWORD().getSymbol(),
      declaration.subName().getStart(),
      declaration.paramList(),
      false,
      declaration.EXPORT_KEYWORD() != null,
      getCompilerDirective(declaration.compilerDirective()),
      createAnnotations(declaration.annotation())
    );

    methods.add(methodSymbol);

    return ctx;
  }

  // есть определенные предпочтения при использовании &НаКлиентеНаСервереБезКонтекста в модуле упр.формы
  // при ее использовании с другой директивой будет использоваться именно она
  // например, порядок 1
  //&НаКлиентеНаСервереБезКонтекста
  //&НаСервереБезКонтекста
  //показывает Сервер в отладчике и доступен серверный объект ТаблицаЗначений
  // или порядок 2
  //&НаСервереБезКонтекста
  //&НаКлиентеНаСервереБезКонтекста
  //аналогично
  //т.е. порядок этих 2х директив не важен, все равно используется &НаКлиентеНаСервереБезКонтекста.
  // проверял на 8.3.15

  // есть определенные предпочтения при использовании &НаКлиентеНаСервере в модуле команды
  // при ее использовании с другой директивой будет использоваться именно она
  //  проверял на 8.3.15
  //  порядок
  //  1
  //  &НаКлиентеНаСервере
  //  &НаКлиенте
  //  вызывает клиент при вызове метода с клиента
  //  вызывает сервер при вызове метода с сервера
  //  2
  //  &НаКлиенте
  //  &НаКлиентеНаСервере
  //  вызывает клиент при вызове метода с клиента
  //  вызывает сервер при вызове метода с сервера

  private static Optional<CompilerDirectiveKind> getCompilerDirective(
    List<? extends BSLParser.CompilerDirectiveContext> compilerDirectiveContexts
  ) {
    if (compilerDirectiveContexts.isEmpty()) {
      return Optional.empty();
    }
    var tokenType = compilerDirectiveContexts.stream()
      .map(compilerDirectiveContext -> compilerDirectiveContext.getStop().getType())
      .filter(SPECIAL_COMPILER_DIRECTIVES_TOKEN_TYPES::contains)
      .findAny()
      .orElseGet(() -> compilerDirectiveContexts.get(0).getStop().getType());

    return CompilerDirectiveKind.of(tokenType);

  }

  private MethodSymbol createMethodSymbol(
    TerminalNode startNode,
    TerminalNode stopNode,
    Token startOfMethod,
    Token subName,
    BSLParser.ParamListContext paramList,
    boolean function,
    boolean export,
    Optional<CompilerDirectiveKind> compilerDirective,
    List<Annotation> annotations
  ) {
    Optional<MethodDescription> description = createDescription(startOfMethod)
      .or(() -> createDescription(startNode.getSymbol()));
    boolean deprecated = description
      .map(MethodDescription::isDeprecated)
      .orElse(false);

    return MethodSymbol.builder()
      .name(subName.getText().intern())
      .owner(documentContext)
      .range(Ranges.create(startNode, stopNode))
      .subNameRange(Ranges.create(subName))
      .function(function)
      .export(export)
      .description(description)
      .deprecated(deprecated)
      .parameters(createParameters(paramList, description))
      .compilerDirectiveKind(compilerDirective)
      .annotations(annotations)
      .build();
  }

  private Optional<MethodDescription> createDescription(Token token) {
    List<Token> comments = Trees.getComments(documentContext.getTokens(), token);
    if (comments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new MethodDescription(comments));
  }

  private static List<ParameterDefinition> createParameters(
    @Nullable BSLParser.ParamListContext paramList,
    Optional<MethodDescription> description
  ) {
    if (paramList == null) {
      return Collections.emptyList();
    }

    return paramList.param().stream()
      .map((BSLParser.ParamContext param) -> {
        String parameterName = getParameterName(param.IDENTIFIER());
        return ParameterDefinition.builder()
          .name(parameterName)
          .byValue(param.VAL_KEYWORD() != null)
          .defaultValue(getDefaultValue(param))
          .range(getParameterRange(param))
          .description(getParameterDescription(parameterName, description))
          .build();
      }).toList();
  }

  private static ParameterDefinition.DefaultValue getDefaultValue(BSLParser.ParamContext param) {
    if (param.defaultValue() == null) {
      return ParameterDefinition.DefaultValue.EMPTY;
    }

    var constValue = param.defaultValue().constValue();

    ParameterDefinition.DefaultValue defaultValue;
    if (constValue.DATETIME() != null) {
      var value = constValue.DATETIME().getSymbol().getText();
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.DATETIME, value.intern());
    } else if (constValue.FALSE() != null) {
      var value = constValue.FALSE().getSymbol().getText();
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.BOOLEAN, value.intern());
    } else if (constValue.TRUE() != null) {
      var value = constValue.TRUE().getSymbol().getText();
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.BOOLEAN, value.intern());
    } else if (constValue.UNDEFINED() != null) {
      var value = constValue.UNDEFINED().getSymbol().getText();
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.UNDEFINED, value.intern());
    } else if (constValue.NULL() != null) {
      var value = constValue.NULL().getSymbol().getText();
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.NULL, value.intern());
    } else if (constValue.string() != null) {
      var value = constValue.string().STRING().stream()
        .map(TerminalNode::getSymbol)
        .map(Token::getText)
        .collect(Collectors.joining("\n"));
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.STRING, value.intern());
    } else if (constValue.numeric() != null) {
      var value = constValue.numeric().getText();
      if (constValue.MINUS() != null) {
        value = constValue.MINUS().getSymbol().getText() + value;
      }
      if (constValue.PLUS() != null) {
        value = constValue.PLUS().getSymbol().getText() + value;
      }
      defaultValue = new ParameterDefinition.DefaultValue(ParameterType.NUMERIC, value.intern());
    } else {
      defaultValue = ParameterDefinition.DefaultValue.EMPTY;
    }

    return defaultValue;
  }

  private static String getParameterName(TerminalNode identifier) {
    return Optional.ofNullable(identifier)
      .map(ParseTree::getText)
      .map(String::intern)
      .orElse("<UNKNOWN_IDENTIFIER>");
  }

  private static Range getParameterRange(BSLParser.ParamContext param) {
    if (param.IDENTIFIER() == null) {
      return Ranges.create(param.start);
    }
    return Ranges.create(param.IDENTIFIER());
  }

  private static Optional<ParameterDescription> getParameterDescription(
    String parameterName,
    Optional<MethodDescription> description) {

    return description.map(MethodDescription::getParameters)
      .stream()
      .flatMap(Collection::stream)
      .filter(parameterDescription -> parameterDescription.getName().equalsIgnoreCase(parameterName))
      .findFirst();

  }

  private static List<Annotation> createAnnotations(List<? extends BSLParser.AnnotationContext> annotationContexts) {
    return annotationContexts.stream()
      .map(MethodSymbolComputer::createAnnotation)
      .toList();
  }

  private static Annotation createAnnotation(BSLParser.AnnotationContext annotation) {
    return Annotation.builder()
      .name(annotation.annotationName().getText().intern())
      .kind(AnnotationKind.of(annotation.annotationName().getStop().getType()))
      .parameters(getAnnotationParameter(annotation.annotationParams()))
      .build();
  }

  private static List<AnnotationParameterDefinition> getAnnotationParameter(
    BSLParser.AnnotationParamsContext annotationParamsContext
  ) {

    if (annotationParamsContext == null) {
      return Collections.emptyList();
    }

    return annotationParamsContext.annotationParam().stream()
      .map(MethodSymbolComputer::getAnnotationParam)
      .toList();
  }

  private static AnnotationParameterDefinition getAnnotationParam(BSLParser.AnnotationParamContext o) {
    var name = Optional.ofNullable(o.annotationParamName())
      .map(BSLParserRuleContext::getText)
      .orElse("");
    var value = Optional.ofNullable(o.constValue())
      .map(BSLParserRuleContext::getText)
      .map(MethodSymbolComputer::excludeTrailingQuotes)
      .orElse("");
    var optional = o.constValue() != null;

    return new AnnotationParameterDefinition(name, value, optional);
  }

  private static String excludeTrailingQuotes(String text) {
    if (text.length() > 2 && text.charAt(0) == '\"') {
      return text.substring(1, text.length() - 1);
    }
    return text;
  }
}
