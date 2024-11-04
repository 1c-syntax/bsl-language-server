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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.support.UseMode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
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
public class UsingSynchronousCallsDiagnostic extends AbstractVisitorDiagnostic {
  private static final Pattern MODALITY_METHODS = CaseInsensitivePattern.compile(
    "(ВОПРОС|DOQUERYBOX|ОТКРЫТЬФОРМУМОДАЛЬНО|OPENFORMMODAL|ОТКРЫТЬЗНАЧЕНИЕ|OPENVALUE|" +
      "ПРЕДУПРЕЖДЕНИЕ|DOMESSAGEBOX|ВВЕСТИДАТУ|INPUTDATE|ВВЕСТИЗНАЧЕНИЕ|INPUTVALUE|" +
      "ВВЕСТИСТРОКУ|INPUTSTRING|ВВЕСТИЧИСЛО|INPUTNUMBER|УСТАНОВИТЬВНЕШНЮЮКОМПОНЕНТУ|INSTALLADDIN|" +
      "УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|INSTALLFILESYSTEMEXTENSION|" +
      "УСТАНОВИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|INSTALLCRYPTOEXTENSION|" +
      "ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ|ATTACHCRYPTOEXTENSION|" +
      "ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ|ATTACHFILESYSTEMEXTENSION|ПОМЕСТИТЬФАЙЛ|PUTFILE|" +
      "КОПИРОВАТЬФАЙЛ|FILECOPY|ПЕРЕМЕСТИТЬФАЙЛ|MOVEFILE|НАЙТИФАЙЛЫ|FINDFILES|УДАЛИТЬФАЙЛЫ|DELETEFILES|" +
      "СОЗДАТЬКАТАЛОГ|CREATEDIRECTORY|КАТАЛОГВРЕМЕННЫХФАЙЛОВ|TEMPFILESDIR|КАТАЛОГДОКУМЕНТОВ|DOCUMENTSDIR|" +
      "РАБОЧИЙКАТАЛОГДАННЫХПОЛЬЗОВАТЕЛЯ|USERDATAWORKDIR|ПОЛУЧИТЬФАЙЛЫ|GETFILES|ПОМЕСТИТЬФАЙЛЫ|PUTFILES|" +
      "ЗАПРОСИТЬРАЗРЕШЕНИЕПОЛЬЗОВАТЕЛЯ|REQUESTUSERPERMISSION|ЗАПУСТИТЬПРИЛОЖЕНИЕ|RUNAPP)"
  );

  private static final Set<CompilerDirectiveKind> serverCompilerDirectives =
    EnumSet.of(CompilerDirectiveKind.AT_SERVER, CompilerDirectiveKind.AT_SERVER_NO_CONTEXT);

  private final HashMap<String, String> pairMethods = new HashMap<>();

  public UsingSynchronousCallsDiagnostic() {
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
    pairMethods.put("ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСКРИПТОГРАФИЕЙ", "НачатьПодключениеРасширенияРаботыСКриптографией");
    pairMethods.put("ATTACHCRYPTOEXTENSION", "BeginAttachingCryptoExtension");
    pairMethods.put("ПОДКЛЮЧИТЬРАСШИРЕНИЕРАБОТЫСФАЙЛАМИ", "НачатьПодключениеРасширенияРаботыСФайлами");
    pairMethods.put("ATTACHFILESYSTEMEXTENSION", "BeginAttachingFileSystemExtension");
    pairMethods.put("ПОМЕСТИТЬФАЙЛ", "НачатьПомещениеФайла");
    pairMethods.put("PUTFILE", "BeginPutFile");
    pairMethods.put("КОПИРОВАТЬФАЙЛ", "НачатьКопированиеФайла");
    pairMethods.put("FILECOPY", "BeginCopyingFile");
    pairMethods.put("ПЕРЕМЕСТИТЬФАЙЛ", "НачатьПеремещениеФайла");
    pairMethods.put("MOVEFILE", "BeginMovingFile");
    pairMethods.put("НАЙТИФАЙЛЫ", "НачатьПоискФайлов");
    pairMethods.put("FINDFILES", "BeginFindingFiles");
    pairMethods.put("УДАЛИТЬФАЙЛЫ", "НачатьУдалениеФайлов");
    pairMethods.put("DELETEFILES", "BeginDeletingFiles");
    pairMethods.put("СОЗДАТЬКАТАЛОГ", "НачатьСозданиеКаталога");
    pairMethods.put("CREATEDIRECTORY", "BeginCreatingDirectory");
    pairMethods.put("КАТАЛОГВРЕМЕННЫХФАЙЛОВ", "НачатьПолучениеКаталогаВременныхФайлов");
    pairMethods.put("TEMPFILESDIR", "BeginGettingTempFilesDir");
    pairMethods.put("КАТАЛОГДОКУМЕНТОВ", "НачатьПолучениеКаталогаДокументов");
    pairMethods.put("DOCUMENTSDIR", "BeginGettingDocumentsDir");
    pairMethods.put("РАБОЧИЙКАТАЛОГДАННЫХПОЛЬЗОВАТЕЛЯ", "НачатьПолучениеРабочегоКаталогаДанныхПользователя");
    pairMethods.put("USERDATAWORKDIR", "BeginGettingUserDataWorkDir");
    pairMethods.put("ПОЛУЧИТЬФАЙЛЫ", "НачатьПолучениеФайлов");
    pairMethods.put("GETFILES", "BeginGettingFiles");
    pairMethods.put("ПОМЕСТИТЬФАЙЛЫ", "НачатьПомещениеФайлов");
    pairMethods.put("PUTFILES", "BeginPuttingFiles");
    pairMethods.put("ЗАПРОСИТЬРАЗРЕШЕНИЕПОЛЬЗОВАТЕЛЯ", "НачатьЗапросРазрешенияПользователя");
    pairMethods.put("REQUESTUSERPERMISSION", "BeginRequestingUserPermission");
    pairMethods.put("ЗАПУСТИТЬПРИЛОЖЕНИЕ", "НачатьЗапускПриложения");
    pairMethods.put("RUNAPP", "BeginRunningApplication");
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    var configuration = documentContext.getServerContext().getConfiguration();
    // если использование синхронных вызовов разрешено (без предупреждения), то
    // ничего не диагностируется
    if (configuration instanceof Configuration cf && cf.getSynchronousExtensionAndAddInCallUseMode() == UseMode.USE) {
      return ctx;
    }

    if (isServerModule(documentContext)) {
      return ctx;
    }

    return super.visitFile(ctx);
  }

  private static boolean isServerModule(DocumentContext documentContext) {
    return switch (documentContext.getModuleType()) {
      case ApplicationModule, CommandModule, FormModule, ManagedApplicationModule -> false;
      case CommonModule -> isServerCommonModule(documentContext);
      default -> true; // Все прочие модули это строго серверные и в них синхронные вызовы разрешены
    };
  }

  private static boolean isServerCommonModule(DocumentContext documentContext) {
    var mdObject = documentContext.getMdObject();

    return mdObject.map(CommonModule.class::cast)
      .filter(commonModule -> !(commonModule.isClientManagedApplication() ||
                                commonModule.isClientOrdinaryApplication()))
      .isPresent();
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol(ctx);
    if (methodSymbol.isPresent()) {
      var compilerDirective = methodSymbol.get().getCompilerDirectiveKind();
      if (compilerDirective.isPresent() && serverCompilerDirectives.contains(compilerDirective.get())) {
        return ctx;
      }
    }

    return super.visitSub(ctx);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    String methodName = ctx.methodName().getText();
    if (MODALITY_METHODS.matcher(methodName).matches()) {
      diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(methodName, pairMethods.get(methodName.toUpperCase(Locale.ENGLISH))));
    }
    return super.visitGlobalMethodCall(ctx);
  }
}
