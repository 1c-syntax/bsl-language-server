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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * Сценарии из отчёта alisher-nil к #4194 (комментарий после первичной реализации):
 * <ol>
 *   <li>чейнинг через точку по ленивому полю «забывает» содержимое структуры
 *       (проверяем и инференс, и hover-разметку);</li>
 *   <li>взаимная рекурсия через см.-поля/параметры (Контейнер↔Коробка) приводит к
 *       лавинообразному развороту при наведении (hover) — сервер «ложится».</li>
 * </ol>
 */
@CleanupContextBeforeClassAndAfterClass
class ReporterScenariosSeeRefTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Autowired
  private HoverProvider hoverProvider;

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private LanguageServerConfiguration languageServerConfiguration;

  @Test
  void mutualRecursionHoverSeeRefHonorsEnglishLocale() {
    // В EN-локали обрыв см.-цикла рендерится как `See Функция` (а не `См.`).
    languageServerConfiguration.setLanguage(Language.EN);
    try {
      var hover = hoverContent(mutualDoc(), "Контекст = ", 0);
      assertThat(hover)
        .as("в EN-локали ссылка на цикле рендерится как `See Коробка`")
        .contains("See Коробка");
    } finally {
      languageServerConfiguration.setLanguage(Language.DEFAULT_LANGUAGE);
    }
  }

  // 1. Чейнинг через точку по ленивому полю — инференс.

  @Test
  void chainedDotIntoLazyFieldKeepsStructureFields() {
    // УзелДанные = Контекст.ДанныеТокена
    var t = inferVar(chainedDoc(), "УзелДанные");
    assertThat(t.getAllFieldNames())
      .as("чейнинг `Контекст.ДанныеТокена` через точку не должен забывать поля")
      .contains("Токен", "СрокДействия");
  }

  @Test
  void doubleChainedDotIntoLazyFieldKeepsStructureFields() {
    // УзелСрок = Контекст.ДанныеТокена.СрокДействия
    var t = inferVar(chainedDoc(), "УзелСрок");
    assertThat(t.getAllFieldNames())
      .as("двойной чейнинг раскрывает вложенную структуру")
      .contains("СрокДействия", "ДатаПолучения");
  }

  // 1b. Тот же чейнинг — в hover-разметке.

  @Test
  void hoverOnChainedVariableRendersNestedFields() {
    var hover = hoverContent(chainedDoc(), "УзелДанные = ", 0);
    assertThat(hover)
      .as("hover переменной из чейнинга показывает поля структуры")
      .contains("Токен")
      .contains("СрокДействия");
  }

  // 1c. Тот же чейнинг — в автокомплите (`Контекст.ДанныеТокена.`).

  @Test
  void chainedDotCompletionListsNestedFields() {
    var items = completionAt(chainedCompletionDoc(), "Контекст.ДанныеТокена.");
    assertThat(items)
      .as("автокомплит после `Контекст.ДанныеТокена.` показывает поля вложенной структуры")
      .extracting(CompletionItem::getLabel)
      .contains("Токен", "СрокДействия");
  }

  // 2. Взаимная рекурсия Контейнер↔Коробка — hover и автокомплит не должны зависать.

  @Test
  void mutualRecursionHoverTerminates() {
    // Без защиты от цикла на чтении collectFieldBullets разворачивает ленивые
    // поля бесконечно: Контейнер.Содержимое -> Коробка.Родитель -> Контейнер ...
    assertTimeout(Duration.ofSeconds(20), () -> {
      var hover = hoverContent(mutualDoc(), "Контекст = ", 0);
      assertThat(hover)
        .as("hover взаимно-рекурсивной структуры завершается и показывает поля")
        .contains("Содержимое")
        .contains("Описание");
      assertThat(hover)
        .as("на обрыве цикла поле `Содержимое - см. Коробка` сворачивается именно в `См. Коробка` "
          + "(а не в источник уровнем глубже), #4204 п.2")
        .contains("См. Коробка")
        .doesNotContain("См. Контейнер");
    });
  }

  @Test
  void mutualRecursionCompletionTerminates() {
    // Автокомплит `Контекст.` для взаимно-рекурсивной структуры тоже не должен
    // уходить в лавину.
    assertTimeout(Duration.ofSeconds(20), () -> {
      var items = completionAt(mutualCompletionDoc(), "Контекст.");
      assertThat(items)
        .as("автокомплит взаимно-рекурсивной структуры завершается и даёт поля")
        .extracting(CompletionItem::getLabel)
        .contains("Содержимое");
    });
  }

  private List<CompletionItem> completionAt(DocumentContext dc, String marker) {
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер есть в фикстуре: %s", marker).isGreaterThanOrEqualTo(0);
    int targetOffset = markerStart + marker.length();
    int lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    int line = content.substring(0, targetOffset).split("\n", -1).length - 1;
    int charInLine = targetOffset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, charInLine));
    return completionProvider.getCompletion(dc, params).getItems();
  }

  private String hoverContent(DocumentContext dc, String marker, int offsetInMarker) {
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер есть в фикстуре: %s", marker).isGreaterThanOrEqualTo(0);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n", -1).length - 1;
    int charInLine = targetOffset - lineStart;
    var params = new HoverParams(new TextDocumentIdentifier(dc.getUri().toString()),
      new Position(line, charInLine));
    Optional<org.eclipse.lsp4j.Hover> hover = hoverProvider.getHover(dc, params);
    return hover.map(h -> h.getContents().getRight().getValue()).orElse("");
  }

  private TypeSet inferVar(DocumentContext dc, String varName) {
    VariableSymbol variable = dc.getSymbolTree().getMethods().stream()
      .map(method -> dc.getSymbolTree().getVariableSymbol(varName, method))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst()
      .orElseThrow();
    return inferencer.inferSymbol(variable);
  }

  private DocumentContext chainedDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/ChainedSeeRef.bsl");
  }

  private DocumentContext mutualDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/MutualRecursionSeeRef.bsl");
  }

  private DocumentContext chainedCompletionDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/ChainedCompletion.bsl");
  }

  private DocumentContext mutualCompletionDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/MutualRecursionCompletion.bsl");
  }
}
