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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformationCapabilities;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SignatureHelpContext;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureHelpTriggerKind;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SignatureInformationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SignatureHelpProviderTest {

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @AfterEach
  void resetClientCapabilities() {
    clientCapabilitiesHolder.setCapabilities(null);
    signatureHelpProvider.handleInitializeEvent();
  }

  private void initSignatureHelpCapabilities(boolean labelOffsetSupport, boolean markdownDocumentation) {
    var parameterInformation = new ParameterInformationCapabilities();
    parameterInformation.setLabelOffsetSupport(labelOffsetSupport);
    var signatureInformation = new SignatureInformationCapabilities();
    signatureInformation.setParameterInformation(parameterInformation);
    if (markdownDocumentation) {
      signatureInformation.setDocumentationFormat(List.of(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
    } else {
      signatureInformation.setDocumentationFormat(List.of(MarkupKind.PLAINTEXT));
    }
    var signatureHelp = new SignatureHelpCapabilities();
    signatureHelp.setSignatureInformation(signatureInformation);
    var textDocumentCaps = new TextDocumentClientCapabilities();
    textDocumentCaps.setSignatureHelp(signatureHelp);
    var caps = new ClientCapabilities();
    caps.setTextDocument(textDocumentCaps);
    clientCapabilitiesHolder.setCapabilities(caps);
    signatureHelpProvider.handleInitializeEvent();
  }

  @Test
  void testNoSignatureWithoutCall() {
    var content = "А = 1;\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 4));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).isEmpty();
  }

  @Test
  void testGlobalMethodCallOfLocalMethod() {
    var content =
      "Процедура МояПроцедура(А, Б = 5, В) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "МояПроцедура(1, 2, 3);\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после второй запятой — активный параметр должен быть 2 (третий)
    params.setPosition(new Position(3, 18));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).hasSize(1);
    var sig = help.getSignatures().get(0);
    assertThat(sig.getParameters()).hasSize(3);
    assertThat(sig.getLabel()).contains("МояПроцедура(");
    // Необязательный параметр «Б = 5» (без аннотации типа) помечается «?» после имени: Б?
    assertThat(sig.getLabel()).contains("Б?");
    assertThat(help.getActiveParameter()).isEqualTo(2);
  }

  @Test
  void testAccessMethodCallSignatureViaVariable() {
    // Массив = Новый Массив; Массив.Добавить(1);  — на позиции после '(' должна быть сигнатура Добавить
    var content =
      "Массив = Новый Массив;\n"
        + "Массив.Добавить(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор сразу после '(' в "Добавить("
    var line = 1;
    var col = content.split("\n")[1].indexOf("Добавить(") + "Добавить(".length();
    params.setPosition(new Position(line, col));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getSignatures().get(0).getLabel()).startsWith("Добавить(");
  }

  @Test
  void variadicConstructorSignatureExpandsNumberedParameters() {
    // `Новый Массив(2, 3, 4)` — вариадик-конструктор: один параметр-база
    // КоличествоЭлементов разворачивается в нумерованные по числу аргументов.
    var content = "М = Новый Массив(2, 3, 4);\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.indexOf("Массив(") + "Массив(".length();
    params.setPosition(new Position(0, col));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getSignatures().get(0).getLabel())
      .contains("КоличествоЭлементов1", "КоличествоЭлементов2", "КоличествоЭлементов3")
      .doesNotContain("КоличествоЭлементов,");
  }

  @Test
  void signatureHelpRendersInConfiguredLanguageEn() {
    // Signature help — элемент интерфейса: при language=EN имя метода и
    // параметр показываются по-английски (Add(Value)), хотя в исходнике
    // написано русское Добавить.
    var content = """
      Массив = Новый Массив;
      Массив.Добавить(1);
      """;
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Добавить(") + "Добавить(".length();
    params.setPosition(new Position(1, col));

    languageServerConfiguration.setLanguage(Language.EN);
    try {
      var help = signatureHelpProvider.getSignatureHelp(documentContext, params);
      assertThat(help.getSignatures()).isNotEmpty();
      var label = help.getSignatures().get(0).getLabel();
      assertThat(label)
        .as("имя метода и параметр signature help — на языке конфига (EN)")
        .startsWith("Add(")
        .contains("Value")
        .doesNotContain("Добавить")
        .doesNotContain("Значение");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  @Test
  void testGlobalBuiltinFunctionSignature() {
    var content = "Сообщить(\"привет\");\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция внутри скобок Сообщить — после первой открывающей скобки
    params.setPosition(new Position(0, 9));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).hasSize(1);
    var sig = help.getSignatures().get(0);
    assertThat(sig.getLabel()).startsWith("Сообщить(");
    assertThat(sig.getParameters()).hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  void testActiveSignaturePicksOverloadByArgumentIndex() {
    // ЧтениеДанных.Разделить имеет 2 варианта: [separator] и [separator, encoding].
    // Курсор на 2-м параметре (после первой запятой) — должна стать активной 2-я сигнатура.
    var content =
      "ЧД = Новый ЧтениеДанных(\"path\");\n"
        + "ЧД.Разделить(\"|\", \"UTF-8\");\n";
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // позиция сразу после запятой во втором аргументе Разделить(
    var line = 1;
    var col = content.split("\n")[1].indexOf("\"UTF-8\"");
    params.setPosition(new Position(line, col));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).hasSize(2);
    assertThat(help.getActiveParameter()).isEqualTo(1);
    assertThat(help.getActiveSignature()).isEqualTo(1);
    assertThat(help.getSignatures().get(1).getParameters()).hasSize(2);
  }

  @Test
  void signatureHelpFindsInnermostNestedCall() {
    // given — вложенные вызовы, курсор внутри внутреннего.
    var content =
      "Процедура М(А) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "Процедура Внутр(Б, В) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "М(Внутр(1, 2));\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор сразу после первой запятой во внутреннем вызове.
    var line = 6;
    var col = content.split("\n")[line].indexOf(", ") + 2;
    params.setPosition(new Position(line, col));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — supplier выбирает внутренний (innermost) doCall = Внутр(...).
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getSignatures().get(0).getLabel()).startsWith("Внутр(");
    assertThat(help.getActiveParameter()).isEqualTo(1);
  }

  @Test
  void signatureHelpWithBlankInRangeReturnsCorrectActiveParameter() {
    // given
    var content =
      "Процедура М(А, Б, В) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "М(, , );\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор после первой запятой → активный параметр = 1.
    params.setPosition(new Position(3, content.split("\n")[3].indexOf(',') + 2));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getActiveParameter()).isEqualTo(1);
  }

  @Test
  void noSignatureForUnknownTypeConstructor() {
    // given — несуществующий тип
    var content = "А = Новый НесуществующийТип(1, 2);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, "А = Новый НесуществующийТип(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).isEmpty();
  }

  @Test
  void noSignatureWhenCursorOutsideParens() {
    // given — курсор ДО открывающей скобки
    var content = "Сообщить(\"привет\");\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, 3));  // в середине слова `Сообщить`

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).isEmpty();
  }

  @Test
  void emptyCallReturnsSignatureWithZeroActiveParameter() {
    // given
    var content =
      "Процедура М(А) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "М();\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "М(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getActiveParameter()).isZero();
  }

  @Test
  void accessCallOnUntypedVariableReturnsNoSignature() {
    // given — переменная без типа, точный метод не определить
    var content =
      "Перем X;\n"
        + "X.СомнительныйМетод(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, "X.СомнительныйМетод(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).isEmpty();
  }

  @Test
  void signatureHelpFallbackToActiveParameterWhenTypesNoMatch() {
    // given — локальный метод с двумя параметрами + аргументы без типов.
    var content =
      "Процедура М(А, Б, В) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "М(, , );\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор сразу после второй запятой — активный параметр = 2.
    var thirdParamCol = content.split("\n")[3].lastIndexOf(", ") + 2;
    params.setPosition(new Position(3, thirdParamCol));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — fallback к pickIndexByActiveParameter.
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getActiveParameter()).isEqualTo(2);
  }

  @Test
  void signatureHelpForCallStatementOnSameLine() {
    // given — standalone callStatement (Объект.Метод();) на той же строке.
    var content = "Сообщить(\"hello\", \"info\");\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.indexOf(", ") + 2));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — supplier находит сигнатуру глобальной функции.
    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getActiveParameter()).isPositive();
  }

  @Test
  void signatureHelpForGlobalMethodCallOfUnknownFunction() {
    // given — вызов несуществующей глобальной функции.
    var content = "НеТакаяФункция12345(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(0, content.length() - 3));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).isEmpty();
  }

  @Test
  void signatureHelpForMethodWithOptionalParameter() {
    // given — М(А, Б = "default") — supplier строит сигнатуру с optional Б.
    var content =
      "Процедура М(А, Б = \"default\") Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "М(1, \"X\");\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "М(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — label содержит Б (optional не убран из label).
    assertThat(help.getSignatures()).isNotEmpty();
    assertThat(help.getSignatures().get(0).getLabel()).contains("Б");
  }

  @Test
  void signatureHelpForArrayInsertMethod() {
    // given — Массив.Вставить(...) — access call на платформенном типе.
    var content = "А = Новый Массив;\nА.Вставить(0, \"X\");\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, content.split("\n")[1].indexOf(", ") + 2));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — Массив.Вставить имеет сигнатуру.
    assertThat(help.getSignatures()).isNotEmpty();
  }

  @Test
  void signatureHelpForChainedDereferenceCall() {
    // given — Объект.Свойство.Метод() — chain через 2 точки до methodCall.
    var content = "О = Новый Структура;\nО.Вставить(\"К\", Новый Массив);\nО.К.Добавить(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(2, content.split("\n")[2].indexOf("(") + 1));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — supplier не падает; сигнатура может быть найдена или нет в зависимости
    // от инференса типа через chain.
    assertThat(help).isNotNull();
  }

  @Test
  void signatureHelpForNestedNewExpression() {
    // given — Новый Массив(Новый ФиксированныйМассив(...))
    var content = "А = Новый Массив(Новый ФиксированныйМассив(1, 2, 3));\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // курсор внутри внутреннего конструктора, после первой запятой.
    params.setPosition(new Position(0, content.indexOf(", ") + 2));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — supplier выбирает внутренний конструктор и активный параметр.
    assertThat(help).isNotNull();
  }

  @Test
  void localMethodCallFindsSignatureCaseInsensitive() {
    // given — вызов локального метода в другом регистре
    var content =
      "Процедура МояПроцедура(А) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "мОяПрОцЕдУрА(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "мОяПрОцЕдУрА(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then
    assertThat(help.getSignatures()).hasSize(1);
    assertThat(help.getSignatures().get(0).getLabel()).startsWith("МояПроцедура(");
  }

  @Test
  void testLocalMethodSignatureRendersDeclaredParameterAndReturnTypes() {
    // Объявленные типы параметров и возвращаемого значения из JsDoc должны
    // попадать в label сигнатуры локального метода.
    var content =
      "// Описание.\n"
        + "//\n"
        + "// Параметры:\n"
        + "//  Док - ДокументСсылка.Заказ - первичный документ\n"
        + "//  Имя - Строка\n"
        + "//\n"
        + "// Возвращаемое значение:\n"
        + "//  Массив\n"
        + "//\n"
        + "Функция МояФункция(Док, Имя) Экспорт\n"
        + "  Возврат Новый Массив;\n"
        + "КонецФункции\n"
        + "\n"
        + "Рез = МояФункция(А, Б);\n";
    var documentContext = TestUtils.getDocumentContext(content);

    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var lines = content.split("\n", -1);
    int callLine = -1;
    int col = -1;
    for (int i = 0; i < lines.length; i++) {
      int idx = lines[i].indexOf("Рез = МояФункция(");
      if (idx >= 0) {
        callLine = i;
        col = idx + "Рез = МояФункция(".length();
        break;
      }
    }
    assertThat(callLine).isNotNegative();
    params.setPosition(new Position(callLine, col));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help).isNotNull();
    assertThat(help.getSignatures()).hasSize(1);
    var sig = help.getSignatures().get(0);
    assertThat(sig.getLabel())
      .startsWith("МояФункция(")
      .contains("Док: ДокументСсылка.Заказ")
      .contains("Имя: Строка")
      .endsWith("): Массив");
    assertThat(sig.getParameters()).hasSize(2);
    // описание первого параметра доходит как трейлинг-описание типа из JsDoc
    var paramDoc = sig.getParameters().get(0).getDocumentation();
    assertThat(paramDoc).isNotNull();
    assertThat(paramDoc.getLeft()).contains("первичный документ");
  }

  @Test
  void retriggerKeepsUserSelectedSignatureWhenStillValid() {
    // given — у метода Разделить две перегрузки: одна с разделителем и одна
    // с разделителем и кодировкой; курсор на первом параметре (после открывающей
    // скобки), где сервер по числу аргументов выбрал бы нулевую перегрузку,
    // но пользователь стрелками выбрал первую.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var userPicked = userPickedContext(previous, 1);
    params.setContext(userPicked);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — выбор пользователя (1) сохранён, активный параметр пересчитан (0).
    assertThat(help.getSignatures()).hasSize(2);
    assertThat(help.getActiveParameter()).isZero();
    assertThat(help.getActiveSignature()).isEqualTo(1);
  }

  @Test
  void retriggerFallsBackToServerWhenUserSignatureInvalid() {
    // given — курсор на втором параметре (после первой запятой); пользователь ранее
    // выбрал 0-ю перегрузку с одним параметром, для которой второй параметр невалиден.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|", "UTF-8");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("\"UTF-8\"");
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var userPicked = userPickedContext(previous, 0);
    params.setContext(userPicked);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — 0-я перегрузка невалидна (один параметр), сервер пересчитывает на 1-ю.
    assertThat(help.getSignatures()).hasSize(2);
    assertThat(help.getActiveParameter()).isEqualTo(1);
    assertThat(help.getActiveSignature()).isEqualTo(1);
  }

  @Test
  void nonRetriggerIgnoresProvidedContextSignature() {
    // given — тот же контекст с выбором 1, но без флага retrigger.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var context = userPickedContext(previous, 1);
    context.setIsRetrigger(false);
    context.setTriggerKind(SignatureHelpTriggerKind.TriggerCharacter);
    params.setContext(context);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — контекст игнорируется, активная сигнатура по серверной логике (0).
    assertThat(help.getActiveSignature()).isZero();
  }

  @Test
  void retriggerWithoutActiveSignatureHelpFallsBackToServer() {
    // given — retrigger без присланного activeSignatureHelp в контексте.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));
    var context = new SignatureHelpContext(SignatureHelpTriggerKind.TriggerCharacter, true);
    params.setContext(context);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — присланной сигнатуры нет, используется серверная логика (0).
    assertThat(help.getActiveSignature()).isZero();
  }

  @Test
  void retriggerWithNullActiveSignatureFallsBackToServer() {
    // given — retrigger с activeSignatureHelp, но без выбранной активной сигнатуры.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var activeHelp = new SignatureHelp();
    activeHelp.setSignatures(previous.getSignatures());
    activeHelp.setActiveSignature(null);
    var context = new SignatureHelpContext(SignatureHelpTriggerKind.TriggerCharacter, true);
    context.setActiveSignatureHelp(activeHelp);
    params.setContext(context);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — активная сигнатура не задана, используется серверная логика (0).
    assertThat(help.getActiveSignature()).isZero();
  }

  @Test
  void retriggerWithActiveSignatureOutOfRangeFallsBackToServer() {
    // given — присланный индекс активной сигнатуры за границами текущего списка.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var context = userPickedContext(previous, 99);
    params.setContext(context);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — индекс невалиден, используется серверная логика (0).
    assertThat(help.getActiveSignature()).isZero();
  }

  @Test
  void retriggerWithDifferentLabelFallsBackToServer() {
    // given — присланная по тому же индексу сигнатура имеет другую метку,
    // значит пользовательский выбор более не актуален.
    var content = """
      ЧД = Новый ЧтениеДанных("path");
      ЧД.Разделить("|");
      """;
    var documentContext = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    var col = content.split("\n")[1].indexOf("Разделить(") + "Разделить(".length();
    params.setPosition(new Position(1, col));

    var previous = signatureHelpProvider.getSignatureHelp(documentContext, params);
    var activeHelp = new SignatureHelp();
    var staleSignatures = previous.getSignatures().stream()
      .map(SignatureHelpProviderTest::copySignatureInformation)
      .toList();
    staleSignatures.get(1).setLabel("OtherMethod(args)");
    activeHelp.setSignatures(staleSignatures);
    activeHelp.setActiveSignature(1);
    activeHelp.setActiveParameter(previous.getActiveParameter());
    var context = new SignatureHelpContext(SignatureHelpTriggerKind.TriggerCharacter, true);
    context.setActiveSignatureHelp(activeHelp);
    params.setContext(context);

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — метка не совпадает, используется серверная логика (0).
    assertThat(help.getActiveSignature()).isZero();
  }

  @Test
  void parameterLabelUsesOffsetsWhenClientSupportsLabelOffset() {
    // given — клиент заявил labelOffsetSupport.
    initSignatureHelpCapabilities(true, false);
    var content =
      "Процедура МояПроцедура(А, Б) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "МояПроцедура(1, 2);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "МояПроцедура(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — ParameterInformation.label задан офсетами [start, end), которые
    // указывают на подстроку параметра внутри label сигнатуры.
    assertThat(help.getSignatures()).hasSize(1);
    var sig = help.getSignatures().get(0);
    var paramLabel = sig.getParameters().get(0).getLabel();
    assertThat(paramLabel.isRight()).isTrue();
    var offsets = paramLabel.getRight();
    assertThat(sig.getLabel().substring(offsets.getFirst(), offsets.getSecond())).isEqualTo("А");
  }

  @Test
  void parameterLabelUsesStringWhenClientDoesNotSupportLabelOffset() {
    // given — клиент НЕ заявил labelOffsetSupport.
    initSignatureHelpCapabilities(false, false);
    var content =
      "Процедура МояПроцедура(А, Б) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "МояПроцедура(1, 2);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(3, "МояПроцедура(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — ParameterInformation.label задан строкой-подстрокой параметра.
    assertThat(help.getSignatures()).hasSize(1);
    var sig = help.getSignatures().get(0);
    var paramLabel = sig.getParameters().get(0).getLabel();
    assertThat(paramLabel.isLeft()).isTrue();
    assertThat(paramLabel.getLeft()).isEqualTo("А");
  }

  @Test
  void signatureDocumentationUsesMarkdownWhenClientSupportsIt() {
    // given — клиент поддерживает markdown в documentation сигнатуры.
    initSignatureHelpCapabilities(true, true);
    var content =
      "// Описание метода.\n"
        + "Процедура МояПроцедура(А) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "МояПроцедура(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, "МояПроцедура(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — documentation сигнатуры — MarkupContent(MARKDOWN).
    assertThat(help.getSignatures()).hasSize(1);
    var documentation = help.getSignatures().get(0).getDocumentation();
    assertThat(documentation).isNotNull();
    assertThat(documentation.isRight()).isTrue();
    assertThat(documentation.getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(documentation.getRight().getValue()).contains("Описание метода");
  }

  @Test
  void signatureDocumentationUsesPlaintextWhenClientDoesNotSupportMarkdown() {
    // given — клиент не поддерживает markdown в documentation сигнатуры.
    initSignatureHelpCapabilities(true, false);
    var content =
      "// Описание метода.\n"
        + "Процедура МояПроцедура(А) Экспорт\n"
        + "КонецПроцедуры\n"
        + "\n"
        + "МояПроцедура(1);\n";
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(4, "МояПроцедура(".length()));

    // when
    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    // then — documentation сигнатуры — обычная строка (plaintext).
    assertThat(help.getSignatures()).hasSize(1);
    var documentation = help.getSignatures().get(0).getDocumentation();
    assertThat(documentation).isNotNull();
    assertThat(documentation.isLeft()).isTrue();
    assertThat(documentation.getLeft()).contains("Описание метода");
  }

  private static SignatureHelpContext userPickedContext(SignatureHelp previous, int activeSignature) {
    var activeHelp = new SignatureHelp();
    activeHelp.setSignatures(previous.getSignatures().stream()
      .map(SignatureHelpProviderTest::copySignatureInformation)
      .toList());
    activeHelp.setActiveSignature(activeSignature);
    activeHelp.setActiveParameter(previous.getActiveParameter());
    var context = new SignatureHelpContext(SignatureHelpTriggerKind.TriggerCharacter, true);
    context.setActiveSignatureHelp(activeHelp);
    return context;
  }

  private static SignatureInformation copySignatureInformation(SignatureInformation source) {
    var copy = new SignatureInformation();
    copy.setLabel(source.getLabel());
    copy.setParameters(source.getParameters());
    copy.setDocumentation(source.getDocumentation());
    return copy;
  }
}
