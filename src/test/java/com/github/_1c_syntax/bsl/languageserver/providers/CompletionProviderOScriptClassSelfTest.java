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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-доступ (без квалификации, внутри собственного тела класса) к встроенным
 * членам OScript-класса ({@code ВызватьСобытие}/{@code ЭтотОбъект}) — через
 * {@link CompletionProvider}, {@link HoverProvider} и {@link SignatureHelpProvider}.
 * Фикстура — implicit library-класс (не объявлен в {@code lib.config}, обнаружен
 * только обходом convention-каталога {@code internal/Классы}), тот же кейс, что
 * и у реального проекта с приватными классами вне манифеста библиотеки.
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionProviderOScriptClassSelfTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/internal-classes-test";
  private static final String CLASS_FILE = FIXTURE_DIR
    + "/oscript_modules/internal-classes-lib/src/internal/Классы/ВнутренняяСущность.os";

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private HoverProvider hoverProvider;

  @Autowired
  private SignatureHelpProvider signatureHelpProvider;

  @Autowired
  private OScriptLibraryIndex index;

  private DocumentContext openClassDocument() {
    var fixtureRoot = Path.of(FIXTURE_DIR).toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
    return TestUtils.getDocumentContextFromFile(CLASS_FILE, context);
  }

  @Test
  void noDotCompletionInsideImplicitLibraryClassOffersBuiltinRaiseEvent() {
    var documentContext = openClassDocument();

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    // Функция Эхо(Текст) Экспорт / ВызватьСобытие("Эхо"); / Возврат |Текст; —
    // курсор сразу после "Возврат " (пустой префикс), внутри собственного
    // метода класса.
    params.setPosition(new Position(12, 9));

    var items = completionProvider.getCompletion(documentContext, params).getItems();

    // Встроенные члены класса доступны без квалификации внутри его же тела,
    // как и любой другой self-член.
    assertThat(items)
      .extracting(CompletionItem::getLabel)
      .contains("ВызватьСобытие", "ЭтотОбъект");
  }

  @Test
  void hoverOnBareSelfCallResolvesToBuiltinRaiseEvent() {
    var documentContext = openClassDocument();

    // Строка 12 (1-based) / line 11 (0-based): "\tВызватьСобытие(\"Эхо\");" —
    // имя метода занимает columns [1,15).
    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(11, 5));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).as("hover на голом self-вызове ВызватьСобытие должен резолвиться").isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("ВызватьСобытие")
      .contains("ИмяСобытия");
  }

  @Test
  void signatureHelpOnBareSelfCallShowsRaiseEventParameters() {
    var documentContext = openClassDocument();

    // Курсор внутри скобок вызова на той же строке.
    var params = new SignatureHelpParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(11, 17));

    var help = signatureHelpProvider.getSignatureHelp(documentContext, params);

    assertThat(help.getSignatures())
      .as("параметр-хинты для голого self-вызова ВызватьСобытие должны резолвиться")
      .isNotEmpty();
    assertThat(help.getSignatures().get(0).getLabel()).contains("ВызватьСобытие");
  }

  @Test
  void hoverOnExternalDotAccessCallStillResolves() {
    // Контрольная проверка независимого пути резолва: тот же встроенный метод,
    // но вызванный ИЗВНЕ через точку у сконструированного экземпляра
    // (dereference по инферированному типу ресивера, не self-механизм).
    openClassDocument();

    var content = """
      #Использовать internal-classes-lib
      Инст = Новый ВнутренняяСущность();
      Инст.ВызватьСобытие("Эхо");
      """;
    var caller = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
    var col = "Инст.".length();
    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(caller.getUri().toString()));
    params.setPosition(new Position(2, col + 2));

    var hover = hoverProvider.getHover(caller, params);

    assertThat(hover).isPresent();
    assertThat(hover.get().getContents().getRight().getValue()).contains("ВызватьСобытие");
  }
}
