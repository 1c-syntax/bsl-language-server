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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentLinkProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода»: пункты про возможности редактора —
 * контекстная подсказка, подсказка при наведении, переход к определению, генератор
 * документирующих комментариев.
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Тест выражает требование рекомендации, а не текущее поведение: пока пункт не закрыт,
 * тест красный, и закрытие пункта его чинит.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecEditorFeaturesTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;
  @Autowired
  private HoverProvider hoverProvider;
  @Autowired
  private DefinitionProvider definitionProvider;
  @Autowired
  private DocumentLinkProvider documentLinkProvider;
  @Autowired
  private CodeActionProvider codeActionProvider;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  @DisplayName("2.9 Контекстная подсказка перечисляет свойства и методы известного типа")
  void completionListsMembersOfKnownType() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	Ссылка = Справочники.Справочник1.ПустаяСсылка();
      	Ссылка.
      КонецПроцедуры
      """, context);

    // when
    var items = completion(documentContext, 2, 8);

    // then: совпадает с рекомендацией — видны и реквизиты объекта, и методы ссылки.
    assertThat(items).extracting(CompletionItem::getLabel)
      .contains("Реквизит1", "Реквизит2", "ПолучитьОбъект", "Метаданные");
  }

  @Test
  @DisplayName("2.10 Подсказка по объекту показывает его тип")
  void hoverShowsDeclaredType() {
    // given: у параметра объявлен тип.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Объект - СправочникОбъект.Справочник1 -
      Процедура Тест(Объект)
      	Значение = Объект;
      КонецПроцедуры
      """, context);

    // when
    var hover = hover(documentContext, 3, 13);

    // then: совпадает с рекомендацией.
    assertThat(hover).contains("Тип: СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("2.12 Контекстная подсказка работает при наборе документирующего комментария")
  void completionInsideDocComment() {
    // given: набирается имя типа в секции параметров.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Объект - Справочник
      Процедура Тест(Объект)
      КонецПроцедуры
      """, context);

    // when
    var items = completion(documentContext, 1, 21);

    // then: совпадает с рекомендацией — подсказываются имена типов конфигурации.
    assertThat(items).extracting(CompletionItem::getLabel)
      .contains("Catalogs.Справочник1", "Справочники");
  }

  @Test
  @DisplayName("2.13 Всплывающая подсказка показывает рассчитанный тип")
  void hoverShowsComputedType() {
    // given: тип переменной не объявлен, а рассчитан по присваиванию.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	Ссылка = Справочники.Справочник1.ПустаяСсылка();
      	Значение = Ссылка;
      КонецПроцедуры
      """, context);

    // when
    var hover = hover(documentContext, 2, 13);

    // then: совпадает с рекомендацией.
    assertThat(hover).contains("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("2.14 Подсказка после точки показывает тип свойства и тип-источник")
  void completionItemShowsPropertyTypeButNotItsSource() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	Ссылка = Справочники.Справочник1.ПустаяСсылка();
      	Ссылка.
      КонецПроцедуры
      """, context);

    // when
    var attribute = completion(documentContext, 2, 8).stream()
      .filter(item -> "Реквизит1".equals(item.getLabel()))
      .findFirst()
      .orElseThrow();

    // then: формат рекомендации — «Объект.Свойство <Тип свойства> ~ Тип объекта».
    assertThat(attribute.getDetail())
      .as("рекомендация: в подсказке виден и тип свойства, и тип, из которого оно получено")
      .contains("Строка")
      .contains("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("2.15 Переход к определению объекта")
  void definitionOfLocalMethod() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	ДругойМетод();
      КонецПроцедуры

      Процедура ДругойМетод()
      КонецПроцедуры
      """, context);
    var params = new DefinitionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(1, 5));

    // when
    var definition = definitionProvider.getDefinition(documentContext, params);

    // then: совпадает с рекомендацией — переход ведёт на объявление метода.
    assertThat(definition.getLeft())
      .isNotEmpty()
      .first()
      .satisfies(location -> assertThat(location.getRange().getStart().getLine()).isEqualTo(4));
  }

  @Test
  @DisplayName("2.16 Генератор документирующего описания метода")
  void docCommentGenerator() {
    // given / when
    var titles = codeActionTitles();

    // then
    assertThat(titles)
      .as("рекомендация: описание метода можно создать и дозаполнить по расчётным типам")
      .anyMatch(title -> title.matches("(?iu).*(описани|документир).*"));
  }

  @Test
  @DisplayName("2.17 Панель структуры документирующего комментария")
  void docCommentStructureView() {
    // given / when
    var titles = codeActionTitles();

    // then
    assertThat(titles)
      .as("рекомендация: структуру описания видно так, как её прочитал разбор")
      .anyMatch(title -> title.matches("(?iu).*структур.*(коммент|описани).*"));
  }

  @Test
  @DisplayName("2.25 Групповое включение строгой типизации по проекту или списку объектов")
  void groupStrictTypingCommand() {
    // given / when
    var titles = codeActionTitles();

    // then
    assertThat(titles)
      .as("рекомендация: строгую типизацию включают командой сразу для группы модулей")
      .anyMatch(title -> title.matches("(?iu).*(strict|строг).*"));
  }

  @Test
  @DisplayName("3.1 Ссылка на веб-страницу и на метод конфигурации в описании метода")
  void linksInMethodDescription() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      // В описании ссылки:
      // См. https://1c.ru
      // См. ОбщегоНазначения.ЗначениеВМассиве
      Процедура Тест()
      КонецПроцедуры
      """, context);

    // when
    var links = documentLinkProvider.getDocumentLinks(documentContext);

    // then: совпадает с рекомендацией — обе ссылки становятся переходами.
    assertThat(links).hasSize(2);
    assertThat(links).extracting(link -> link.getTarget())
      .anySatisfy(target -> assertThat(target).isEqualTo("https://1c.ru"))
      // имя модуля в ссылке процентно-закодировано, поэтому сверяем путь до него.
      .anySatisfy(target -> assertThat(target).contains("CommonModules"));
  }

  private List<CompletionItem> completion(DocumentContext documentContext, int line, int character) {
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(line, character));
    return completionProvider.getCompletion(documentContext, params).getItems();
  }

  private String hover(DocumentContext documentContext, int line, int character) {
    var params = new HoverParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(line, character));
    return hoverProvider.getHover(documentContext, params)
      .map(value -> value.getContents().getRight().getValue())
      .orElse("");
  }

  private List<String> codeActionTitles() {
    var documentContext = TestUtils.getDocumentContext("""
      // Описание метода.
      //
      // Параметры:
      //  Объект - СправочникОбъект.Справочник1 -
      Процедура Тест(Объект) Экспорт
      КонецПроцедуры
      """, context);

    var params = new CodeActionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setRange(new Range(new Position(4, 0), new Position(4, 10)));
    var codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(List.of());
    params.setContext(codeActionContext);

    return codeActionProvider.getCodeActions(params, documentContext).stream()
      .map(action -> action.isRight() ? action.getRight().getTitle() : action.getLeft().getTitle())
      .sorted()
      .toList();
  }
}
