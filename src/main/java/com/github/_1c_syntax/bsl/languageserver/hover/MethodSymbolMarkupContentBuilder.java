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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Построитель контента для всплывающего окна для {@link MethodSymbol}.
 */
@Component
@RequiredArgsConstructor
public class MethodSymbolMarkupContentBuilder implements MarkupContentBuilder<MethodSymbol> {

  private static final String METHOD_LOCATION_KEY = "methodLocation";
  private static final String PROCEDURE_KEY = "procedure";
  private static final String FUNCTION_KEY = "function";
  private static final String EXPORT_KEY = "export";
  private static final String VAL_KEY = "val";
  private static final String PARAMETERS_KEY = "parameters";
  private static final String RETURNED_VALUE_KEY = "returnedValue";
  private static final String EXAMPLES_KEY = "examples";
  private static final String CALL_OPTIONS_KEY = "callOptions";

  private final ServerContext serverContext;
  private final LanguageServerConfiguration configuration;

  @Override
  public MarkupContent getContent(MethodSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // местоположение метода
    // описание метода
    // параметры
    // возвращаемое значение
    // примеры
    // варианты вызова

    // сигнатура
    String signature = getSignature(symbol);
    addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение метода
    String methodLocation = getMethodLocation(symbol);
    addSectionIfNotEmpty(markupBuilder, methodLocation);

    // описание метода
    String purposeSection = getPurposeSection(symbol);
    addSectionIfNotEmpty(markupBuilder, purposeSection);

    // параметры
    String parametersSection = getParametersSection(symbol);
    addSectionIfNotEmpty(markupBuilder, parametersSection);

    // возвращаемое значение
    String returnedValueSection = getReturnedValueSection(symbol);
    addSectionIfNotEmpty(markupBuilder, returnedValueSection);

    // примеры
    String examplesSection = getExamplesSection(symbol);
    addSectionIfNotEmpty(markupBuilder, examplesSection);

    // варианты вызова
    String callOptionsSection = getCallOptionsSection(symbol);
    addSectionIfNotEmpty(markupBuilder, callOptionsSection);

    String content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public Class<MethodSymbol> getType() {
    return MethodSymbol.class;
  }

  private static void addSectionIfNotEmpty(StringJoiner markupBuilder, String newContent) {
    if (!newContent.isEmpty()) {
      markupBuilder.add(newContent);
      markupBuilder.add("");
      markupBuilder.add("---");
    }
  }

  private static String getPurposeSection(MethodSymbol methodSymbol) {
    return methodSymbol.getDescription()
      .map(MethodDescription::getPurposeDescription)
      .orElse("");
  }

  private String getParametersSection(MethodSymbol methodSymbol) {
    var parameters = new StringJoiner("\n");
    String parameterTemplate = "**_%s_**: `%s` - %s\n";
    methodSymbol.getParameters().forEach((ParameterDefinition parameterDefinition) -> {
      String types = parameterDefinition.getDescription()
        .map(ParameterDescription::getTypes)
        .map(MethodSymbolMarkupContentBuilder::getTypes)
        .orElse("");
      String description = parameterDefinition.getDescription()
        .map(ParameterDescription::getDescription)
        .orElse("");
      String parameter = String.format(
        parameterTemplate,
        parameterDefinition.getName(),
        types,
        description
      );

      parameters.add(parameter);
    });

    var parametersSection = new StringJoiner("\n");

    if (parameters.length() > 0) {
      String header = "**" + getResourceString(PARAMETERS_KEY) + ":**";
      parametersSection.add(header);
      parametersSection.add("");
      parametersSection.add(parameters.toString());
    }

    return parametersSection.toString();
  }

  private String getReturnedValueSection(MethodSymbol methodSymbol) {
    String returnedValueTemplate = "`%s` - %s";
    String returnedValue = methodSymbol.getDescription()
      .map(MethodDescription::getReturnedValue)
      .stream()
      .flatMap(Collection::stream)
      .map(typeDescription -> String.format(
        returnedValueTemplate,
        typeDescription.getName(),
        typeDescription.getDescription()
        )
      )
      .collect(Collectors.joining("\n"));

    if (!returnedValue.isEmpty()) {
      returnedValue = "**" + getResourceString(RETURNED_VALUE_KEY) + ":**\n\n" + returnedValue;
    }

    return returnedValue;
  }

  private String getExamplesSection(MethodSymbol methodSymbol) {
    String examples = methodSymbol.getDescription()
      .map(MethodDescription::getExamples)
      .stream()
      .flatMap(Collection::stream)
      .map(s -> "```bsl\n" + s + "\n```")
      .collect(Collectors.joining("\n"));

    if (!examples.isEmpty()) {
      examples = "**" + getResourceString(EXAMPLES_KEY) + ":**\n\n" + examples;
    }

    return examples;
  }

  private String getCallOptionsSection(MethodSymbol methodSymbol) {
    String callOptions = methodSymbol.getDescription()
      .map(MethodDescription::getCallOptions)
      .stream()
      .flatMap(Collection::stream)
      .map(s -> "```bsl\n" + s + "\n```")
      .collect(Collectors.joining("\n"));

    if (!callOptions.isEmpty()) {
      callOptions = "**" + getResourceString(CALL_OPTIONS_KEY) + ":**\n\n" + callOptions;
    }

    return callOptions;
  }

  private String getMethodLocation(MethodSymbol methodSymbol) {
    String mdoRef = MdoRefBuilder.getMdoRef(methodSymbol.getOwner());
    return getResourceString(METHOD_LOCATION_KEY, mdoRef);
//    String methodLocation;
//    if (methodSymbol.getUri().equals(documentContext.getUri())) {
//      methodLocation = getResourceString(CURRENT_METHOD_KEY);
//    } else {
//      var uri = methodSymbol.getUri();
//      String uriPresentation = "file".equals(uri.getScheme()) ? Path.of(uri).toString() : uri.toString();
//      methodLocation = getResourceString(EXTERNAL_METHOD_KEY, uriPresentation);
//    }
  }

  private String getSignature(MethodSymbol methodSymbol) {
    String signatureTemplate = "```bsl\n%s %s(%s)%s%s\n```";

    String methodKind;
    if (methodSymbol.isFunction()) {
      methodKind = getResourceString(FUNCTION_KEY);
    } else {
      methodKind = getResourceString(PROCEDURE_KEY);
    }
    String methodName = methodSymbol.getName();

    var parametersDescription = new StringJoiner(", ");
    methodSymbol.getParameters().forEach((ParameterDefinition parameterDefinition) -> {
      var parameter = "";
      var parameterName = parameterDefinition.getName();

      if (parameterDefinition.isByValue()) {
        parameter = parameter + getResourceString(VAL_KEY) + " ";
      }
      parameter += parameterName;

      var parameterTypes = parameterDefinition.getDescription()
        .map(ParameterDescription::getTypes)
        .map(MethodSymbolMarkupContentBuilder::getTypes)
        .orElse("");

      if (!parameterTypes.isEmpty()) {
        parameter += ": " + parameterTypes;
      }

      if (parameterDefinition.isOptional()) {
        parameter += " = ";
        parameter += parameterDefinition.getDefaultValue().getValue();
      }

      parametersDescription.add(parameter);
    });
    var parameters = parametersDescription.toString();

    String returnedValueType = methodSymbol.getDescription()
      .map(MethodDescription::getReturnedValue)
      .map(MethodSymbolMarkupContentBuilder::getTypes)
      .orElse("");
    if (!returnedValueType.isEmpty()) {
      returnedValueType = ": " + returnedValueType;
    }

    String export = methodSymbol.isExport() ? (" " + getResourceString(EXPORT_KEY)) : "";

    return String.format(
      signatureTemplate,
      methodKind,
      methodName,
      parameters,
      export,
      returnedValueType
    );
  }

  private static String getTypes(List<TypeDescription> typeDescriptions) {
    return typeDescriptions.stream()
      .map(TypeDescription::getName)
      .flatMap(parameterType -> Stream.of(parameterType.split(",")))
      .map(String::trim)
      .collect(Collectors.joining(" | "));
  }

  private String getResourceString(String key) {
    return Resources.getResourceString(configuration.getLanguage(), getClass(), key);
  }

  private String getResourceString(String key, Object... args) {
    return Resources.getResourceString(configuration.getLanguage(), getClass(), key, args);
  }
}
