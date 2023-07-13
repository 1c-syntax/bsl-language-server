/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  scope = DiagnosticScope.BSL,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_10,
  tags = {
    DiagnosticTag.DEPRECATED
  }

)
public class DeprecatedMethods8310Diagnostic extends AbstractVisitorDiagnostic {

  private static final String SET_SHORT_APPLICATION_CAPTION_RU = "УстановитьКраткийЗаголовокПриложения";
  private static final String SET_SHORT_APPLICATION_CAPTION_EN = "SetShortApplicationCaption";
  private static final String GET_SHORT_APPLICATION_CAPTION_RU = "ПолучитьКраткийЗаголовокПриложения";
  private static final String GET_SHORT_APPLICATION_CAPTION_EN = "GetShortApplicationCaption";
  private static final String SET_CLIENT_APPLICATION_CAPTION_RU = "УстановитьЗаголовокКлиентскогоПриложения";
  private static final String SET_CLIENT_APPLICATION_CAPTION_EN = "SetClientApplicationCaption";
  private static final String GET_CLIENT_APPLICATION_CAPTION_RU = "ПолучитьЗаголовокКлиентскогоПриложения";
  private static final String GET_CLIENT_APPLICATION_CAPTION_EN = "GetClientApplicationCaption";
  private static final String BASE_FONT_CURRENT_VARIANT_RU = "ТекущийВариантОсновногоШрифтаКлиентскогоПриложения";
  private static final String BASE_FONT_CURRENT_VARIANT_EN = "ClientApplicationBaseFontCurrentVariant";
  private static final String CLIENT_INTERFACE_VARIANT_RU = "ТекущийВариантИнтерфейсаКлиентскогоПриложения";
  private static final String CLIENT_INTERFACE_VARIANT_EN = "ClientApplicationInterfaceCurrentVariant";

  private static final Pattern METHOD_PATTERN = CaseInsensitivePattern.compile(getRegex());

  private static final HashMap<String, String> newMethods = new HashMap<>();

  static {
    newMethods.put(SET_SHORT_APPLICATION_CAPTION_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.УстановитьКраткийЗаголовок");
    newMethods.put(GET_SHORT_APPLICATION_CAPTION_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.ПолучитьКраткийЗаголовок");
    newMethods.put(SET_CLIENT_APPLICATION_CAPTION_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.УстановитьЗаголовок");
    newMethods.put(GET_CLIENT_APPLICATION_CAPTION_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.ПолучитьЗаголовок");
    newMethods.put(BASE_FONT_CURRENT_VARIANT_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.ТекущийВариантОсновногоШрифта");
    newMethods.put(CLIENT_INTERFACE_VARIANT_RU.toLowerCase(Locale.ENGLISH),
      "КлиентскоеПриложение.ТекущийВариантИнтерфейса");

    newMethods.put(SET_SHORT_APPLICATION_CAPTION_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.SetShortCaption");
    newMethods.put(GET_SHORT_APPLICATION_CAPTION_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.GetShortCaption");
    newMethods.put(SET_CLIENT_APPLICATION_CAPTION_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.SetCaption");
    newMethods.put(GET_CLIENT_APPLICATION_CAPTION_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.GetCaption");
    newMethods.put(BASE_FONT_CURRENT_VARIANT_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.CurrentBaseFontVariant");
    newMethods.put(CLIENT_INTERFACE_VARIANT_EN.toLowerCase(Locale.ENGLISH),
      "ClientApplication.CurrentInterfaceVariant");
  }

  private static String getRegex() {
    HashMap<String, String> methodsPair = new HashMap<>();
    methodsPair.put(SET_SHORT_APPLICATION_CAPTION_RU, SET_SHORT_APPLICATION_CAPTION_EN);
    methodsPair.put(GET_SHORT_APPLICATION_CAPTION_RU, GET_SHORT_APPLICATION_CAPTION_EN);
    methodsPair.put(SET_CLIENT_APPLICATION_CAPTION_RU, SET_CLIENT_APPLICATION_CAPTION_EN);
    methodsPair.put(GET_CLIENT_APPLICATION_CAPTION_RU, GET_CLIENT_APPLICATION_CAPTION_EN);
    methodsPair.put(BASE_FONT_CURRENT_VARIANT_RU, BASE_FONT_CURRENT_VARIANT_EN);
    methodsPair.put(CLIENT_INTERFACE_VARIANT_RU, CLIENT_INTERFACE_VARIANT_EN);
    StringJoiner regex = new StringJoiner("|");

    methodsPair.forEach((String k, String v) -> {
      regex.add(k);
      regex.add(v);
    });

    return regex.toString();
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    Matcher matcher = METHOD_PATTERN.matcher(ctx.methodName().getText());
    if (matcher.matches()) {
      diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(matcher.group(), newMethods.getOrDefault(matcher.group().toLowerCase(Locale.ENGLISH), "")));
    }

    return super.visitGlobalMethodCall(ctx);
  }
}
