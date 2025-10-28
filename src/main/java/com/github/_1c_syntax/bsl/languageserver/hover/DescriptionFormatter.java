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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.ParameterDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class DescriptionFormatter {

  private static final String PROCEDURE_KEY = "procedure";
  private static final String FUNCTION_KEY = "function";
  private static final String ANNOTATION_KEY = "annotation";
  private static final String EXPORT_KEY = "export";
  private static final String VAL_KEY = "val";
  private static final String VARIABLE_KEY = "var";
  private static final String PARAMETERS_KEY = "parameters";
  private static final String RETURNED_VALUE_KEY = "returnedValue";
  private static final String EXAMPLES_KEY = "examples";
  private static final String CALL_OPTIONS_KEY = "callOptions";
  private static final String PARAMETER_TEMPLATE = "* **%s**: %s";

  private final Resources resources;

  public void addSectionIfNotEmpty(StringJoiner markupBuilder, String newContent) {
    if (!newContent.isEmpty()) {
      markupBuilder.add(newContent);
      markupBuilder.add("");
      markupBuilder.add("---");
    }
  }

  public String getPurposeSection(MethodSymbol methodSymbol) {
    return methodSymbol.getDescription()
      .map(MethodDescription::getPurposeDescription)
      .orElse("");
  }

  public String getParametersSection(MethodSymbol methodSymbol) {
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

  public String getReturnedValueSection(MethodSymbol methodSymbol) {
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

  public String getExamplesSection(MethodSymbol methodSymbol) {
    var examples = methodSymbol.getDescription()
      .map(MethodDescription::getExamples)
      .orElseGet(Collections::emptyList);
    return getSectionWithCodeFences(examples, EXAMPLES_KEY);
  }

  public String getCallOptionsSection(MethodSymbol methodSymbol) {
    var callOptions = methodSymbol.getDescription()
      .map(MethodDescription::getCallOptions)
      .orElseGet(Collections::emptyList);
    return getSectionWithCodeFences(callOptions, CALL_OPTIONS_KEY);
  }

  public String getSectionWithCodeFences(Collection<String> codeBlocks, String resourceKey) {
    String codeFences = codeBlocks
      .stream()
      .map(codeBlock -> "```bsl\n" + codeBlock + "\n```")
      .collect(Collectors.joining("\n"));

    if (!codeFences.isEmpty()) {
      codeFences = "**" + getResourceString(resourceKey) + ":**\n\n" + codeFences;
    }

    return codeFences;
  }

  public String getLocation(MethodSymbol symbol) {
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

  public String getLocation(VariableSymbol symbol) {
    var documentContext = symbol.getOwner();
    var startPosition = symbol.getSelectionRange().getStart();
    String mdoRef = MdoRefBuilder.getMdoRef(documentContext);

    String parentPostfix = symbol.getRootParent(SymbolKind.Method)
      .map(sourceDefinedSymbol -> "." + sourceDefinedSymbol.getName())
      .orElse("");
    mdoRef += parentPostfix;

    return String.format(
      "[%s](%s#%d)",
      mdoRef,
      documentContext.getUri(),
      startPosition.getLine() + 1
    );
  }

  public String getSignature(MethodSymbol methodSymbol) {
    var signatureTemplate = "```bsl\n%s %s(%s)%s%s\n```";

    String methodKind;
    if (methodSymbol.isFunction()) {
      methodKind = getResourceString(FUNCTION_KEY);
    } else {
      methodKind = getResourceString(PROCEDURE_KEY);
    }
    String methodName = methodSymbol.getName();

    var parameters = getParametersSignatureDescription(methodSymbol);
    var returnedValueType = getReturnedValueTypeDescriptionPart(methodSymbol);
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

  public String getSignature(AnnotationSymbol symbol, MethodSymbol methodSymbol) {
    var signatureTemplate = "```bsl\n%s &%s(%s)\n```";

    var annotationKind = getResourceString(ANNOTATION_KEY);
    var annotationName = symbol.getName();

    var parameters = getParametersSignatureDescription(methodSymbol);

    return String.format(
      signatureTemplate,
      annotationKind,
      annotationName,
      parameters
    );
  }

  public String getSignature(VariableSymbol symbol) {
    var signatureTemplate = "```bsl\n%s %s%s\n```";

    var varKey = getResourceString(VARIABLE_KEY);
    var name = symbol.getName();
    var export = symbol.isExport() ? (" " + getResourceString(EXPORT_KEY)) : "";

    return String.format(
      signatureTemplate,
      varKey,
      name,
      export
    );
  }

  public String getParametersSignatureDescription(MethodSymbol methodSymbol) {
    var parametersDescription = new StringJoiner(", ");
    methodSymbol.getParameters().forEach((ParameterDefinition parameterDefinition) -> {
      StringBuilder parameter = new StringBuilder();
      parameter.append(getAnnotationsDescriptionPart(parameterDefinition));
      var parameterName = parameterDefinition.getName();

      if (parameterDefinition.isByValue()) {
        parameter.append(getResourceString(VAL_KEY)).append(" ");
      }
      parameter.append(parameterName);

      var parameterTypes = parameterDefinition.getDescription()
        .map(ParameterDescription::types)
        .map(DescriptionFormatter::getTypes)
        .orElse("");

      if (!parameterTypes.isEmpty()) {
        parameter.append(": ").append(parameterTypes);
      }

      if (parameterDefinition.isOptional()) {
        parameter.append(" = ");
        parameter.append(parameterDefinition.getDefaultValue().value());
      }

      parametersDescription.add(parameter.toString());
    });

    return parametersDescription.toString();
  }

  private static String getAnnotationsDescriptionPart(ParameterDefinition parameterDefinition) {
    var description = new StringBuilder();
    for (Annotation annotation : parameterDefinition.getAnnotations()) {
      description.append("&").append(annotation.getName()).append(" ");
    }

    return description.toString();
  }

  private static String getReturnedValueTypeDescriptionPart(MethodSymbol methodSymbol) {
    String returnedValueType = methodSymbol.getDescription()
      .map(MethodDescription::getReturnedValue)
      .map(DescriptionFormatter::getTypes)
      .orElse("");
    if (!returnedValueType.isEmpty()) {
      returnedValueType = ": " + returnedValueType;
    }
    return returnedValueType;
  }

  private static String getTypes(List<TypeDescription> typeDescriptions) {
    return typeDescriptions.stream()
      .map(TypeDescription::name)
      .flatMap(parameterType -> Stream.of(parameterType.split(",")))
      .map(String::trim)
      .collect(Collectors.joining(" | "));
  }

  public String parameterToString(ParameterDescription parameterDescription, int level) {
    var result = new StringJoiner("  \n"); // два пробела
    Map<String, String> typesMap = typesToMap(parameterDescription.types(), level);
    var parameterTemplate = "  ".repeat(level) + PARAMETER_TEMPLATE;

    if (typesMap.size() == 1) {
      result.add(String.format(parameterTemplate,
        parameterDescription.name(),
        typesMapToString(typesMap, 0)));
    } else {
      result.add(String.format(parameterTemplate, parameterDescription.name(), ""));
      result.add(typesMapToString(typesMap, level + 1));
    }
    return result.toString();
  }

  public String parameterToString(ParameterDefinition parameterDefinition) {
    var level = 0;
    var parameterDescription = parameterDefinition.getDescription();
    if (parameterDescription.isPresent()) {
      return parameterToString(parameterDescription.get(), level);
    }

    return String.format(
      PARAMETER_TEMPLATE,
      parameterDefinition.getName(),
      ""
    );
  }

  private Map<String, String> typesToMap(List<TypeDescription> parameterTypes, int level) {
    Map<String, String> types = new HashMap<>();

    parameterTypes.forEach((TypeDescription type) -> {
      var typeDescription = typeToString(type, level);
      String typeName;
      if (type.isHyperlink()) {
        typeName = String.format("[%s](%s)", type.name(), type.link());
      } else {
        typeName = String.format("`%s`", type.name());
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

  private String typeToString(TypeDescription type, int level) {
    var result = new StringJoiner("  \n"); // два пробела
    var description = type.description().replace("\n", "<br>" + "&nbsp;&nbsp;".repeat(level + 1));

    if (!description.isBlank()) {
      description = "- " + description;
    }
    if (!type.parameters().isEmpty()) {
      description += ":";
    }

    result.add(description);
    type.parameters().forEach((ParameterDescription parameter) ->
      result.add(parameterToString(parameter, level + 1)));
    return result.toString();
  }

  private String getResourceString(String key) {
    return resources.getResourceString(getClass(), key);
  }

}
