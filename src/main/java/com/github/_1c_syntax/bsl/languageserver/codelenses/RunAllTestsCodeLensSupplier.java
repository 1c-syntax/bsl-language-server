/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.codelenses.testrunner.TestRunnerAdapter;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Поставщик линзы для запуска всех тестов в текущем файле.
 */
@Component
@Slf4j
public class RunAllTestsCodeLensSupplier
  extends AbstractRunTestsCodeLensSupplier<DefaultCodeLensData> {

  private static final String COMMAND_ID = "language-1c-bsl.languageServer.runAllTests";

  private final TestRunnerAdapter testRunnerAdapter;
  private final Resources resources;

  public RunAllTestsCodeLensSupplier(
    LanguageServerConfiguration configuration,
    TestRunnerAdapter testRunnerAdapter,
    Resources resources
  ) {
    super(configuration);
    this.testRunnerAdapter = testRunnerAdapter;
    this.resources = resources;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {

    var testIds = testRunnerAdapter.getTestIds(documentContext);

    if (testIds.isEmpty()) {
      return Collections.emptyList();
    }

    var symbolTree = documentContext.getSymbolTree();
    var firstMethod = symbolTree.getMethods().get(0);

    return List.of(toCodeLens(firstMethod, documentContext));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, DefaultCodeLensData data) {
    var path = Paths.get(documentContext.getUri());

    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();
    var executable = options.getExecutableForCurrentOS();
    String runText = executable + " " + options.getRunAllTestsArguments();
    runText = String.format(runText, path);

    var command = new Command();
    command.setTitle(resources.getResourceString(getClass(), "runAllTests"));
    command.setCommand(COMMAND_ID);
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);

    return unresolved;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<DefaultCodeLensData> getCodeLensDataClass() {
    return DefaultCodeLensData.class;
  }

  private CodeLens toCodeLens(MethodSymbol method, DocumentContext documentContext) {

    var codeLensData = new DefaultCodeLensData(documentContext.getUri(), getId());

    var codeLens = new CodeLens(method.getSubNameRange());
    codeLens.setData(codeLensData);

    return codeLens;
  }

}
