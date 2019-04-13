/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO
)
public class UsingServiceTagDiagnostic extends AbstractVisitorDiagnostic{

  private static final String SERVICE_TAGS_DEFAULT = "todo|fixme";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + SERVICE_TAGS_DEFAULT,
    description = "Служебные теги"
  )
  private String serviceTags = SERVICE_TAGS_DEFAULT;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    serviceTags = (String) configuration.get("serviceTags");
    pattern = getPatternSearch(serviceTags);
  }

  public static Pattern pattern = getPatternSearch(UsingServiceTagDiagnostic.SERVICE_TAGS_DEFAULT);

  public static Pattern getPatternSearch(String value)
  {
    return Pattern.compile(
      new StringBuilder().append("\\s+(").append(value).append(")").toString(),
      Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  }
  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    List<Diagnostic> diagnostics = new ArrayList<>();

    List <Token> list = documentContext.getTokensWithComments()
      .parallelStream()
      .filter((Token token) -> pattern.matcher(token.getText()).find())
      .collect((Collectors.toList()));

    list.forEach(token -> {
      Matcher m = pattern.matcher(token.getText());
      diagnostics.add(BSLDiagnostic.createDiagnostic(
        this,
        RangeHelper.newRange(token),
        getDiagnosticMessage(token.getText()))); // TODO: сомнительно?
    });

    return diagnostics;
  }

  private String getDiagnosticMessage(String tag) {
    String diagnosticMessage = getDiagnosticMessage();
    return String.format(diagnosticMessage, tag.replaceAll("\\s+",""));
  }
}
