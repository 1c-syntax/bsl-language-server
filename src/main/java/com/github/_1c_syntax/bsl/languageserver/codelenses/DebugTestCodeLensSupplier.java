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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@Order(3)
public class DebugTestCodeLensSupplier
  extends AbstractRunTestsCodeLensSupplier<DebugTestCodeLensSupplier.DebugTestCodeLensData> {

  private static final String COMMAND_ID = "language-1c-bsl.languageServer.debugTest";

  private final TestRunnerAdapter testRunnerAdapter;
  private final Resources resources;

  // Self-injection для работы кэша в базовом классе.
  @Autowired
  @Lazy
  @Getter
  @SuppressWarnings("NullAway.Init")
  private DebugTestCodeLensSupplier self;

  public DebugTestCodeLensSupplier(
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

    if (documentContext.getFileType() == FileType.BSL) {
      return Collections.emptyList();
    }

    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();

    if (options.getDebugTestArguments().isEmpty()) {
      return Collections.emptyList();
    }

    var testIds = testRunnerAdapter.getTestIds(documentContext);
    var symbolTree = documentContext.getSymbolTree();

    return testIds.stream()
      .map(symbolTree::getMethodSymbol)
      .flatMap(Optional::stream)
      .map(methodSymbol -> toCodeLens(methodSymbol, documentContext))
      .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<DebugTestCodeLensSupplier.DebugTestCodeLensData> getCodeLensDataClass() {
    return DebugTestCodeLensSupplier.DebugTestCodeLensData.class;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, DebugTestCodeLensData data) {

    var path = Paths.get(documentContext.getUri());
    var testId = data.getTestId();

    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();
    var executable = options.getExecutableForCurrentOS();
    String runText = executable + " " + options.getDebugTestArguments();
    runText = String.format(runText, path, testId);

    var command = new Command();
    command.setTitle(resources.getResourceString(getClass(), "title"));
    command.setCommand(COMMAND_ID);
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);

    return unresolved;

  }

  private CodeLens toCodeLens(MethodSymbol method, DocumentContext documentContext) {
    var testId = method.getName();
    var codeLensData = new DebugTestCodeLensSupplier.DebugTestCodeLensData(documentContext.getUri(), getId(), testId);

    var codeLens = new CodeLens(method.getSubNameRange());
    codeLens.setData(codeLensData);

    return codeLens;
  }

  /**
   * DTO для хранения данных линз для отладки теста.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class DebugTestCodeLensData extends DefaultCodeLensData {
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
    public DebugTestCodeLensData(URI uri, String id, String testId) {
      super(uri, id);
      this.testId = testId;
    }
  }

}
