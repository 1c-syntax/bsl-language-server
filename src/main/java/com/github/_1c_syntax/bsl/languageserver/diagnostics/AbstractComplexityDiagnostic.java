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

import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexityData;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexitySecondaryLocation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс диагностик превышения сложности метода и тела модуля.
 * <p>
 * Замечание ставится на имя метода (или на начало тела модуля), сложность которого больше порога;
 * связанная информация — места, увеличивающие сложность. Вид сложности и параметры задаёт
 * наследник: параметры диагностики объявляются в нём самом, потому что значения по умолчанию у
 * видов сложности разные.
 */
public abstract class AbstractComplexityDiagnostic extends AbstractVisitorDiagnostic {

  private boolean fileCodeBlockChecked;

  /**
   * Сложность методов и тела модуля текущего документа.
   */
  protected abstract ComplexityData complexityData();

  /**
   * Порог: замечание ставится при сложности строго больше него.
   */
  protected abstract int complexityThreshold();

  /**
   * Проверять ли сложность тела модуля (кода вне методов).
   */
  protected abstract boolean checkModuleBody();

  /**
   * Добавлять ли первой связанной информацией у метода само имя метода с текстом замечания.
   */
  protected abstract boolean relateMethodName();

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    documentContext.getSymbolTree().getMethodSymbol(ctx).ifPresent((MethodSymbol methodSymbol) -> {
      Integer methodComplexity = complexityData().methodsComplexity().get(methodSymbol);

      if (methodComplexity > complexityThreshold()) {
        var message = info.getMessage(methodSymbol.getName(), methodComplexity, complexityThreshold());
        var relatedInformation = new ArrayList<DiagnosticRelatedInformation>();
        if (relateMethodName()) {
          relatedInformation.add(RelatedInformation.create(
            documentContext.getUri(),
            methodSymbol.getSubNameRange(),
            message
          ));
        }
        addSecondaryLocations(
          relatedInformation,
          complexityData().methodsComplexitySecondaryLocations().get(methodSymbol)
        );

        diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(), message, relatedInformation);
      }
    });
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext ctx) {
    checkFileCodeBlock(ctx);
    fileCodeBlockChecked = ctx.getChildCount() > 0;
    return ctx;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext ctx) {
    if (!fileCodeBlockChecked) {
      checkFileCodeBlock(ctx);
    }
    return ctx;
  }

  private void checkFileCodeBlock(ParserRuleContext ctx) {
    if (!checkModuleBody()) {
      return;
    }

    Integer fileCodeBlockComplexity = complexityData().fileCodeBlockComplexity();

    if (fileCodeBlockComplexity > complexityThreshold()) {
      var message = info.getMessage("body", fileCodeBlockComplexity, complexityThreshold());
      var relatedInformation = new ArrayList<DiagnosticRelatedInformation>();
      relatedInformation.add(RelatedInformation.create(
        documentContext.getUri(),
        Ranges.create(ctx.getStart()),
        message
      ));
      addSecondaryLocations(relatedInformation, complexityData().fileBlockComplexitySecondaryLocations());

      diagnosticStorage.addDiagnostic(ctx.getStart(), message, relatedInformation);
    }
  }

  private void addSecondaryLocations(
    List<DiagnosticRelatedInformation> relatedInformation,
    List<ComplexitySecondaryLocation> secondaryLocations
  ) {
    secondaryLocations.forEach(secondaryLocation -> relatedInformation.add(RelatedInformation.create(
      documentContext.getUri(),
      secondaryLocation.range(),
      secondaryLocation.message()
    )));
  }
}
