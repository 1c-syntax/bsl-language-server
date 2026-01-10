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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.description.HyperlinkTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

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
      var header = "**" + getResourceString(PARAMETERS_KEY) + ":**";
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
    return methodSymbol.getDescription()
      .map(MethodDescription::getExamples)
      .filter(example -> !example.isEmpty())
      .map(codeBlock -> "**" + getResourceString(EXAMPLES_KEY) + ":**\n\n" + "```bsl\n" + codeBlock + "\n```")
      .orElseGet(String::new);
  }

  public String getLocation(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var uri = documentContext.getUri();

    var mdObject = documentContext.getMdObject();
    String mdoRefLocal = mdObject.map(md -> documentContext.getServerContext()
      .getConfiguration()
      .getMdoRefLocal(md)
    ).orElseGet(documentContext::getMdoRef);

    return "[%s](%s)".formatted(
      mdoRefLocal,
      uri
    );
  }

  public String getLocation(MethodSymbol symbol) {
    var documentContext = symbol.getOwner();
    var startPosition = symbol.getSelectionRange().getStart();
    var mdoRef = documentContext.getMdoRef();

    return "[%s](%s#%d)".formatted(
      mdoRef,
      documentContext.getUri(),
      startPosition.getLine() + 1
    );
  }

  public String getLocation(VariableSymbol symbol) {
    var documentContext = symbol.getOwner();
    var startPosition = symbol.getSelectionRange().getStart();
    var mdoRef = documentContext.getMdoRef();

    var parentPostfix = symbol.getRootParent(SymbolKind.Method)
      .map(sourceDefinedSymbol -> "." + sourceDefinedSymbol.getName())
      .orElse("");
    mdoRef += parentPostfix;

    return "[%s](%s#%d)".formatted(
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
    var methodName = methodSymbol.getName();

    var parameters = getParametersSignatureDescription(methodSymbol);
    var returnedValueType = getReturnedValueTypeDescriptionPart(methodSymbol);
    var export = methodSymbol.isExport() ? (" " + getResourceString(EXPORT_KEY)) : "";

    return signatureTemplate.formatted(
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

    return signatureTemplate.formatted(
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

    return signatureTemplate.formatted(
      varKey,
      name,
      export
    );
  }

  public String getParametersSignatureDescription(MethodSymbol methodSymbol) {
    var parametersDescription = new StringJoiner(", ");
    methodSymbol.getParameters().forEach((ParameterDefinition parameterDefinition) -> {
      var parameter = new StringBuilder();
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
    for (var annotation : parameterDefinition.getAnnotations()) {
      description.append("&").append(annotation.getName()).append(" ");
    }

    return description.toString();
  }

  private static String getReturnedValueTypeDescriptionPart(MethodSymbol methodSymbol) {
    var returnedValueType = methodSymbol.getDescription()
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
    var typesMap = typesToMap(parameterDescription.types(), level);
    var parameterTemplate = "  ".repeat(level) + PARAMETER_TEMPLATE;

    if (typesMap.size() == 1) {
      result.add(parameterTemplate.formatted(
        parameterDescription.name(),
        typesMapToString(typesMap, 0)));
    } else {
      result.add(parameterTemplate.formatted(parameterDescription.name(), ""));
      result.add(typesMapToString(typesMap, level + 1));
    }
    return result.toString();
  }

  public String parameterToString(ParameterDefinition parameterDefinition) {
    var level = 0;
    var parameterDescription = parameterDefinition.getDescription();
    return parameterDescription
      .map(description -> parameterToString(description, level))
      .orElseGet(() -> PARAMETER_TEMPLATE.formatted(
        parameterDefinition.getName(),
        ""
      )
      );

  }

  private Map<String, String> typesToMap(List<TypeDescription> parameterTypes, int level) {
    Map<String, String> types = new HashMap<>();

    parameterTypes.forEach((TypeDescription type) -> {
      var typeDescription = typeToString(type, level);
      String typeName;
      if (type instanceof HyperlinkTypeDescription hyperlinkTypeDescription) {
        typeName = "[%s](%s)".formatted(hyperlinkTypeDescription.name(), hyperlinkTypeDescription.hyperlink());
      } else {
        typeName = "`%s`".formatted(type.name());
      }

      types.merge(typeDescription, typeName, "%s | %s"::formatted);
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
        result.add("%s%s %s".formatted(indent, value, key));
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
    if (!type.fields().isEmpty()) {
      description += ":";
    }

    result.add(description);
    type.fields().forEach((ParameterDescription parameter) ->
      result.add(parameterToString(parameter, level + 1)));
    return result.toString();
  }

  private String getResourceString(String key) {
    return resources.getResourceString(getClass(), key);
  }

}
