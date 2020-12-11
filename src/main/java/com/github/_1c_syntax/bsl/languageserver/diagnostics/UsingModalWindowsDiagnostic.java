/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.UseMode;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3
)
public class UsingModalWindowsDiagnostic extends AbstractVisitorDiagnostic {

  private final Pattern modalityMethods = CaseInsensitivePattern.compile(
    "(ВОПРОС|DOQUERYBOX|ОТКРЫТЬФОРМУМОДАЛЬНО|OPENFORMMODAL|ОТКРЫТЬЗНАЧЕНИЕ|OPENVALUE|" +
      "ПРЕДУПРЕЖДЕНИЕ|DOMESSAGEBOX|ВВЕСТИДАТУ|INPUTDATE|ВВЕСТИЗНАЧЕНИЕ|INPUTVALUE|" +
      "ВВЕСТИСТРОКУ|INPUTSTRING|ВВЕСТИЧИСЛО|INPUTNUMBER|УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ|INSTALLADDIN|" +
      "УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|INSTALLFILESYSTEMEXTENSION|" +
      "УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|INSTALLCRYPTOEXTENSION|ПОМЕСТИТЬФАЙЛ|PUTFILE)"
  );

  private final HashMap<String, String> pairMethods = new HashMap<>();

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "false"
  )
  private boolean forceModalityMode;

  public UsingModalWindowsDiagnostic() {
    pairMethods.put("ВОПРОС", "ПоказатьВопрос");
    pairMethods.put("DOQUERYBOX", "ShowQueryBox");
    pairMethods.put("ОТКРЫТЬФОРМУМОДАЛЬНО", "ОткрытьФорму");
    pairMethods.put("OPENFORMMODAL", "OpenForm");
    pairMethods.put("ОТКРЫТЬЗНАЧЕНИЕ", "ПоказатьЗначение");
    pairMethods.put("OPENVALUE", "ShowValue");
    pairMethods.put("ПРЕДУПРЕЖДЕНИЕ", "ПоказатьПредупреждение");
    pairMethods.put("DOMESSAGEBOX", "ShowMessageBox");
    pairMethods.put("ВВЕСТИДАТУ", "ПоказатьВводДаты");
    pairMethods.put("INPUTDATE", "ShowInputDate");
    pairMethods.put("ВВЕСТИЗНАЧЕНИЕ", "ПоказатьВводЗначения");
    pairMethods.put("INPUTVALUE", "ShowInputValue");
    pairMethods.put("ВВЕСТИСТРОКУ", "ПоказатьВводСтроки");
    pairMethods.put("INPUTSTRING", "ShowInputString");
    pairMethods.put("ВВЕСТИЧИСЛО", "ПоказатьВводЧисла");
    pairMethods.put("INPUTNUMBER", "ShowInputNumber");
    pairMethods.put("УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ", "НачатьУстановкуВнешнейКомпоненты");
    pairMethods.put("INSTALLADDIN", "BeginInstallAddIn");
    pairMethods.put("УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ", "НачатьУстановкуРасширенияРаботыСФайлами");
    pairMethods.put("INSTALLFILESYSTEMEXTENSION", "BeginInstallFileSystemExtension");
    pairMethods.put("УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ", "НачатьУстановкуРасширенияРаботыСКриптографией");
    pairMethods.put("INSTALLCRYPTOEXTENSION", "BeginInstallCryptoExtension");
    pairMethods.put("ПОМЕСТИТЬФАЙЛ", "НачатьПомещениеФайла");
    pairMethods.put("PUTFILE", "BeginPutFile");
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var configuration = documentContext.getServerContext().getConfiguration();
    // если использование модальных окон разрешено (без предупреждение) 
    // и не установлен флаг игнорирования использования модальных окон, то
    // ничего не диагностируется
    if (!forceModalityMode && configuration.getModalityUseMode() == UseMode.USE) {
      return ctx;
    }

    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    String methodName = ctx.methodName().getText();
    if (modalityMethods.matcher(methodName).matches()) {
      diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(methodName, pairMethods.get(methodName.toUpperCase(Locale.ENGLISH))));
    }
    return super.visitGlobalMethodCall(ctx);
  }
}
