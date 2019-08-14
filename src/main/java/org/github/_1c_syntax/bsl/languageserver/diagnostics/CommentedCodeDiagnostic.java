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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.UnicodeBOMInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5
)
public class CommentedCodeDiagnostic extends AbstractVisitorDiagnostic {

  private static Pattern pattern = Pattern.compile("//");

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    List<List<Token>> commentGroups = groupComments(documentContext.getComments());
    commentGroups.forEach(this::checkCommentGroup);
    return diagnosticStorage.getDiagnostics();
  }

  private static List<List<Token>> groupComments(List<Token> comments) {
    List<List<Token>> groups = new ArrayList<>();
    List<Token> currentGroup = null;

    for(Token comment : comments) {
      if (currentGroup == null) {
        currentGroup = initNewGroup(comment);
      } else if (isAdjacent(comment, currentGroup)) {
        currentGroup.add(comment);
      } else {
        groups.add(currentGroup);
        currentGroup = initNewGroup(comment);
      }
    }

    if (currentGroup != null) {
      groups.add(currentGroup);
    }

    return groups;
  }

  private static List<Token> initNewGroup(Token comment) {
    List<Token> group = new ArrayList<>();
    group.add(comment);
    return group;
  }

  private static boolean isAdjacent(Token comment, List<Token> currentGroup) {
    return currentGroup.get(currentGroup.size() - 1).getLine() + 1 == comment.getLine();
  }

  private void checkCommentGroup(List<Token> commentGroup) {
    String groupText = uncomment(commentGroup);

    if (isTextParsedAsCode(groupText)) {
      addIssue(commentGroup);
    }
  }

  private static String uncomment(List<Token> commentGroup) {
    StringBuilder uncommentedText = new StringBuilder();
    for (Token comment : commentGroup) {
      String value = pattern.matcher(comment.getText()).replaceFirst("");
      uncommentedText.append("\n");
      uncommentedText.append(value);
    }
    return uncommentedText.toString().trim();
  }

  private static boolean isTextParsedAsCode(String text) {
    BSLParser.FileContext ast = computeAST(text);
    return ast.exception == null;
  }

  private static BSLParser.FileContext computeAST(String text) {
    CharStream input;

    try (InputStream inputStream = IOUtils.toInputStream(text, StandardCharsets.UTF_8);
         UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(inputStream)
    ) {

      ubis.skipBOM();

      input = CharStreams.fromStream(ubis, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    BSLLexer lexer = new BSLLexer(input);

    lexer.setInputStream(input);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);

    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    tokenStream.fill();

    BSLParser parser = new BSLParser(tokenStream);
    parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
    return parser.file();
  }

  private void addIssue(List<Token> commentGroup) {
    Token first = commentGroup.get(0);
    Token last = commentGroup.get(commentGroup.size() - 1);
    diagnosticStorage.addDiagnostic(
      first.getLine() - 1,
      first.getCharPositionInLine(),
      last.getLine() - 1,
      last.getCharPositionInLine() + last.getText().length()
    );
  }
}
