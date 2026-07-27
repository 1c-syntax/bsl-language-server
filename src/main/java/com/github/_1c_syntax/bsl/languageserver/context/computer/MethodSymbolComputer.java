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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegularMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition.ParameterType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotations;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Вычислитель символов методов и функций.
 * <p>
 * Анализирует AST и создает символы для всех методов и функций модуля,
 * включая информацию о параметрах, аннотациях и описаниях.
 */
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

    TerminalNode functionKeyword = declaration.FUNCTION_KEYWORD();
    TerminalNode stopNode = ctx.ENDFUNCTION_KEYWORD();

    if (functionKeyword == null
      || functionKeyword instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    TerminalNode asyncKeyword = declaration.ASYNC_KEYWORD();
    TerminalNode startNode = selectStartNode(declaration.annotation(), asyncKeyword, functionKeyword);
    Token startOfMethod = asyncKeyword != null ? asyncKeyword.getSymbol() : functionKeyword.getSymbol();

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      startOfMethod,
      declaration.subName().getStart(),
      declaration.paramList(),
      true,
      declaration.EXPORT_KEYWORD() != null,
      asyncKeyword != null,
      getCompilerDirective(declaration.compilerDirective()),
      Annotations.from(declaration.annotation()));

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    TerminalNode procedureKeyword = declaration.PROCEDURE_KEYWORD();
    TerminalNode stopNode = ctx.ENDPROCEDURE_KEYWORD();

    if (procedureKeyword == null
      || procedureKeyword instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    TerminalNode asyncKeyword = declaration.ASYNC_KEYWORD();
    TerminalNode startNode = selectStartNode(declaration.annotation(), asyncKeyword, procedureKeyword);
    Token startOfMethod = asyncKeyword != null ? asyncKeyword.getSymbol() : procedureKeyword.getSymbol();

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      startOfMethod,
      declaration.subName().getStart(),
      declaration.paramList(),
      false,
      declaration.EXPORT_KEYWORD() != null,
      asyncKeyword != null,
      getCompilerDirective(declaration.compilerDirective()),
      Annotations.from(declaration.annotation())
    );

    methods.add(methodSymbol);

    return ctx;
  }

  /**
   * Выбирает токен начала символа метода в соответствии с грамматикой
   * {@code (preprocessor | compilerDirective | annotation)* ASYNC_KEYWORD? PROCEDURE_KEYWORD|FUNCTION_KEYWORD}.
   * Приоритет (от самой ранней позиции к самой поздней):
   * первая аннотация → {@code Асинх} → ключевое слово {@code Процедура}/{@code Функция}.
   * Препроцессоры и compiler-directive исторически не входят в range символа.
   */
  private static TerminalNode selectStartNode(
    List<? extends BSLParser.AnnotationContext> annotations,
    @Nullable TerminalNode asyncKeyword,
    TerminalNode keywordNode
  ) {
    if (!annotations.isEmpty()) {
      return annotations.getFirst().AMPERSAND();
    }
    if (asyncKeyword != null) {
      return asyncKeyword;
    }
    return keywordNode;
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
      .orElseGet(() -> compilerDirectiveContexts.getFirst().getStop().getType());

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
    boolean async,
    Optional<CompilerDirectiveKind> compilerDirective,
    List<Annotation> annotations
  ) {
    Optional<MethodDescription> description = createDescription(startOfMethod)
      .or(() -> createDescription(startNode.getSymbol()));
    boolean deprecated = description
      .map(MethodDescription::isDeprecated)
      .orElse(false);

    var name = subName.getText().intern();
    var range = Ranges.create(startNode, stopNode);
    var subNameRange = Ranges.create(subName);
    var parameters = createParameters(paramList, description);

    if (isOscriptClassConstructor(name, function)) {
      return ConstructorSymbol.builder()
        .name(name)
        .owner(documentContext)
        .range(range)
        .subNameRange(subNameRange)
        .function(function)
        .export(export)
        .async(async)
        .description(description)
        .deprecated(deprecated)
        .parameters(parameters)
        .compilerDirectiveKind(compilerDirective)
        .annotations(annotations)
        .build();
    }

    return RegularMethodSymbol.builder()
      .name(name)
      .owner(documentContext)
      .range(range)
      .subNameRange(subNameRange)
      .function(function)
      .export(export)
      .async(async)
      .description(description)
      .deprecated(deprecated)
      .parameters(parameters)
      .compilerDirectiveKind(compilerDirective)
      .annotations(annotations)
      .build();
  }

  /**
   * Конструктор OneScript-класса — это процедура с именем {@code ПриСозданииОбъекта}
   * или {@code OnObjectCreate} в файле, обозначающем класс
   * ({@link ModuleType#OScriptClass}). В {@link ModuleType#OScriptModule} такая
   * процедура — обычный метод (в OScript-модулях нет конструкторов).
   */
  private boolean isOscriptClassConstructor(String name, boolean function) {
    if (function) {
      return false;
    }
    if (documentContext.getModuleType() != ModuleType.OScriptClass) {
      return false;
    }
    return Methods.isOscriptClassConstructorName(name);
  }

  private Optional<MethodDescription> createDescription(Token token) {
    List<Token> comments = Trees.getComments(documentContext.getTokens(), token);
    if (comments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(documentContext.getSymbolDescriptionIndex().methodDescription(documentContext, comments));
  }

  private static List<ParameterDefinition> createParameters(
    BSLParser.@Nullable ParamListContext paramList,
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
          .annotations(Annotations.from(param.annotation()))
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
      .filter(parameterDescription -> parameterDescription.name().equalsIgnoreCase(parameterName))
      .findFirst();

  }

}
