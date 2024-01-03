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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.support.ReturnValueReuse;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  modules = {
    ModuleType.CommonModule,
    ModuleType.ObjectModule,
    ModuleType.ManagerModule,
    ModuleType.FormModule,
    ModuleType.RecordSetModule
  },
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.CLUMSY
  },
  scope = DiagnosticScope.BSL
)
public class RedundantAccessToObjectDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern PATTERN = CaseInsensitivePattern.compile("^ЭтотОбъект|ThisObject");
  private static final Pattern PATTERN_WITH_DOT = CaseInsensitivePattern.compile("^(ЭтотОбъект|ThisObject)\\..*");

  private static final boolean CHECK_OBJECT_MODULE = true;
  private static final boolean CHECK_FORM_MODULE = true;
  private static final boolean CHECK_RECORD_SET_MODULE = true;

  private boolean needCheckName;
  private boolean skipLValue;
  private Pattern namePatternWithDot;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_OBJECT_MODULE
  )
  private boolean checkObjectModule = CHECK_OBJECT_MODULE;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_FORM_MODULE
  )
  private boolean checkFormModule = CHECK_FORM_MODULE;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_RECORD_SET_MODULE
  )
  private boolean checkRecordSetModule = CHECK_RECORD_SET_MODULE;

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    var typeModule = documentContext.getModuleType();
    if (typeModule == ModuleType.CommonModule || typeModule == ModuleType.ManagerModule) {
      documentContext.getMdObject().ifPresent((MD mdo) -> {
        needCheckName = !(mdo instanceof CommonModule commonModule)
          || commonModule.getReturnValuesReuse() == ReturnValueReuse.DONT_USE;

        skipLValue = true;
        namePatternWithDot = CaseInsensitivePattern.compile(
          String.format(getManagerModuleName(mdo.getMdoType()), mdo.getName())
        );
      });
    }

    if (skipModule(typeModule)) {
      return new ArrayList<>();
    }
    return super.getDiagnostics(documentContext);
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    if (ctx.globalMethodCall() != null && ctx.getStart() == ctx.globalMethodCall().getStart()) {
      return super.visitCallStatement(ctx);
    }

    if (PATTERN_WITH_DOT.matcher(ctx.getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    if (needCheckName && namePatternWithDot.matcher(ctx.getText()).matches()) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    var identifier = ctx.IDENTIFIER();
    var modifiers = ctx.modifier();

    if (identifier == null || modifiers.isEmpty()) {
      return ctx;
    }

    if (
      PATTERN.matcher(identifier.getText()).matches()
        && modifiers.get(0) != null
        && modifiers.get(0).accessIndex() == null
    ) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    return ctx;
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {
    if (skipLValue) {
      return ctx;
    }

    var identifier = ctx.IDENTIFIER();
    var acceptor = ctx.acceptor();

    if (identifier == null || acceptor == null) {
      return ctx;
    }

    if (
      PATTERN.matcher(identifier.getText()).matches()
        && notHasAccessIndex(acceptor)
        && hasAccessProperty(acceptor)
    ) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    return ctx;
  }

  private static String getManagerModuleName(MDOType objectType) {
    if (objectType == MDOType.CATALOG) {
      return "^(Справочники|Catalogs)\\.%s\\..*";
    } else if (objectType == MDOType.DOCUMENT) {
      return "^(Документы|Documents)\\.%s\\..*";
    } else if (objectType == MDOType.ACCOUNTING_REGISTER) {
      return "^(РегистрыБухгалтерии|AccountingRegisters)\\.%s\\..*";
    } else if (objectType == MDOType.ACCUMULATION_REGISTER) {
      return "^(РегистрыНакопления|AccumulationRegisters)\\.%s\\..*";
    } else if (objectType == MDOType.CALCULATION_REGISTER) {
      return "^(РегистрыРасчета|CalculationRegisters)\\.%s\\..*";
    } else if (objectType == MDOType.INFORMATION_REGISTER) {
      return "^(РегистрыСведений|InformationRegisters)\\.%s\\..*";
    } else {
      return "^%s\\..*";
    }
  }

  private boolean skipModule(ModuleType typeModule) {
    return typeModule == ModuleType.ObjectModule && !checkObjectModule
      || typeModule == ModuleType.RecordSetModule && !checkRecordSetModule
      || typeModule == ModuleType.FormModule && !checkFormModule;
  }

  private static boolean notHasAccessIndex(BSLParser.AcceptorContext acceptor) {
    var modifiers = acceptor.modifier();
    return modifiers == null
      || modifiers.isEmpty()
      || modifiers.get(0) == null
      || modifiers.get(0).accessIndex() == null;
  }

  private static boolean hasAccessProperty(BSLParser.AcceptorContext acceptor) {
    return acceptor.accessProperty() != null;
  }
}
