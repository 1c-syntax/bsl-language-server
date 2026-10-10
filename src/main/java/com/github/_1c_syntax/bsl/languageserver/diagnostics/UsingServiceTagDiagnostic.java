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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.antlr.v4.runtime.Token;

import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class UsingServiceTagDiagnostic extends AbstractDiagnostic {

  private static final Pattern NO_TAGS = Pattern.compile("(?!)");

  private static final String SERVICE_TAGS_DEFAULT = "todo|fixme|!!|mrg|@|отладка|debug|для\\s*отладки"
    + "|(\\{\\{|\\}\\})КОНСТРУКТОР_|(\\{\\{|\\}\\})MRG"
    + "|Вставить\\s*содержимое\\s*обработчика"
    + "|Paste\\s*handler\\s*content|Insert\\s*handler\\s*code"
    + "|Insert\\s*handler\\s*content|Insert\\s*handler\\s*contents";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = SERVICE_TAGS_DEFAULT
  )
  private String serviceTags = SERVICE_TAGS_DEFAULT;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ""
  )
  private String additionalServiceTags = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ""
  )
  private String excludedServiceTags = "";

  private Pattern pattern = getPatternSearch(SERVICE_TAGS_DEFAULT);
  private Pattern additionalPattern = NO_TAGS;
  private Pattern excludedPattern = NO_TAGS;

  @Override
  public void configure(Map<String, Object> configuration) {
    serviceTags = (String) configuration.getOrDefault("serviceTags", serviceTags);
    additionalServiceTags = (String) configuration.getOrDefault("additionalServiceTags", additionalServiceTags);
    excludedServiceTags = (String) configuration.getOrDefault("excludedServiceTags", excludedServiceTags);
    pattern = getPatternSearch(serviceTags);
    additionalPattern = getOptionalPatternSearch(additionalServiceTags);
    excludedPattern = getOptionalPatternSearch(excludedServiceTags);
  }

  private Pattern getOptionalPatternSearch(String value) {
    return value.isEmpty() ? NO_TAGS : getPatternSearch(value);
  }

  public Pattern getPatternSearch(String value) {
    return Pattern.compile(
      "//\\s*+(" + value + ")",
      Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);
  }

  @Override
  public void check() {
    documentContext.getComments()
      .parallelStream()
      .forEach((Token token) -> {
        var text = token.getText();
        var matcher = pattern.matcher(text);
        if (!matcher.find()) {
          matcher = additionalPattern.matcher(text);
          if (!matcher.find()) {
            return;
          }
        }
        if (excludedPattern.matcher(text).region(matcher.start(), text.length()).lookingAt()) {
          return;
        }
        diagnosticStorage.addDiagnostic(
          token,
          info.getMessage(matcher.group(0))
        );
      });
  }

}
