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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormHandlerRoleIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Отрисовка <b>контракта платформенного события</b> в hover'е: шапка обработчика и его
 * параметры с типами и описаниями из синтакс-помощника.
 * <p>
 * Вынесено из {@link DescriptionFormatter}: тот рисует описание, написанное человеком в
 * шапке-комментарии, а здесь — то, что о методе знает платформа. Разные источники,
 * разные зависимости; вместе они тянули в один класс и реестр типов, и индекс
 * контрактов, и роли обработчиков формы.
 * <p>
 * Строки ресурсов берутся из бандла {@link DescriptionFormatter}: секции у hover'а
 * общие, и заводить второй набор переводов ради одного заголовка незачем.
 */
@Component
@RequiredArgsConstructor
public class EventContractFormatter {

  private static final String PARAMETERS_KEY = "parameters";
  private static final String EVENT_HANDLER_HEADER_KEY = "eventHandlerHeader";
  private static final String COMMAND_HANDLER_HEADER_KEY = "commandHandlerHeader";
  private static final String PARAMETER_TEMPLATE = "* **%s**: %s";

  private final Resources resources;
  private final EventContractsIndex eventContractsIndex;
  private final FormHandlerRoleIndex formHandlerRoleIndex;
  private final TypeRegistry typeRegistry;
  private final LanguageServerConfiguration configuration;

  public String getEventHandlerSection(MemberDescriptor event) {
    return getEventHandlerSection(null, event);
  }

  /**
   * Перегрузка с контекстным методом: к платформенному описанию события
   * подмешивается пользовательское описание метода из шапки-комментария.
   * Платформенное описание идёт первым, пользовательское — следом.
   * <p>
   * Обработчик команды формы событием не является, поэтому и называется иначе —
   * и назван в шапке именем команды, а не процедуры: имя процедуры и так стоит
   * в сигнатуре выше.
   */
  public String getEventHandlerSection(@Nullable MethodSymbol method, MemberDescriptor event) {
    var sj = new StringJoiner("\n");
    var command = commandOf(method);
    if (command == null) {
      sj.add("**" + getResourceString(EVENT_HANDLER_HEADER_KEY) + ":** `" + event.name() + "`");
    } else {
      sj.add("**" + getResourceString(COMMAND_HANDLER_HEADER_KEY) + ":** `" + command + "`");
    }
    var platformDescription = event.description();
    if (!platformDescription.isBlank()) {
      sj.add("");
      sj.add(platformDescription);
    }
    appendUserPurpose(sj, method);
    return sj.toString();
  }

  /** Описание метода из шапки-комментария — оно дополняет платформенное, а не заменяет. */
  private static void appendUserPurpose(StringJoiner sj, @Nullable MethodSymbol method) {
    if (method == null) {
      return;
    }
    var userPurpose = method.getDescription()
      .map(MethodDescription::getPurposeDescription)
      .filter(text -> !text.isBlank())
      .orElse("");
    if (!userPurpose.isBlank()) {
      sj.add("");
      sj.add(userPurpose);
    }
  }

  /**
   * Имя команды, обработчиком которой объявлен метод.
   *
   * @param method метод модуля формы; {@code null} — контекста нет.
   * @return имя команды; {@code null}, если метод — обработчик события, а не команды.
   */
  private @Nullable String commandOf(@Nullable MethodSymbol method) {
    if (method == null) {
      return null;
    }
    return formHandlerRoleIndex.roleOf(method.getOwner(), method.getName())
      .filter(handler -> handler.role() == FormHandlerRoleIndex.Role.COMMAND)
      .map(FormHandlerRoleIndex.Handler::owner)
      .filter(name -> !name.isBlank())
      .orElse(null);
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
  public String getParametersSection(@Nullable MethodSymbol method, MemberDescriptor eventContract) {
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
      .map(p -> p.bilingualDescription().forLanguage(configuration.getLanguage()))
      .orElse("");
  }

  /** Имя параметра обработчика по позиции в шапке-комментарии метода (для подмешивания user-описания). */
  private static Map<Integer, String> userParameterDescriptions(@Nullable MethodSymbol method) {
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
    @Nullable MethodSymbol method
  ) {
    var lang = configuration.getLanguage();
    var name = pickName(parameter, method, index, lang);
    var types = parameter.types().refs().stream()
      .map(ref -> typeRegistry.displayName(ref, lang))
      .collect(Collectors.joining(" | "));
    var line = PARAMETER_TEMPLATE.formatted(name, types);
    var contractDescription = parameter.bilingualDescription().forLanguage(lang);
    var userDescription = userDescriptions.getOrDefault(index, "");
    if (!contractDescription.isBlank() && !userDescription.isBlank()) {
      return line + " — " + contractDescription + " / " + userDescription;
    }
    if (!contractDescription.isBlank()) {
      return line + " — " + contractDescription;
    }
    if (!userDescription.isBlank()) {
      return line + " — " + userDescription;
    }
    return line;
  }

  /**
   * Имя параметра для секции «Параметры»: платформенное из контракта. Имя из кода
   * подставляется только когда контракт его не знает — иначе разошедшиеся имена
   * читаются как дубль: у {@code ПередЗакрытием(Отказ, СтандартнаяОбработка)} второй
   * параметр контракта — {@code ЗавершениеРаботы}, и под именем из кода он выглядел
   * бы вторым {@code СтандартнаяОбработка}.
   */
  private static String pickName(ParameterDescriptor parameter, @Nullable MethodSymbol method, int index,
                                 Language language) {
    var fromContract = parameter.bilingualName().forLanguage(language);
    if (!fromContract.isBlank()) {
      return fromContract;
    }
    if (method != null && index < method.getParameters().size()) {
      var fromCode = method.getParameters().get(index).getName();
      if (!fromCode.isBlank()) {
        return fromCode;
      }
    }
    return "";
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

  private static Optional<ParameterDescriptor> parameterAt(MemberDescriptor contract, int index) {
    if (contract.signatures().isEmpty()) {
      return Optional.empty();
    }
    var params = contract.signatures().get(0).parameters();
    if (params.isEmpty()) {
      return Optional.empty();
    }
    var idx = index < params.size() ? index : (params.size() - 1);
    var p = params.get(idx);
    if (index >= params.size() && !p.variadic()) {
      return Optional.empty();
    }
    return Optional.of(p);
  }

  private String getResourceString(String key) {
    return resources.getResourceString(DescriptionFormatter.class, key);
  }
}
