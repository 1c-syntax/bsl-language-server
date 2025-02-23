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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


abstract class AbstractCommonModuleNameDiagnostic extends AbstractDiagnostic {

  protected Pattern pattern;
  private final LanguageServerConfiguration serverConfiguration;

  protected AbstractCommonModuleNameDiagnostic(LanguageServerConfiguration serverConfiguration, String regexp) {
    this.serverConfiguration = serverConfiguration;
    this.pattern = CaseInsensitivePattern.compile(regexp);
  }

  @Override
  protected void check() {
    var range = documentContext.getSymbolTree().getModule().getSelectionRange();
    if (Ranges.isEmpty(range)) {
      return;
    }

    documentContext.getMdObject()
      .filter(CommonModule.class::isInstance)
      .map(CommonModule.class::cast)
      .filter(this::flagsCheck)
      .map(CommonModule::getName)
      .map(pattern::matcher)
      .filter(this::matchCheck)
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(range));
  }

  protected abstract boolean flagsCheck(CommonModule commonModule);

  protected boolean matchCheck(Matcher matcher) {
    return !matcher.find();
  }

  protected boolean isClientServer(CommonModule commonModule) {
    return !commonModule.isServerCall()
      && commonModule.isServer()
      && commonModule.isExternalConnection()
      && isClientApplication(commonModule);
  }

  protected boolean isClient(CommonModule commonModule) {
    return !commonModule.isServerCall()
      && !commonModule.isServer()
      && !commonModule.isExternalConnection()
      && isClientApplication(commonModule);
  }

  protected boolean isServerCall(CommonModule commonModule) {
    return commonModule.isServerCall()
      && commonModule.isServer()
      && !commonModule.isExternalConnection()
      && !commonModule.isClientOrdinaryApplication()
      && !commonModule.isClientManagedApplication();
  }

  protected boolean isServer(CommonModule commonModule) {
    return !commonModule.isServerCall()
      && commonModule.isServer()
      && commonModule.isExternalConnection()
      && isClientOrdinaryAppIfNeed(commonModule)
      && !commonModule.isClientManagedApplication();
  }

  private boolean isClientApplication(CommonModule commonModule) {
    return isClientOrdinaryAppIfNeed(commonModule)
      && commonModule.isClientManagedApplication();
  }

  private boolean isClientOrdinaryAppIfNeed(CommonModule commonModule) {
    return commonModule.isClientOrdinaryApplication()
      || !serverConfiguration.getDiagnosticsOptions().isOrdinaryAppSupport();
  }
}
