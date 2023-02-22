/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.InitializeParams;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RunAllTestsCodeLensSupplier
  implements CodeLensSupplier<DefaultCodeLensData> {

  private final TestRunnerAdapter testRunnerAdapter;
  private final LanguageServerConfiguration configuration;
  private final Resources resources;

  private boolean clientIsSupported;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Анализирует параметры запроса и подготавливает данные для слежения за родительским процессом.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerInitializeRequestReceivedEvent event) {
    var clientName = Optional.of(event)
      .map(LanguageServerInitializeRequestReceivedEvent::getParams)
      .map(InitializeParams::getClientInfo)
      .map(ClientInfo::getName)
      .orElse("");
    clientIsSupported = "Visual Studio Code".equals(clientName);
  }

  @Override
  public String getId() {
    return "language-1c-bsl.languageServer.runAllTests";
  }

  @Override
  public boolean isApplicable(DocumentContext documentContext) {
    return documentContext.getFileType() == FileType.OS && clientIsSupported;
  }

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {

    var testNames = testRunnerAdapter.getTestNames(documentContext);

    if (testNames.isEmpty()) {
      return Collections.emptyList();
    }

    var symbolTree = documentContext.getSymbolTree();
    var firstMethod = symbolTree.getMethods().get(0);

    return List.of(toCodeLens(firstMethod, documentContext));
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, DefaultCodeLensData data) {
    var path = Paths.get(documentContext.getUri());

    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();
    var executable = options.getExecutableForCurrentOS();
    String runText = executable + " " + options.getRunAllTestsArguments();
    runText = String.format(runText, path);

    var command = new Command();
    command.setTitle(resources.getResourceString(getClass(), "runAllTests"));
    command.setCommand(getId());
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);

    return unresolved;
  }

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
