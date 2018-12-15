/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.parser.BSLLexer;
import org.github._1c_syntax.parser.BSLParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

abstract class AbstractDiagnosticTest<T extends BSLDiagnostic> {


  private final T diagnostic;

  AbstractDiagnosticTest(Class<T> diagnosticClass) {
    try {
      diagnostic = diagnosticClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      e.printStackTrace();
      throw new RuntimeException("Diagnostic instantiate error", e);
    }
  }

  T getDiagnosticInstance() {
    return diagnostic;
  }

  List<Diagnostic> getDiagnostics() throws IOException {
    String textDocumentContent = IOUtils.resourceToString(
      "diagnostics/" + diagnostic.getClass().getSimpleName() + ".bsl",
      Charset.forName("UTF-8"),
      this.getClass().getClassLoader()
    );
    BSLLexer lexer = new BSLLexer(CharStreams.fromString(textDocumentContent));

    CommonTokenStream tokens = new CommonTokenStream(lexer);

    BSLParser parser = new BSLParser(tokens);

    return diagnostic.getDiagnostics(parser.file());
  }

}
