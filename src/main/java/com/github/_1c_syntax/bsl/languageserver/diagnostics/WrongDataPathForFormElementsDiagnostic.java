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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.storage.form.FormItem;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Range;

import java.util.function.Predicate;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 5,
  scope = DiagnosticScope.BSL,
  modules = {
    // todo переделать, когда появится привязка к объектам метаданных
    ModuleType.FormModule,
    ModuleType.ManagedApplicationModule
  },
  tags = {
    DiagnosticTag.UNPREDICTABLE,
  }
)
public class WrongDataPathForFormElementsDiagnostic extends AbstractDiagnostic {

  private Range diagnosticRange;

  @Override
  protected void check() {
    var range = documentContext.getSymbolTree().getModule().getSelectionRange();
    if (!Ranges.isEmpty(range)) {
      checkCurrentModule(range);
    }
  }

  private static boolean wrongDataPath(FormItem formItem) {
    return formItem.getDataPath().startsWith("~");
  }

  private static boolean haveFormModules(Form form) {
    return !form.getModules().isEmpty();
  }

  private void checkCurrentModule(Range range) {
    diagnosticRange = range;

    if (documentContext.getModuleType() == ModuleType.FormModule) {
      checkMdoObjectStream(WrongDataPathForFormElementsDiagnostic::haveFormModules,
        documentContext.getMdObject().stream());
    } else {
      checkAllFormsWithoutModules();
    }
  }

  private void checkAllFormsWithoutModules() {
    checkMdoObjectStream(form -> !haveFormModules(form),
      documentContext.getServerContext().getConfiguration().getPlainChildren().stream());
  }

  private void checkMdoObjectStream(Predicate<Form> formFilter, Stream<MD> stream) {
    stream
      .filter(Form.class::isInstance)
      .map(Form.class::cast)
      .filter(formFilter)
      .forEach(this::checkForm);
  }

  private void checkForm(Form form) {
    var formData = form.getData();
    if (formData.isEmpty()) {
      return;
    }
    formData.getPlainItems()
      .stream()
      .filter(WrongDataPathForFormElementsDiagnostic::wrongDataPath)
      .forEach(formItem -> diagnosticStorage.addDiagnostic(diagnosticRange,
        info.getMessage(formItem.getName(), getMdoRef(form))));
  }

  private String getMdoRef(Form form) {
    return form.getMdoReference().getMdoRef(documentContext.getServerContext().getConfiguration().getScriptVariant());
  }
}
