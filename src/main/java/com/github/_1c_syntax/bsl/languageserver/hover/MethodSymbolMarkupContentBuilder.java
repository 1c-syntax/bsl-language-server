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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Построитель контента для всплывающего окна для {@link MethodSymbol}.
 */
@Component
@RequiredArgsConstructor
public class MethodSymbolMarkupContentBuilder implements MarkupContentBuilder<MethodSymbol> {

  private static final String PROCEDURE_KEY = "procedure";
  private static final String FUNCTION_KEY = "function";
  private static final String EXPORT_KEY = "export";
  private static final String VAL_KEY = "val";
  private static final String PARAMETERS_KEY = "parameters";
  private static final String RETURNED_VALUE_KEY = "returnedValue";
  private static final String EXAMPLES_KEY = "examples";
  private static final String CALL_OPTIONS_KEY = "callOptions";
  private static final String PARAMETER_TEMPLATE = "* **%s**: %s";

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
    String methodLocation = getLocation(symbol);
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
  public SymbolKind getSymbolKind() {
    return SymbolKind.Method;
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
    var result = new StringJoiner("  \n"); // два пробела
    methodSymbol.getParameters().forEach(parameterDefinition ->
      result.add(parameterToString(parameterDefinition))
    );

    var parameters = result.toString();

    if (!parameters.isBlank()) {
      var parametersSection = new StringJoiner("\n");
      String header = "**" + getResourceString(PARAMETERS_KEY) + ":**";
      parametersSection.add(header);
      parametersSection.add("");
      parametersSection.add(parameters);
      return parametersSection.toString();
    }

    return "";
  }

  private String getReturnedValueSection(MethodSymbol methodSymbol) {
    var result = new StringJoiner("  \n"); // два пробела
    methodSymbol.getDescription().ifPresent((MethodDescription methodDescription) -> {
      Map<String, String> typesMap = typesToMap(methodDescription.getReturnedValue(), 0);
      result.add(typesMapToString(typesMap, 1));
    });

    var returnedValue = result.toString();

    if (!returnedValue.isEmpty()) {
      returnedValue = "**" + getResourceString(RETURNED_VALUE_KEY) + ":**\n\n" + returnedValue;
    }

    return returnedValue;
  }

  private String getExamplesSection(MethodSymbol methodSymbol) {
    var examples = methodSymbol.getDescription()
      .map(MethodDescription::getExamples)
      .orElseGet(Collections::emptyList);
    return getSectionWithCodeFences(examples, EXAMPLES_KEY);
  }

  private String getCallOptionsSection(MethodSymbol methodSymbol) {
    var callOptions = methodSymbol.getDescription()
      .map(MethodDescription::getCallOptions)
      .orElseGet(Collections::emptyList);
    return getSectionWithCodeFences(callOptions, CALL_OPTIONS_KEY);
  }

  private String getSectionWithCodeFences(List<String> codeBlocks, String resourceKey) {
    String codeFences = codeBlocks
      .stream()
      .map(codeBlock -> "```bsl\n" + codeBlock + "\n```")
      .collect(Collectors.joining("\n"));

    if (!codeFences.isEmpty()) {
      codeFences = "**" + getResourceString(resourceKey) + ":**\n\n" + codeFences;
    }

    return codeFences;
  }

  private static String getLocation(MethodSymbol symbol) {
    var documentContext = symbol.getOwner();
    var startPosition = symbol.getSelectionRange().getStart();
    String mdoRef = MdoRefBuilder.getMdoRef(documentContext);

    return String.format(
      "[%s](%s#%d)",
      mdoRef,
      documentContext.getUri(),
      startPosition.getLine() + 1
    );
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

  public static String parameterToString(ParameterDescription parameter, int level) {
    var result = new StringJoiner("  \n"); // два пробела
    Map<String, String> typesMap = typesToMap(parameter.getTypes(), level);
    var parameterTemplate = "  ".repeat(level) + PARAMETER_TEMPLATE;

    if (typesMap.size() == 1) {
      result.add(String.format(parameterTemplate,
        parameter.getName(),
        typesMapToString(typesMap, 0)));
    } else {
      result.add(String.format(parameterTemplate, parameter.getName(), ""));
      result.add(typesMapToString(typesMap, level + 1));
    }
    return result.toString();
  }

  public static String parameterToString(ParameterDefinition parameterDefinition) {
    int level = 0;
    if (parameterDefinition.getDescription().isPresent()) {
      return parameterToString(parameterDefinition.getDescription().get(), level);
    }

    return String.format(PARAMETER_TEMPLATE, parameterDefinition.getName(), "");
  }

  private static Map<String, String> typesToMap(List<TypeDescription> parameterTypes, int level) {
    Map<String, String> types = new HashMap<>();

    parameterTypes.forEach((TypeDescription type) -> {
      var typeDescription = typeToString(type, level);
      String typeName;
      if (type.isHyperlink()) {
        typeName = String.format("[%s](%s)", type.getName(), type.getLink());
      } else {
        typeName = String.format("`%s`", type.getName());
      }

      types.merge(typeDescription, typeName, (oldValue, newValue) -> String.format("%s | %s", oldValue, newValue));
    });
    return types;
  }

  private static String typesMapToString(Map<String, String> types, int level) {
    var result = new StringJoiner("  \n"); // два пробела
    var indent = "&nbsp;&nbsp;".repeat(level);
    types.forEach((String key, String value) -> {
      if (key.isBlank()) {
        result.add(value);
      } else {
        result.add(String.format("%s%s %s", indent, value, key));
      }
    });
    return result.toString();
  }

  private static String typeToString(TypeDescription type, int level) {
    var result = new StringJoiner("  \n"); // два пробела
    var description = type.getDescription().replace("\n", "<br>" + "&nbsp;&nbsp;".repeat(level + 1));

    if (!description.isBlank()) {
      description = "- " + description;
    }
    if (!type.getParameters().isEmpty()) {
      description += ":";
    }

    result.add(description);
    type.getParameters().forEach((ParameterDescription parameter) ->
      result.add(parameterToString(parameter, level + 1)));
    return result.toString();
  }
}
