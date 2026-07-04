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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Рендеринг inlay-подсказок имён параметров для вызовов методов, разрешённых через
 * систему типов ({@link PlatformMethodCallInlayHintCollector}): построение метки
 * (имя параметра, значение по умолчанию), tooltip и позиции с учётом вариадик-хвоста
 * и пропущенных аргументов.
 */
@Component
public class PlatformMethodCallHintRenderer {

  private final LanguageServerConfiguration configuration;
  private final Resources resources;

  public PlatformMethodCallHintRenderer(LanguageServerConfiguration configuration, Resources resources) {
    this.configuration = configuration;
    this.resources = resources;
  }

  /**
   * Добавляет в {@code sink} подсказки имён параметров для фактических аргументов вызова.
   *
   * @param sink      Коллекция-приёмник подсказок.
   * @param member    Дескриптор вызываемого метода/конструктора.
   * @param signature Выбранная сигнатура вызова.
   * @param args      Фактические аргументы вызова.
   */
  public void appendHints(
    List<InlayHint> sink,
    MemberDescriptor member,
    SignatureDescriptor signature,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var parameters = signature.parameters();
    if (parameters.isEmpty()) {
      return;
    }
    var lang = currentLanguage();
    var variadicIndex = variadicIndex(parameters);
    // Единственный пустой callParam — это запись вида `Метод()` / `Новый Тип()`,
    // т.е. ноль фактических аргументов. Пропуск аргумента (`Метод(а,,б)`) даёт
    // несколько callParam'ов, в которых пустой обозначает именно опущенный довод.
    var hasSkippedArguments = args.size() > 1;
    for (var i = 0; i < args.size(); i++) {
      var unit = paramAt(parameters, variadicIndex, i, lang);
      if (unit == null) {
        break;
      }
      appendHint(sink, member, unit, args.get(i), hasSkippedArguments);
    }
  }

  /** Имя параметра + дескриптор для конкретной позиции аргумента. */
  private record NamedParam(String name, ParameterDescriptor descriptor) {
  }

  /**
   * Параметр для позиции аргумента {@code i}. Вариадик-хвост разворачивается в
   * нумерованные имена по фактическим аргументам (Значение → Значение1, …).
   * {@code null}, если аргументов больше, чем параметров, и хвост не вариадик.
   */
  @Nullable
  private static NamedParam paramAt(List<ParameterDescriptor> parameters, int variadicIndex,
                                    int i, Language lang) {
    if (variadicIndex >= 0 && i >= variadicIndex) {
      var p = parameters.get(variadicIndex);
      return new NamedParam(p.displayName(lang) + (i - variadicIndex + 1), p);
    }
    if (i < parameters.size()) {
      var p = parameters.get(i);
      return new NamedParam(p.displayName(lang), p);
    }
    return null;
  }

  private void appendHint(List<InlayHint> sink, MemberDescriptor member, NamedParam unit,
                          BSLParser.CallParamContext callParam, boolean argumentSkipAllowed) {
    var passedValue = callParam.getText();
    // Пустой довод — либо `Метод()`/`Новый Тип()` (ноль аргументов, хинт не нужен),
    // либо пропущенный аргумент `Метод(а,,б)`. Для пропущенного при включённом
    // showDefaultValues показываем значение по умолчанию из сигнатуры.
    if (passedValue.isBlank()) {
      if (!argumentSkipAllowed || !showDefaultValues() || unit.descriptor().defaultValue().isBlank()) {
        return;
      }
    } else if (!showParametersWithTheSameName()
      && shadowsName(passedValue, unit.name(), unit.descriptor())) {
      return;
    } else {
      // непустой довод, не дублирующий имя параметра — показываем обычный хинт ниже
    }
    var hint = new InlayHint();
    hint.setKind(InlayHintKind.Parameter);
    hint.setLabel(buildLabel(unit.name(), unit.descriptor(), passedValue));
    hint.setPosition(positionOf(callParam));
    if (!unit.name().isBlank() || !unit.descriptor().description().isBlank()) {
      hint.setTooltip(buildTooltip(unit.name(), unit.descriptor(), member));
    }
    hint.setPaddingRight(Boolean.TRUE);
    sink.add(hint);
  }

  /** Индекс вариадик-параметра (он же последний), либо {@code -1}. */
  private static int variadicIndex(List<ParameterDescriptor> parameters) {
    for (var i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).variadic()) {
        return i;
      }
    }
    return -1;
  }

  /** Аргумент уже содержит имя параметра — хинт был бы избыточен. */
  private static boolean shadowsName(String passedValue, String paramName, ParameterDescriptor parameter) {
    if (paramName.isBlank()) {
      return false;
    }
    var bn = parameter.bilingualName();
    return Strings.CI.contains(passedValue, paramName)
      || (!bn.ru().isEmpty() && Strings.CI.contains(passedValue, bn.ru()))
      || (!bn.en().isEmpty() && Strings.CI.contains(passedValue, bn.en()));
  }

  private String buildLabel(String paramName, ParameterDescriptor parameter, String passedValue) {
    var sb = new StringBuilder();
    sb.append(paramName);
    if (showDefaultValues() && passedValue.isBlank() && !parameter.defaultValue().isBlank()) {
      sb.append(" (").append(parameter.defaultValue()).append(')');
    } else {
      sb.append(':');
    }
    return sb.toString();
  }

  private MarkupContent buildTooltip(String paramName, ParameterDescriptor parameter, MemberDescriptor member) {
    var sb = new StringBuilder();
    var lang = currentLanguage();
    sb.append("**").append(paramName).append("**");
    if (parameter.optional()) {
      sb.append(" _(").append(tr("optionalParameter")).append(")_");
    }
    if (!parameter.defaultValue().isBlank()) {
      sb.append(" _= ").append(parameter.defaultValue()).append('_');
    }
    if (!parameter.types().isEmpty()) {
      sb.append(": ");
      var first = true;
      for (var ref : parameter.types().refs()) {
        if (!first) {
          sb.append(" | ");
        }
        sb.append(ref.qualifiedName());
        first = false;
      }
    }
    var pDesc = parameter.displayDescription(lang);
    if (!pDesc.isBlank()) {
      sb.append("\n\n").append(pDesc);
    }
    sb.append("\n\n_").append(tr("methodLabel")).append("_ `").append(member.displayName(lang)).append('`');
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  private static Position positionOf(BSLParser.CallParamContext callParam) {
    var token = callParam.getStart();
    return new Position(token.getLine() - 1, token.getCharPositionInLine());
  }

  private boolean showDefaultValues() {
    return MethodCallInlayHintFlags.showDefaultValues(configuration);
  }

  private boolean showParametersWithTheSameName() {
    return MethodCallInlayHintFlags.showParametersWithTheSameName(configuration);
  }

  private Language currentLanguage() {
    return configuration.getLanguage();
  }

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }
}
