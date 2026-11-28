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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.utils.Positions;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Сигнатура метода-обработчика платформенного события не соответствует
 * контракту события: расходится количество обязательных параметров либо имена
 * не совпадают.
 * <p>
 * Если платформа дёргает обработчик с N параметрами, а в коде объявлено
 * меньше — runtime даст ошибку или параметр будет «обрезан». Сравнение идёт
 * с первой (основной) сигнатурой события из bsl-context.
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.STANDARD
  }
)
public class EventHandlerInvalidSignatureDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private final EventContractsIndex eventContractsIndex;

  public EventHandlerInvalidSignatureDiagnostic(EventContractsIndex eventContractsIndex) {
    this.eventContractsIndex = eventContractsIndex;
  }

  @Override
  public void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .filter(EventMethodSymbol.class::isInstance)
      .forEach(method ->
        eventContractsIndex.getContract(documentContext, method.getName())
          .map(MemberDescriptor::signatures)
          .filter(signatures -> !signatures.isEmpty())
          .ifPresent(signatures -> checkSignature(method, signatures)));
  }

  /**
   * Сравнивает арность метода с допустимыми диапазонами параметров событий.
   * Варианты сигнатур у одного события встречаются у форм (с/без доп.
   * параметров расширения) — для целей валидации достаточно совпадения
   * с любой из них.
   */
  private void checkSignature(MethodSymbol method, List<SignatureDescriptor> contractSignatures) {
    var requiredCounts = contractSignatures.stream()
      .mapToInt(sig -> (int) sig.parameters().stream().filter(p -> !p.optional()).count())
      .toArray();
    // Вариадик-хвост (например, конструктор OneScript-класса ПриСозданииОбъекта)
    // принимает переменное число аргументов — верхняя граница не ограничена.
    var totalCounts = contractSignatures.stream()
      .mapToInt(sig -> hasVariadicTail(sig)
        ? Integer.MAX_VALUE
        : sig.parameters().size())
      .toArray();
    int userParamCount = method.getParameters().size();
    if (fitsAnySignature(userParamCount, requiredCounts, totalCounts)) {
      return;
    }
    // Подставим максимальное число параметров среди вариантов сигнатур —
    // это самая «строгая» формулировка ожидаемого контракта.
    int expected = Arrays.stream(totalCounts).max().orElse(0);
    diagnosticStorage.addDiagnostic(method.getSubNameRange(),
      info.getMessage(method.getName(), expected, userParamCount));
  }

  private static boolean hasVariadicTail(SignatureDescriptor signature) {
    var parameters = signature.parameters();
    return !parameters.isEmpty() && parameters.get(parameters.size() - 1).variadic();
  }

  private static boolean fitsAnySignature(int userParamCount, int[] requiredCounts, int[] totalCounts) {
    for (var i = 0; i < requiredCounts.length; i++) {
      if (userParamCount >= requiredCounts[i] && userParamCount <= totalCounts[i]) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<CodeAction> getQuickFixes(List<Diagnostic> diagnostics,
                                        CodeActionParams params,
                                        DocumentContext documentContext) {
    return QuickFixProvider.fixEach(diagnostics, info.getResourceString("quickFixMessage"), documentContext.getUri(),
      diagnostic -> documentContext.getSymbolTree().getMethodSymbolAtPosition(diagnostic.getRange().getStart())
        .flatMap(method -> eventContractsIndex.getContract(documentContext, method.getName())
          .filter(contract -> !contract.signatures().isEmpty())
          .flatMap(contract -> buildSignatureFix(documentContext, method, contract)))
        .map(List::of)
        .orElse(List.of()));
  }

  /**
   * Считает целевой список имён параметров (из метода — где совпадает по
   * позиции, из контракта — для добавляемых) и возвращает {@link TextEdit}
   * с подменой блока {@code (...)} в сигнатуре метода.
   */
  private Optional<TextEdit> buildSignatureFix(DocumentContext documentContext,
                                                MethodSymbol method,
                                                MemberDescriptor contract) {
    var contractParams = contract.signatures().get(0).parameters();
    var methodParams = method.getParameters();
    var targetSize = contractParams.size();
    var scriptLanguage = documentContext.getScriptVariantLanguage();
    var names = new ArrayList<String>(targetSize);
    for (var i = 0; i < targetSize; i++) {
      if (i < methodParams.size() && !methodParams.get(i).getName().isBlank()) {
        names.add(methodParams.get(i).getName());
      } else {
        names.add(pickParamName(contractParams.get(i), i, scriptLanguage));
      }
    }
    var replacement = String.join(", ", names);
    return locateParamListRange(documentContext, method)
      .map(range -> new TextEdit(range, replacement));
  }

  private String pickParamName(ParameterDescriptor descriptor, int index, Language language) {
    var preferEn = language == Language.EN;
    var first = preferEn ? descriptor.bilingualName().en() : descriptor.bilingualName().ru();
    if (!first.isBlank()) {
      return first;
    }
    var second = preferEn ? descriptor.bilingualName().ru() : descriptor.bilingualName().en();
    if (!second.isBlank()) {
      return second;
    }
    return info.getResourceString("parameterFallbackName") + (index + 1);
  }

  /**
   * Диапазон содержимого между {@code (} и {@code )} в заголовке метода —
   * без самих скобок. Чтобы поддержать и «было пусто, добавляем» (нулевой
   * range между {@code ()}), и «было что-то, заменяем» — ищем токены LPAREN
   * и RPAREN после имени метода и берём диапазон строго между ними.
   */
  private static Optional<Range> locateParamListRange(DocumentContext documentContext, MethodSymbol method) {
    var tokens = documentContext.getTokens();
    var headerTokens = methodHeaderTokens(tokens, method);
    int lparenIndex = indexOf(headerTokens, BSLLexer.LPAREN);
    if (lparenIndex < 0) {
      return Optional.empty();
    }
    int rparenIndex = matchingRparenIndex(headerTokens, lparenIndex);
    if (rparenIndex < 0) {
      return Optional.empty();
    }
    var lparen = headerTokens.get(lparenIndex);
    var rparen = headerTokens.get(rparenIndex);
    return Optional.of(new Range(Positions.createEnd(lparen), Positions.create(rparen)));
  }

  /** Токены в пределах одной строки заголовка метода — от имени до конца метода. */
  private static List<Token> methodHeaderTokens(
    List<Token> all, MethodSymbol method
  ) {
    var subNameEnd = method.getSubNameRange().getEnd();
    var methodEnd = method.getRange().getEnd();
    return all.stream()
      .filter(t -> Positions.compareStart(t, subNameEnd) >= 0 && t.getLine() - 1 <= methodEnd.getLine())
      .toList();
  }

  private static int indexOf(List<Token> tokens, int tokenType) {
    for (var i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).getType() == tokenType) {
        return i;
      }
    }
    return -1;
  }

  /** Индекс парного RPAREN с учётом баланса для default-value выражений. */
  private static int matchingRparenIndex(List<Token> tokens, int lparenIndex) {
    var depth = 0;
    for (var i = lparenIndex; i < tokens.size(); i++) {
      depth += switch (tokens.get(i).getType()) {
        case BSLLexer.LPAREN -> 1;
        case BSLLexer.RPAREN -> -1;
        default -> 0;
      };
      if (depth == 0 && tokens.get(i).getType() == BSLLexer.RPAREN) {
        return i;
      }
    }
    return -1;
  }
}
