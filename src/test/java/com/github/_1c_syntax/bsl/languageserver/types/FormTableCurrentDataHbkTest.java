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
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Строка таблицы формы — в выводе типов и в подсказке — того типа, который задаёт вид
 * данных таблицы: динамический список — {@code ДанныеФормыСтруктура}, дерево значений —
 * {@code ДанныеФормыЭлементДерева}, табличная часть и набор записей —
 * {@code ДанныеФормыЭлементКоллекции}.
 * <p>
 * Тип конкретной таблицы собирается из расширения таблицы под её вид данных, а расширений
 * табличных частей и дерева значений в JSON-фолбэке нет, поэтому тест требует
 * установленной 1С.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (расширения таблицы формы под вид данных)")
class FormTableCurrentDataHbkTest extends AbstractServerContextAwareTest {

  private static final String LIST_FORM_MODULE = "Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl";
  private static final String DOCUMENT_FORM_MODULE = "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";
  private static final String RECORD_SET_FORM_MODULE =
    "InformationRegisters/РегистрСведений1/Forms/ФормаНабораЗаписей/Ext/Form/Module.bsl";

  @Autowired
  private TypeService typeService;

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void dynamicListRowIsFormDataStructure() {
    var documentContext = formModule(LIST_FORM_MODULE, "Список");

    assertThat(rowTypes(documentContext)).containsOnly("ДанныеФормыСтруктура");
    assertThat(completionAfterCurrentData(documentContext, "Список"))
      .contains("Свойство", "Реквизит1")
      .doesNotContain("ПолучитьИдентификатор");
  }

  @Test
  void valueTreeRowIsFormDataTreeItem() {
    var documentContext = formModule(DOCUMENT_FORM_MODULE, "ДеревоПодбора");

    assertThat(rowTypes(documentContext)).containsOnly("ДанныеФормыЭлементДерева");
    assertThat(identifierTypes(documentContext)).containsExactly("Число");
    assertThat(completionAfterCurrentData(documentContext, "ДеревоПодбора"))
      .contains("ПолучитьЭлементы", "ПолучитьРодителя", "Группа", "Пометка");
  }

  @Test
  void tabularSectionRowIsFormDataCollectionItem() {
    var documentContext = formModule(DOCUMENT_FORM_MODULE, "ТабличнаяЧасть1");

    assertThat(rowTypes(documentContext)).containsOnly("ДанныеФормыЭлементКоллекции");
    assertThat(identifierTypes(documentContext)).containsExactly("Число");
    assertThat(completionAfterCurrentData(documentContext, "ТабличнаяЧасть1"))
      .contains("ПолучитьИдентификатор", "Реквизит1");
  }

  @Test
  void recordSetRowIsFormDataCollectionItemWithRecordColumns() {
    var documentContext = formModule(RECORD_SET_FORM_MODULE, "НаборЗаписей");

    assertThat(rowTypes(documentContext)).containsOnly("ДанныеФормыЭлементКоллекции");
    assertThat(identifierTypes(documentContext)).containsExactly("Число");
    assertThat(completionAfterCurrentData(documentContext, "НаборЗаписей"))
      .as("колонки строки набора — поля записи регистра")
      .contains("ПолучитьИдентификатор", "Справочник1");
  }

  /**
   * Модуль формы с обращениями к данным таблицы: {@code ТекущиеДанные}, {@code ДанныеСтроки}
   * и элемент {@code ВыделенныеСтроки}. Предпоследняя строка — место для подсказки.
   */
  private DocumentContext formModule(String modulePath, String tableName) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, modulePath));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, """
      &НаКлиенте
      Процедура Тест()
      \tТекущая = Элементы.%1$s.ТекущиеДанные;
      \tПоИдентификатору = Элементы.%1$s.ДанныеСтроки(Элементы.%1$s.ТекущаяСтрока);
      \tДля Каждого Идентификатор Из Элементы.%1$s.ВыделенныеСтроки Цикл
      \t\tВыделенный = Идентификатор;
      \tКонецЦикла;
      \tЭлементы.%1$s.ТекущиеДанные.
      КонецПроцедуры
      """.formatted(tableName), 1);
    return documentContext;
  }

  /** Отображаемые типы {@code ТекущиеДанные} и {@code ДанныеСтроки}. */
  private List<String> rowTypes(DocumentContext documentContext) {
    var current = typeService.expressionTypesAt(documentContext, beforeSemicolon(documentContext, "Текущая = "));
    var byId = typeService.expressionTypesAt(documentContext, onMethodName(documentContext, "ПоИдентификатору = "));
    return Stream.of(current, byId)
      .flatMap(types -> types.refs().stream())
      .map(ref -> typeService.displayName(ref, Language.RU))
      .toList();
  }

  /** Тип элемента {@code ВыделенныеСтроки}. */
  private List<String> identifierTypes(DocumentContext documentContext) {
    return typeService.expressionTypesAt(documentContext, beforeSemicolon(documentContext, "Выделенный = "))
      .refs().stream()
      .map(ref -> typeService.displayName(ref, Language.RU))
      .toList();
  }

  private List<String> completionAfterCurrentData(DocumentContext documentContext, String tableName) {
    var line = "\tЭлементы." + tableName + ".ТекущиеДанные.";
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(new Position(lineOf(documentContext, line), line.length()));
    return completionProvider.getCompletion(documentContext, params).getItems().stream()
      .map(CompletionItem::getLabel)
      .toList();
  }

  /** Позиция в правой части присваивания — на последнем символе перед {@code ;}. */
  private static Position beforeSemicolon(DocumentContext documentContext, String marker) {
    var content = documentContext.getContent();
    var start = content.indexOf(marker);
    return position(content, content.indexOf(';', start) - 1);
  }

  /** Позиция на имени вызываемого метода — сразу после последней точки перед скобкой. */
  private static Position onMethodName(DocumentContext documentContext, String marker) {
    var content = documentContext.getContent();
    var bracket = content.indexOf('(', content.indexOf(marker));
    return position(content, content.lastIndexOf('.', bracket) + 1);
  }

  private static Position position(String content, int offset) {
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = (int) content.substring(0, offset).chars().filter(c -> c == '\n').count();
    return new Position(line, offset - lineStart);
  }

  private static int lineOf(DocumentContext documentContext, String line) {
    var lines = documentContext.getContent().split("\n");
    for (var index = 0; index < lines.length; index++) {
      if (lines[index].equals(line)) {
        return index;
      }
    }
    throw new IllegalArgumentException(line);
  }
}
