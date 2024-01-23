/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Поставщик линз для запуска теста по конкретному тестовому методу.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunTestCodeLensSupplier
  extends AbstractRunTestsCodeLensSupplier<RunTestCodeLensSupplier.RunTestCodeLensData> {

  private static final String COMMAND_ID = "language-1c-bsl.languageServer.runTest";

  private final TestRunnerAdapter testRunnerAdapter;
  private final LanguageServerConfiguration configuration;
  private final Resources resources;

  /**
   * {@inheritDoc}
   */
  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {

    if (documentContext.getFileType() == FileType.BSL) {
      return Collections.emptyList();
    }

    var testIds = testRunnerAdapter.getTestIds(documentContext);
    var symbolTree = documentContext.getSymbolTree();

    return testIds.stream()
      .map(symbolTree::getMethodSymbol)
      .flatMap(Optional::stream)
      .map(methodSymbol -> toCodeLens(methodSymbol, documentContext))
      .collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<RunTestCodeLensData> getCodeLensDataClass() {
    return RunTestCodeLensData.class;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, RunTestCodeLensData data) {

    var path = Paths.get(documentContext.getUri());
    var testId = data.getTestId();

    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();
    var executable = options.getExecutableForCurrentOS();
    String runText = executable + " " + options.getRunTestArguments();
    runText = String.format(runText, path, testId);

    var command = new Command();
    command.setTitle(resources.getResourceString(getClass(), "runTest"));
    command.setCommand(COMMAND_ID);
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);

    return unresolved;
  }

  private CodeLens toCodeLens(MethodSymbol method, DocumentContext documentContext) {
    var testId = method.getName();
    var codeLensData = new RunTestCodeLensData(documentContext.getUri(), getId(), testId);

    var codeLens = new CodeLens(method.getSubNameRange());
    codeLens.setData(codeLensData);

    return codeLens;
  }

  /**
   * DTO для хранения данных линз о сложности методов в документе.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class RunTestCodeLensData extends DefaultCodeLensData {
    /**
     * Имя метода.
     */
    String testId;

    /**
     * @param uri    URI документа.
     * @param id     Идентификатор линзы.
     * @param testId Идентификатор теста.
     */
    @ConstructorProperties({"uri", "id", "testId"})
    public RunTestCodeLensData(URI uri, String id, String testId) {
      super(uri, id);
      this.testId = testId;
    }
  }
}
