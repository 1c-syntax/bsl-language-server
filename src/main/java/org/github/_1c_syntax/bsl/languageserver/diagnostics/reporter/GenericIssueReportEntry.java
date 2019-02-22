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
package org.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

//import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import javax.xml.soap.Text;

public class GenericIssueReportEntry {
  @Getter
  private final String engineId;
  @Getter
  private final String ruleId;
  @Getter
  private final String severity;
  @Getter
  private final String type;
  @Getter
  private final int effortMinutes;
  @Getter
  private final PrimaryLocation primaryLocation;

  public GenericIssueReportEntry(String fileName, Diagnostic diagnostic)
  {
    engineId = diagnostic.getSource();
    ruleId = diagnostic.getCode();
    severity = "CRITICAL";
    type = "BUG";
    primaryLocation = new PrimaryLocation(fileName, diagnostic);
    effortMinutes = 0;

  }

  static class PrimaryLocation {

    @Getter
    private final String message;
    @Getter
    private final String filePath;
    @Getter
    private final TextRange textRange;

    public PrimaryLocation()
    {
      message = "";
      filePath = "";
      textRange = new TextRange();
    }

    public PrimaryLocation(String filePath, Diagnostic diagnostic)
    {

      message = diagnostic.getMessage();
      this.filePath = filePath;
      textRange =  new TextRange(diagnostic.getRange());

    }

  }

  static class TextRange {

    @Getter
    private final int startLine;
    @Getter
    private final int endLine;
    @Getter
    private final int startColumn;
    @Getter
    private final int endColumn;

    private final int lag = 1;

    public TextRange()
    {
      startLine = 0;
      endLine = 0;
      startColumn = 0;
      endColumn = 0;
    }

    public TextRange(Range range)
    {

      int localstartColumn;

      Position startPosition = range.getStart();
      Position endPosition = range.getEnd();

      startLine = startPosition.getLine() + lag;
      endLine = endPosition.getLine() + lag;
      startColumn = startPosition.getCharacter() + lag;
      endColumn = endPosition.getCharacter(); // пока без лага

//      // костыль для проверки. Там есть startColumn = endColumn
//      localstartColumn = startPosition.getCharacter() + lag;
//      if (localstartColumn == endColumn)
//      {
//        localstartColumn = endColumn - 1;
//      }
//      startColumn = localstartColumn;

    }

  }

}
