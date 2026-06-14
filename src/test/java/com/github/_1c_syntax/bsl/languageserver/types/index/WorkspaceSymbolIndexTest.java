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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceSymbolIndexTest extends AbstractServerContextAwareTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op
  };

  @Autowired
  private WorkspaceSymbolIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void indexesSupportedSymbolsOnContentChangedEvent() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем МодульнаяПеременная Экспорт;
      Процедура Тест()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    // then — метод и модульная переменная попали в индекс
    var methods = index.search("Тест", NO_CANCEL);
    assertThat(methods)
      .anyMatch(entry -> entry.name().equals("Тест"));

    var variables = index.search("МодульнаяПеременная", NO_CANCEL);
    assertThat(variables)
      .anyMatch(entry -> entry.name().equals("МодульнаяПеременная"));
  }

  @Test
  void findsSymbolByWordStartAndMultiWord() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ОбработкаЗаполнения()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    // then — начало CamelCase-слова «Заполнения» (через trie word-start, не сканом)
    assertThat(index.search("Заполнения", NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("ОбработкаЗаполнения"));

    // then — многословный camel-hump: «Обр» — начало «Обработка», «Зап» — начало «Заполнения»
    assertThat(index.search("ОбрЗап", NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("ОбработкаЗаполнения"));
  }

  @Test
  void doesNotFindArbitrarySubsequenceMatch() {
    // given — символ, где запрос совпадает лишь как разбросанная подпоследовательность
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокументОтбора()
      КонецПроцедуры
      """);

    // when — «првдкотб» (один фрагмент): буквы встречаются по порядку, но не подряд и не с начала слов
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("првдкотб", NO_CANCEL);

    // then — подпоследовательность вразброс намеренно НЕ поддерживается: ничего не найдено
    assertThat(result)
      .noneMatch(entry -> entry.name().equals("ПровестиДокументОтбора"));
  }

  @Test
  void doesNotFindMiddleOfWordSubstring() {
    // given — символ со словом «Документ»; «кумен» — середина слова, не начало
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      """);

    // when — «кумен» не является ни префиксом полного имени, ни началом какого-либо слова
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("кумен", NO_CANCEL);

    // then — произвольная подстрока ВНУТРИ слова намеренно НЕ поддерживается: ничего не найдено
    assertThat(result)
      .noneMatch(entry -> entry.name().equals("ПровестиДокумент"));
  }

  @Test
  void ranksExactAndPrefixMatchesAboveWordStart() {
    // given — три символа: точное имя, префикс полного имени, начало слова из середины имени
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      КонецПроцедуры
      Процедура ТестДанных()
      КонецПроцедуры
      Процедура ПерезаписьТеста()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Тест", NO_CANCEL);

    // then — точное совпадение выше префикса полного имени, префикс выше начала слова из середины
    var names = result.stream().map(Entry::name).toList();
    assertThat(names.indexOf("Тест")).isLessThan(names.indexOf("ТестДанных"));
    assertThat(names.indexOf("ТестДанных")).isLessThan(names.indexOf("ПерезаписьТеста"));
  }

  @Test
  void returnsAllMatchesRankedByScore() {
    // given — точное совпадение, совпадение по префиксу и совпадение по началу слова из середины
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Дата()
      КонецПроцедуры
      Процедура ДатаНачала()
      КонецПроцедуры
      Процедура НачалоДатаОтчёта()
      КонецПроцедуры
      """);

    // when — все совпадения возвращаются без усечения, по убыванию релевантности
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Дата", NO_CANCEL);

    // then — все три совпадения присутствуют, ранжированы: точное, затем префикс полного имени,
    // затем начало слова из середины (порядок проверяется как подпоследовательность выдачи,
    // т.к. индекс — общий бин на класс и может содержать символы соседних тестов)
    var names = result.stream().map(Entry::name).toList();
    assertThat(names)
      .contains("Дата", "ДатаНачала", "НачалоДатаОтчёта")
      .containsSubsequence("Дата", "ДатаНачала", "НачалоДатаОтчёта");
  }

  @Test
  void prefixSearchViaTrieFindsAndRanksPrefixMatches() {
    // given — несколько имён с общим префиксом и одно без него
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      Процедура ПроверитьЗаполнение()
      КонецПроцедуры
      Процедура Очистить()
      КонецПроцедуры
      """);

    // when — префиксный путь через trie.prefixMap
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Про", NO_CANCEL);

    // then — найдены оба префиксных совпадения, имя без префикса отсутствует;
    // при равном скоре раньше идёт более короткое имя
    var names = result.stream().map(Entry::name).toList();
    assertThat(names)
      .contains("ПровестиДокумент", "ПроверитьЗаполнение")
      .doesNotContain("Очистить");
    assertThat(names.indexOf("ПровестиДокумент")).isLessThan(names.indexOf("ПроверитьЗаполнение"));
  }

  @Test
  void wordStartSearchFindsSymbolByMiddleWord() {
    // given — символ со словом «Документ» в середине имени
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      """);

    // when — запрос «Документ» — начало второго CamelCase-слова имени «ПровестиДокумент»,
    // которое не является префиксом полного имени, но индексировано как word-start ключ
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Документ", NO_CANCEL);

    // then — символ найден по началу слова из середины имени (через trie word-start, не сканом)
    assertThat(result)
      .anyMatch(entry -> entry.name().equals("ПровестиДокумент"));
  }

  @Test
  void fullNamePrefixRanksAboveWordStartForSameQuery() {
    // given — для одного запроса: префикс полного имени и начало слова из середины имени
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ДокументПолучить()
      КонецПроцедуры
      Процедура ПровестиДокумент()
      КонецПроцедуры
      """);

    // when — запрос «Док»: «ДокументПолучить» совпадает как префикс полного имени,
    // «ПровестиДокумент» — как начало слова «Документ» из середины имени
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Док", NO_CANCEL);
    var names = result.stream().map(Entry::name).toList();

    // then — оба найдены, но префикс полного имени идёт раньше начала слова из середины
    assertThat(names)
      .contains("ДокументПолучить", "ПровестиДокумент")
      .containsSubsequence("ДокументПолучить", "ПровестиДокумент");
  }

  @Test
  void multiWordCamelHumpFindsSymbolViaTrieIntersection() {
    // given — имя из двух CamelCase-слов
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      """);

    // when — «ПрДок»: фрагменты «пр»,«док» пересекаются по словам через trie, без полного скана
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("ПрДок", NO_CANCEL);

    // then — символ найден как многословное совпадение
    assertThat(result)
      .anyMatch(entry -> entry.name().equals("ПровестиДокумент"));
  }

  @Test
  void inOrderMultiWordMatchRanksAboveUnordered() {
    // given — два имени из тех же слов в разном порядке
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      Процедура ДокументПровести()
      КонецПроцедуры
      """);

    // when — «ПрДок» (пр, док): для «ПровестиДокумент» — по порядку, для «ДокументПровести» — нет
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("ПрДок", NO_CANCEL);
    var names = result.stream().map(Entry::name).toList();

    // then — оба найдены, но совпадение по порядку идёт раньше совпадения не по порядку
    assertThat(names)
      .contains("ПровестиДокумент", "ДокументПровести")
      .containsSubsequence("ПровестиДокумент", "ДокументПровести");
  }

  @Test
  void outOfOrderMultiWordMatchIsStillReturned() {
    // given — имя, где фрагменты совпадают со словами не по порядку запроса
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ДокументПровести()
      КонецПроцедуры
      """);

    // when — «ПрДок»: «пр» — начало «Провести» (2-е слово), «док» — начало «Документ» (1-е слово)
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("ПрДок", NO_CANCEL);

    // then — несмотря на нарушенный порядок, запись возвращается (не отфильтровывается)
    assertThat(result)
      .anyMatch(entry -> entry.name().equals("ДокументПровести"));
  }

  @Test
  void wordStartMatchDoesNotDuplicateEntry() {
    // given — имя, которое лежит в дереве под несколькими ключами (полное имя и начало слова)
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПровестиДокумент()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Документ", NO_CANCEL);

    // then — запись не дублируется, хотя лежит под несколькими ключами
    var matches = result.stream().filter(entry -> entry.name().equals("ПровестиДокумент")).count();
    assertThat(matches).isEqualTo(1L);
  }

  @Test
  void clearRemovesSymbolFromAllWordStartKeys() {
    // given — символ проиндексирован и по полному имени, и по началу слова
    var documentContext = TestUtils.getDocumentContext("""
      Процедура УникальноеПроведениеДокумента()
      КонецПроцедуры
      """);
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(index.search("Документа", NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("УникальноеПроведениеДокумента"));

    // when — событие жизненного цикла чистит индекс по URI
    eventPublisher.publishEvent(
      new ServerContextDocumentRemovedEvent(documentContext.getServerContext(), documentContext.getUri()));

    // then — символ исчез из ВСЕХ ключей: и по полному имени, и по началу слова из середины
    assertThat(index.search("УникальноеПроведениеДокумента", NO_CANCEL))
      .noneMatch(entry -> entry.name().equals("УникальноеПроведениеДокумента"));
    assertThat(index.search("Документа", NO_CANCEL))
      .noneMatch(entry -> entry.name().equals("УникальноеПроведениеДокумента"));
    assertThat(index.search("Проведение", NO_CANCEL))
      .noneMatch(entry -> entry.name().equals("УникальноеПроведениеДокумента"));
  }

  @Test
  void clearOnLifecycleEventRemovesDocumentSymbols() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура УникальныйСимвол()
      КонецПроцедуры
      """);
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(index.search("УникальныйСимвол", NO_CANCEL)).isNotEmpty();

    // when — событие жизненного цикла чистит индекс по URI
    eventPublisher.publishEvent(
      new ServerContextDocumentRemovedEvent(documentContext.getServerContext(), documentContext.getUri()));

    // then
    assertThat(index.search("УникальныйСимвол", NO_CANCEL)).isEmpty();
  }

  @Test
  void emptyQueryReturnsAllIndexedSymbols() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Первая()
      КонецПроцедуры
      Процедура Вторая()
      КонецПроцедуры
      Процедура Третья()
      КонецПроцедуры
      """);

    // when — пустой запрос возвращает все проиндексированные символы, без усечения
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("", NO_CANCEL);

    // then — символы этого документа присутствуют в полной выдаче пустого запроса
    assertThat(result)
      .extracting(Entry::name)
      .contains("Первая", "Вторая", "Третья");
  }

  @Test
  void cancelledCheckerInterruptsSearchEventually() {
    // given — много символов, чтобы пройти порог проверки отмены
    var builder = new StringBuilder();
    for (var i = 0; i < 2000; i++) {
      builder.append("Процедура Символ").append(i).append("()\nКонецПроцедуры\n");
    }
    var documentContext = TestUtils.getDocumentContext(builder.toString());
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    CancelChecker cancelChecker = () -> {
      throw new CancellationException();
    };

    // when / then
    assertThatThrownBy(() -> index.search("Символ", cancelChecker))
      .isInstanceOf(CancellationException.class);
  }
}
