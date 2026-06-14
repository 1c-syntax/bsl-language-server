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
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.description.HyperlinkTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.ParameterDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
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
  private static final String DEPRECATED_FLAG_KEY = "deprecatedFlag";
  private static final String EVENT_HANDLER_HEADER_KEY = "eventHandlerHeader";
  private static final String PARAMETER_TEMPLATE = "* **%s**: %s";

  private final Resources resources;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final EventContractsIndex eventContractsIndex;

  public void addSectionIfNotEmpty(StringJoiner markupBuilder, String newContent) {
    if (!newContent.isEmpty()) {
      markupBuilder.add(newContent);
      markupBuilder.add("");
      markupBuilder.add("---");
    }
  }

  /**
   * Формирует секцию признака устаревания метода для всплывающего окна.
   *
   * @param methodSymbol символ метода, для которого строится секция
   * @return markdown-блок «Устарела.» с текстом причины устаревания (если он
   *   указан в описании метода), либо пустая строка, если метод не устарел
   */
  public String getDeprecatedSection(MethodSymbol methodSymbol) {
    if (!methodSymbol.isDeprecated()) {
      return "";
    }

    var deprecatedFlag = "**" + getResourceString(DEPRECATED_FLAG_KEY) + "**";
    var deprecationInfo = methodSymbol.getDescription()
      .map(MethodDescription::getDeprecationInfo)
      .filter(info -> !info.isBlank())
      .orElse("");

    if (deprecationInfo.isEmpty()) {
      return deprecatedFlag;
    }

    return deprecatedFlag + " " + deprecationInfo;
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

  /**
   * Секция «Параметры» для метода, который является обработчиком платформенного
   * события. Имена и типы берутся из контракта события (bsl-context), а не из
   * шапки-комментария пользователя — контракт авторитетен, шапка может
   * устаревать или отсутствовать.
   */
  /**
   * Секция-шапка «Обработчик события платформы: {@code <имя>}» + платформенное
   * описание события из bsl-context. Используется hover-билдерами метода и
   * параметра, чтобы показать, что метод/переменная — обработчик платформенного
   * события.
   */
  public String getEventHandlerSection(MemberDescriptor event) {
    var sj = new StringJoiner("\n");
    sj.add("**" + getResourceString(EVENT_HANDLER_HEADER_KEY) + ":** `" + event.name() + "`");
    var description = event.description();
    if (!description.isBlank()) {
      sj.add("");
      sj.add(description);
    }
    return sj.toString();
  }

  public String getParametersSection(MemberDescriptor eventContract) {
    return getParametersSection(null, eventContract);
  }

  /**
   * Перегрузка с контекстным методом: к описанию параметра из контракта
   * подмешивается пользовательское описание из шапки-комментария метода
   * (если оно есть). Платформенное описание идёт первым, затем пользовательское
   * под отдельным префиксом.
   */
  public String getParametersSection(MethodSymbol method, MemberDescriptor eventContract) {
    if (eventContract.signatures().isEmpty()) {
      return "";
    }
    var parameters = eventContract.signatures().get(0).parameters();
    if (parameters.isEmpty()) {
      return "";
    }
    var userDescriptions = userParameterDescriptions(method);
    var result = new StringJoiner("  \n");
    for (var i = 0; i < parameters.size(); i++) {
      result.add(eventParameterToString(parameters.get(i), userDescriptions, i, method));
    }
    var parametersSection = new StringJoiner("\n");
    parametersSection.add("**" + getResourceString(PARAMETERS_KEY) + ":**");
    parametersSection.add("");
    parametersSection.add(result.toString());
    return parametersSection.toString();
  }

  /** Имя параметра обработчика по позиции в шапке-комментарии метода (для подмешивания user-описания). */
  private static Map<Integer, String> userParameterDescriptions(@org.jspecify.annotations.Nullable MethodSymbol method) {
    if (method == null) {
      return Map.of();
    }
    var descriptionOpt = method.getDescription();
    if (descriptionOpt.isEmpty()) {
      return Map.of();
    }
    var docParameters = descriptionOpt.get().getParameters();
    if (docParameters.isEmpty()) {
      return Map.of();
    }
    var byPosition = new HashMap<Integer, String>();
    var methodParameters = method.getParameters();
    for (var i = 0; i < methodParameters.size(); i++) {
      var paramName = methodParameters.get(i).getName();
      for (var docParam : docParameters) {
        if (paramName.equalsIgnoreCase(docParam.name())) {
          var purpose = docParam.types().stream()
            .map(TypeDescription::description)
            .filter(text -> text != null && !text.isBlank())
            .findFirst()
            .orElse("");
          if (!purpose.isBlank()) {
            byPosition.put(i, purpose);
          }
          break;
        }
      }
    }
    return byPosition;
  }

  private String eventParameterToString(
    ParameterDescriptor parameter, Map<Integer, String> userDescriptions, int index,
    @org.jspecify.annotations.Nullable MethodSymbol method
  ) {
    var name = pickName(parameter, method, index);
    var types = parameter.types().refs().stream()
      .map(TypeRef::qualifiedName)
      .collect(Collectors.joining(" | "));
    var line = PARAMETER_TEMPLATE.formatted(name, types);
    var contractDescription = parameter.bilingualDescription().ru();
    var userDescription = userDescriptions.getOrDefault(index, "");
    if (!contractDescription.isBlank() && !userDescription.isBlank()) {
      line = line + " — " + contractDescription + " / " + userDescription;
    } else if (!contractDescription.isBlank()) {
      line = line + " — " + contractDescription;
    } else if (!userDescription.isBlank()) {
      line = line + " — " + userDescription;
    }
    return line;
  }

  private static String pickName(
    ParameterDescriptor parameter, @org.jspecify.annotations.Nullable MethodSymbol method, int index
  ) {
    if (method != null && index < method.getParameters().size()) {
      var fromCode = method.getParameters().get(index).getName();
      if (!fromCode.isBlank()) {
        return fromCode;
      }
    }
    var ru = parameter.bilingualName().ru();
    if (!ru.isBlank()) {
      return ru;
    }
    return parameter.bilingualName().en();
  }

  /**
   * Описание параметра-обработчика платформенного события из контракта
   * (bsl-context): сопоставление <b>по позиции</b> — имена параметров обработчика
   * задаёт пользователь, в коде они могут не совпадать с именами в контракте.
   * При выходе за длину контракта возвращаем пусто, если последний параметр
   * контракта не variadic.
   */
  public String getEventHandlerParameterDescription(VariableSymbol symbol) {
    if (symbol.getKind() != VariableKind.PARAMETER
      || !(symbol.getScope() instanceof MethodSymbol method)) {
      return "";
    }
    var contractOpt = eventContractsIndex.getContract(method.getOwner(), method.getName());
    if (contractOpt.isEmpty()) {
      return "";
    }
    var paramIndex = indexOfParameter(method, symbol.getName());
    if (paramIndex < 0) {
      return "";
    }
    return parameterAt(contractOpt.get(), paramIndex)
      .map(p -> p.bilingualDescription().ru())
      .orElse("");
  }

  private static int indexOfParameter(MethodSymbol method, String name) {
    var params = method.getParameters();
    for (var i = 0; i < params.size(); i++) {
      if (params.get(i).getName().equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  private static java.util.Optional<ParameterDescriptor> parameterAt(MemberDescriptor contract, int index) {
    if (contract.signatures().isEmpty()) {
      return java.util.Optional.empty();
    }
    var params = contract.signatures().get(0).parameters();
    if (params.isEmpty()) {
      return java.util.Optional.empty();
    }
    var idx = index < params.size() ? index : (params.size() - 1);
    var p = params.get(idx);
    if (index >= params.size() && !p.variadic()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(p);
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

  public String getCallOptionsSection(MethodSymbol methodSymbol) {
    return methodSymbol.getDescription()
      .map(MethodDescription::getCallOptions)
      .filter(callOption -> !callOption.isEmpty())
      .map(codeBlock -> "**" + getResourceString(CALL_OPTIONS_KEY) + ":**\n\n" + "```bsl\n" + codeBlock + "\n```")
      .orElseGet(String::new);
  }

  public String getLocation(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var uri = documentContext.getUri();

    var mdObject = documentContext.getMdObject();
    String mdoRefLocal = mdObject.map(md -> documentContext.getServerContext()
      .getConfiguration()
      .getMdoRefLocal(md)
    ).orElseGet(() -> oScriptLibraryIndex.findByUri(uri)
      .map(OScriptLibraryIndex.LibraryEntry::qualifiedName)
      .orElseGet(documentContext::getMdoRef));

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

    var parentPostfix = symbol.getRootParent(MethodSymbol.class)
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
        // Необязательный параметр помечаем «?»: знак приклеивается к типу
        // (Имя: Тип?), а при отсутствии типа — к имени (Имя?).
        parameter.append('?');
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
