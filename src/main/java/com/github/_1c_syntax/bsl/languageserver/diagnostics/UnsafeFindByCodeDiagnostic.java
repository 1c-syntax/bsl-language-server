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
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.bsl.languageserver.utils.Strings;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.support.CodeSeries;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.regex.Pattern;

/**
 * Диагностика обнаруживает использование метода {@code FindByCode()} (или {@code НайтиПоКоду()})
 * для справочников, планов видов характеристик и планов счетов, у которых:
 * <ul>
 *   <li>отключен контроль уникальности кода ({@code CheckUnique = False})</li>
 *   <li>или включены серии кодов не по всему объекту ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
 * </ul>
 * <p>
 * В таких случаях использование метода {@code FindByCode()} может привести к непредсказуемому поведению,
 * так как код может быть не уникальным в пределах всего объекта или могут существовать несколько элементов
 * с одинаковым кодом в разных сериях.
 * <p>
 * Диагностика <b>не срабатывает</b> для объектов, у которых одновременно:
 * <ul>
 *   <li>включен контроль уникальности кода ({@code CheckUnique = True})</li>
 *   <li>и серии кодов установлены для всего объекта ({@code CodeSeries = WHOLE_CATALOG})</li>
 * </ul>
 * <p>
 * Примечание: значения {@code WholeCharacteristicKind} и {@code WholeChartOfAccounts} из XML метаданных
 * преобразуются в {@code WHOLE_CATALOG} в enum {@code CodeSeries}, поэтому для всех типов объектов
 * проверка выполняется на {@code WHOLE_CATALOG}.
 * <p>
 * Также диагностика не срабатывает для объектов метаданных, не являющихся справочниками,
 * планами видов характеристик или планами счетов, а также если объект не найден в метаданных конфигурации.
 * <p>
 * Диагностика указывает на строку с комментарием, предшествующую вызову метода, что соответствует
 * принятому в проекте стилю указания диагностик на комментарии.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.DESIGN,
    DiagnosticTag.SUSPICIOUS
  }
)
public class UnsafeFindByCodeDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern METHOD_NAME_PATTERN = CaseInsensitivePattern.compile(
    "^(НайтиПоКоду|FindByCode)$"
  );

  private static final int COMMENT_LINE_OFFSET = 2;

  /**
   * Обрабатывает вызов метода в контексте сложного идентификатора.
   *
   * @param ctx контекст сложного идентификатора
   * @return результат обхода дерева разбора
   */
  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    var mdoRef = MdoRefBuilder.getMdoRef(documentContext, ctx);
    if (!mdoRef.isEmpty()) {
      checkFindByCodeMethod(ctx, mdoRef);
    }

    return super.visitComplexIdentifier(ctx);
  }

  /**
   * Проверяет, является ли вызов методом FindByCode/НайтиПоКоду и обрабатывает его.
   * <p>
   * Если вызов является методом FindByCode/НайтиПоКоду и объект метаданных имеет небезопасное использование,
   * добавляется диагностика на строку с комментарием перед вызовом метода.
   *
   * @param ctx контекст вызова метода
   * @param mdoRef ссылка на объект метаданных в формате "Catalog.ИмяКаталога",
   *               "ChartOfCharacteristicTypes.ИмяПлана" или "ChartOfAccounts.ИмяПлана"
   */
  private void checkFindByCodeMethod(BSLParser.ComplexIdentifierContext ctx, String mdoRef) {
    Methods.getMethodName(ctx).ifPresent((Token methodName) -> {
      if (isFindByCodeMethod(methodName) && checkMetadataObject(mdoRef)) {
        addDiagnosticOnCommentLine(methodName);
      }
    });
  }

  /**
   * Проверяет, является ли токен методом FindByCode/НайтиПоКоду.
   *
   * @param methodName токен с именем метода
   * @return true, если это метод FindByCode/НайтиПоКоду
   */
  private static boolean isFindByCodeMethod(Token methodName) {
    var methodNameText = Strings.trimQuotes(methodName.getText());
    return METHOD_NAME_PATTERN.matcher(methodNameText).matches();
  }

  /**
   * Проверяет объект метаданных на небезопасное использование метода FindByCode.
   * <p>
   * Метод получает объект метаданных из конфигурации по ссылке {@code mdoRef} и определяет его тип
   * с помощью {@code getMdoType()}. Проверяются только справочники, планы видов характеристик и планы счетов.
   * <p>
   * Использование считается небезопасным, если хотя бы одно из условий выполняется:
   * <ul>
   *   <li>контроль уникальности кода отключен ({@code CheckUnique = False})</li>
   *   <li>серии кодов установлены не для всего объекта ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Если объект безопасен (контроль уникальности включен И серии кодов для всего объекта),
   * или объект метаданных не найден в конфигурации, или объект не является справочником,
   * планом видов характеристик или планом счетов, метод возвращает {@code false}.
   *
   * @param mdoRef ссылка на объект метаданных в формате "Catalog.ИмяКаталога",
   *               "ChartOfCharacteristicTypes.ИмяПлана" или "ChartOfAccounts.ИмяПлана"
   * @return {@code true}, если использование небезопасно; {@code false} в противном случае
   */
  private boolean checkMetadataObject(String mdoRef) {
    var configuration = documentContext.getServerContext().getConfiguration();
    var mdoReference = MdoReference.create(mdoRef);
    var mdoOpt = configuration.findChild(mdoReference);

    if (mdoOpt.isEmpty()) {
      return false;
    }

    var mdo = mdoOpt.get();
    var mdoType = mdo.getMdoType();
    var result = false;

    if (mdoType == MDOType.CATALOG) {
      result = isUnsafeCatalogUsage((Catalog) mdo);
    } else if (mdoType == MDOType.CHART_OF_CHARACTERISTIC_TYPES) {
      result = isUnsafeChartOfCharacteristicTypesUsage((ChartOfCharacteristicTypes) mdo);
    } else if (mdoType == MDOType.CHART_OF_ACCOUNTS) {
      result = isUnsafeChartOfAccountsUsage((ChartOfAccounts) mdo);
    } else {
      result = false;
    }

    return result;
  }

  /**
   * Проверяет, является ли использование справочника небезопасным.
   * <p>
   * Использование считается небезопасным, если хотя бы одно из условий выполняется:
   * <ul>
   *   <li>контроль уникальности кода отключен ({@code CheckUnique = False})</li>
   *   <li>серии кодов установлены не для всего справочника ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Использование считается безопасным только если одновременно:
   * <ul>
   *   <li>контроль уникальности кода включен ({@code CheckUnique = True})</li>
   *   <li>серии кодов установлены для всего справочника ({@code CodeSeries = WHOLE_CATALOG})</li>
   * </ul>
   *
   * @param catalog справочник для проверки
   * @return true, если использование небезопасно; false, если использование безопасно
   */
  private static boolean isUnsafeCatalogUsage(Catalog catalog) {
    return !catalog.isCheckUnique() || catalog.getCodeSeries() != CodeSeries.WHOLE_CATALOG;
  }

  /**
   * Проверяет, является ли использование плана видов характеристик небезопасным.
   * <p>
   * Использование считается небезопасным, если хотя бы одно из условий выполняется:
   * <ul>
   *   <li>контроль уникальности кода отключен ({@code CheckUnique = False})</li>
   *   <li>серии кодов установлены не для всего плана ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Использование считается безопасным только если одновременно:
   * <ul>
   *   <li>контроль уникальности кода включен ({@code CheckUnique = True})</li>
   *   <li>серии кодов установлены для всего плана ({@code CodeSeries = WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Для планов видов характеристик значение {@code WholeCharacteristicKind} из XML метаданных
   * преобразуется в {@code WHOLE_CATALOG} в enum {@code CodeSeries}, поэтому проверка выполняется
   * на {@code WHOLE_CATALOG}, который эквивалентен {@code WholeCharacteristicKind}.
   *
   * @param chartOfCharacteristicTypes план видов характеристик для проверки
   * @return true, если использование небезопасно; false, если использование безопасно
   */
  private static boolean isUnsafeChartOfCharacteristicTypesUsage(ChartOfCharacteristicTypes chartOfCharacteristicTypes) {
    return !chartOfCharacteristicTypes.isCheckUnique()
      || chartOfCharacteristicTypes.getCodeSeries() != CodeSeries.WHOLE_CATALOG;
  }

  /**
   * Проверяет, является ли использование плана счетов небезопасным.
   * <p>
   * Использование считается небезопасным, если хотя бы одно из условий выполняется:
   * <ul>
   *   <li>контроль уникальности кода отключен ({@code CheckUnique = False})</li>
   *   <li>серии кодов установлены не для всего плана ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Использование считается безопасным только если одновременно:
   * <ul>
   *   <li>контроль уникальности кода включен ({@code CheckUnique = True})</li>
   *   <li>серии кодов установлены для всего плана ({@code CodeSeries = WHOLE_CATALOG})</li>
   * </ul>
   * <p>
   * Для планов счетов значение {@code WholeChartOfAccounts} из XML метаданных преобразуется
   * в {@code WHOLE_CATALOG} в enum {@code CodeSeries}, поэтому проверка выполняется на {@code WHOLE_CATALOG},
   * который эквивалентен {@code WholeChartOfAccounts}.
   *
   * @param chartOfAccounts план счетов для проверки
   * @return true, если использование небезопасно; false, если использование безопасно
   */
  private static boolean isUnsafeChartOfAccountsUsage(ChartOfAccounts chartOfAccounts) {
    return !chartOfAccounts.isCheckUnique() || chartOfAccounts.getCodeSeries() != CodeSeries.WHOLE_CATALOG;
  }

  /**
   * Добавляет диагностику на строку с комментарием перед вызовом метода.
   * <p>
   * Диагностика указывает на строку с комментарием, предшествующую вызову метода,
   * что соответствует принятому в проекте стилю указания диагностик на комментарии.
   * <p>
   * ANTLR использует индексацию строк с 1, LSP - с 0. Если метод находится на строке N (ANTLR),
   * то комментарий находится на строке N-1 (ANTLR), что в LSP будет (N-1)-1 = N-2.
   * Для вычисления строки комментария в LSP из строки метода в ANTLR вычитается {@link #COMMENT_LINE_OFFSET}.
   * <p>
   * Если вычисленная строка комментария меньше 0 (комментарий отсутствует или метод на первой строке),
   * диагностика указывает на строку с самим методом.
   * <p>
   * Для покрытия всей строки комментария используется максимальное значение символа ({@code Integer.MAX_VALUE}).
   *
   * @param methodName токен с именем метода, для которого добавляется диагностика
   */
  private void addDiagnosticOnCommentLine(Token methodName) {
    int methodLineANTLR = methodName.getLine();
    int commentLineLSP = methodLineANTLR - COMMENT_LINE_OFFSET;
    if (commentLineLSP >= 0) {
      diagnosticStorage.addDiagnostic(commentLineLSP, 0, commentLineLSP, Integer.MAX_VALUE);
    } else {
      diagnosticStorage.addDiagnostic(methodName);
    }
  }
}
