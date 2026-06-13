/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.BslContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.UNUSED
  }
)
public class UnusedLocalMethodDiagnostic extends AbstractDiagnostic {

  private static final Pattern HANDLER_PATTERN = CaseInsensitivePattern.compile(
    "(ПриСозданииОбъекта|OnObjectCreate)"
  );

  /**
   * Префиксы подключаемых методов
   */
  private static final String ATTACHABLE_METHOD_PREFIXES = "подключаемый_,attachable_";

  private static final Set<AnnotationKind> EXTENSION_ANNOTATIONS = EnumSet.of(
    AnnotationKind.AFTER,
    AnnotationKind.AROUND,
    AnnotationKind.BEFORE,
    AnnotationKind.CHANGEANDVALIDATE
  );
  private static final boolean CHECK_OBJECT_MODULE = false;

  private final ReferenceIndex referenceIndex;
  private final EventContractsIndex eventContractsIndex;
  private final boolean hbkLoaded;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ATTACHABLE_METHOD_PREFIXES
  )
  private Pattern attachableMethodPrefixes = DiagnosticHelper.createPatternFromString(ATTACHABLE_METHOD_PREFIXES);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_OBJECT_MODULE
  )
  private boolean checkObjectModule = CHECK_OBJECT_MODULE;

  public UnusedLocalMethodDiagnostic(ReferenceIndex referenceIndex,
                                     EventContractsIndex eventContractsIndex,
                                     BslContextHolder bslContextHolder) {
    this.referenceIndex = referenceIndex;
    this.eventContractsIndex = eventContractsIndex;
    // BslContextHolder в поле не нужен — HBK либо есть на момент инжекции
    // диагностики, либо нет; ленивая проверка через .get() не нужна.
    this.hbkLoaded = bslContextHolder.get().isPresent();
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    this.attachableMethodPrefixes = DiagnosticHelper.createPatternFromString(
      (String) configuration.getOrDefault("attachableMethodPrefixes", ATTACHABLE_METHOD_PREFIXES));

    this.checkObjectModule = (boolean) configuration.getOrDefault("checkObjectModule", CHECK_OBJECT_MODULE);
  }

  @Override
  public void check() {
    var moduleType = documentContext.getModuleType();
    // Формы пока вне зоны действия: обработчики декларируются в Form.xml
    // блоком <Events>, без отдельной поддержки их не отличить от «забытых» методов.
    if (moduleType == ModuleType.FormModule) {
      return;
    }
    // CommonModule (нет событий) и OScriptClass (события захардкожены в резолвере)
    // диагностируем всегда — HBK для них не требуется.
    if (moduleType == ModuleType.CommonModule || moduleType == ModuleType.OScriptClass) {
      reportUnused();
      return;
    }
    // С HBK обработчики корректно отсекаются через EventContractsIndex — работаем
    // во всех остальных модулях кроме форм.
    if (hbkLoaded) {
      reportUnused();
      return;
    }
    // Без HBK — старое поведение: только ObjectModule по флагу.
    if (moduleType == ModuleType.ObjectModule && checkObjectModule) {
      reportUnused();
    }
  }

  private void reportUnused() {
    documentContext.getSymbolTree().getMethods()
      .stream()
      .filter(method -> !method.isExport())
      .filter(method -> !isOverride(method))
      .filter(method -> !isAttachable(method))
      .filter(method -> !isHandler(method))
      // Платформенный обработчик события (резолвится EventHandlerResolver'ом
      // по имени метода в object/manager/recordset/global/OScript-модулях).
      // Он вызывается платформой по триггеру события, в теле модуля никаких
      // вызовов не будет.
      .filter(method -> eventContractsIndex.getContract(documentContext, method.getName()).isEmpty())
      .filter(method -> referenceIndex.getReferencesTo(method).isEmpty())
      .forEach(method -> diagnosticStorage.addDiagnostic(method.getSubNameRange(), info.getMessage(method.getName())));
  }

  private boolean isAttachable(MethodSymbol methodSymbol) {
    return attachableMethodPrefixes.matcher(methodSymbol.getName()).matches();
  }

  private static boolean isHandler(MethodSymbol methodSymbol) {
    return HANDLER_PATTERN.matcher(methodSymbol.getName()).matches();
  }

  private static boolean isOverride(MethodSymbol method) {
    return method.getAnnotations()
      .stream()
      .map(Annotation::getKind)
      .anyMatch(EXTENSION_ANNOTATIONS::contains);
  }
}
