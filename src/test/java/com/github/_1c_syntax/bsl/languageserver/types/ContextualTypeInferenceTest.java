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
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.inlayhints.VariableTypeInlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ContextualTypeInferenceTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");

  @Autowired
  private TypeService typeService;
  @Autowired
  private VariableTypeInlayHintSupplier variableTypeHints;

  @BeforeEach
  void initWorkspace() {
    initServerContext(ROOT);
  }

  @Test
  void ordinaryTypeQueriesUseTheCallingMethodDirective() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Метод_()
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      &НаКлиенте
      Процедура МетодК()
        Б = Метод_(); // клиент
        Сообщить(Б); // клиент
      КонецПроцедуры
      &НаСервере
      Процедура МетодС()
        Б = Метод_(); // сервер
        Сообщить(Б); // сервер
      КонецПроцедуры
      &НаКлиентеНаСервереБезКонтекста
      Процедура МетодКС()
        Б = Метод_(); // оба
      КонецПроцедуры
      """);

    assertThat(expressionAt(document, "Б = Метод_(); // оба", 4)).containsExactlyInAnyOrder("Число", "Строка");
    assertThat(expressionAt(document, "Б = Метод_(); // клиент", 4)).containsExactly("Строка");
    assertThat(expressionAt(document, "Б = Метод_(); // сервер", 4)).containsExactly("Число");
    assertThat(definitionAt(document, "Б", "Б = Метод_(); // клиент")).containsExactly("Строка");
    assertThat(definitionAt(document, "Б", "Б = Метод_(); // сервер")).containsExactly("Число");
    assertThat(expressionAt(document, "Сообщить(Б); // клиент", 9)).containsExactly("Строка");
    assertThat(expressionAt(document, "Сообщить(Б); // сервер", 9)).containsExactly("Число");
    assertThat(expressionAt(document, "Б = Метод_(); // оба", 4)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void ordinaryTypeQueryFollowsServerCallFromClient() {
    var document = form("""
      &НаСервереБезКонтекста
      Функция Метод_()
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      &НаКлиенте
      Процедура МетодК()
        Б = Метод_();
        Сообщить(Б);
      КонецПроцедуры
      """);

    assertThat(expressionAt(document, "Б = Метод_();", 4)).containsExactly("Число");
    assertThat(definitionAt(document, "Б", "Б = Метод_();")).containsExactly("Число");
  }

  @ParameterizedTest
  @CsvSource({"RU, Строка, Число", "EN, String, Number"})
  void variableInlayHintsUseCallerContextAndInterfaceLanguage(Language language, String clientType, String serverType) {
    context.getLanguageServerConfiguration().setLanguage(language);
    var document = form("""
      &AtClientAtServerNoContext
      Function Value()
      #If Server Then
        Return 1;
      #Else
        Return "";
      #EndIf
      EndFunction
      &AtClient
      Procedure Client()
        B = Value();
      EndProcedure
      &AtServer
      Procedure Server()
        B = Value();
      EndProcedure
      """);
    var params = new InlayHintParams(new TextDocumentIdentifier(document.getUri().toString()),
      new Range(new Position(0, 0), new Position(16, 0)));

    var hints = variableTypeHints.getInlayHints(document, params);

    assertThat(hints).extracting(hint -> hint.getLabel().getRight().getFirst().getValue())
      .containsExactly(": " + clientType, ": " + serverType);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void implicitContextResultsAreInvalidatedWhenACalleeChanges(boolean transitive) {
    var callee = TestUtils.getDocumentContext(ROOT.resolve("CommonModules/КлиентскийМодуль/Ext/Module.bsl").toUri(), """
      Функция Значение() Экспорт
        Возврат 1;
      КонецФункции
      """, context);
    var module = "КлиентскийМодуль";
    if (transitive) {
      module = "КлиентСерверныйМодуль";
      TestUtils.getDocumentContext(ROOT.resolve("CommonModules/" + module + "/Ext/Module.bsl").toUri(), """
        Функция Значение() Экспорт
          Возврат КлиентскийМодуль.Значение();
        КонецФункции
        """, context);
    }
    var assignment = "Б = " + module + ".Значение();";
    var caller = form("&НаКлиенте\nПроцедура Пример()\n  " + assignment + "\nКонецПроцедуры");

    assertThat(expressionAt(caller, assignment, 4)).containsExactly("Число");

    context.rebuildDocument(callee, callee.getContent().replace("Возврат 1", "Возврат \"\""), 1);

    assertThat(expressionAt(caller, assignment, 4)).containsExactly("Строка");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Сервер", "Сервер И Клиент"})
  void excludedAssignmentDoesNotDefineVariableType(String condition) {
    var document = form("""
      &НаКлиенте
      Процедура ПодразделениеПриИзменении(Элемент)
        #Если %s Тогда
          А = "Привет";
        #КонецЕсли
        Сообщить(А);
      КонецПроцедуры
      """.formatted(condition));

    assertThat(typesOfLastReference(document, "А")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"НаКлиенте", "НаСервере"})
  void neutralInferenceRespectsMethodDirectiveAndElse(String directive) {
    var document = form("""
      &%s
      Процедура Пример()
        А = Истина;
        #Если Сервер Тогда
          А = "Привет";
        #Иначе
          А = 1;
        #КонецЕсли
        Сообщить(А);
      КонецПроцедуры
      """.formatted(directive));

    assertThat(typesOfLastReference(document, "А"))
      .containsExactly(directive.equals("НаКлиенте") ? "Число" : "Строка");
  }

  @Test
  void excludedAssignmentDoesNotReplaceExistingValue() {
    var document = form("""
      &НаКлиенте
      Процедура Пример()
        А = 1;
        #Если Сервер Тогда
          А = "Привет";
        #КонецЕсли
        Сообщить(А);
      КонецПроцедуры
      """);

    assertThat(typesOfLastReference(document, "А")).containsExactly("Число");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void contradictoryBranchDoesNotContributeTypesWithOrWithoutMetadata(boolean metadata) {
    var source = """
      Процедура Пример()
        #Если Сервер И НЕ Сервер Тогда
          А = 1;
        #Иначе
          А = "";
        #КонецЕсли
        Сообщить(А);
      КонецПроцедуры
      """;
    var document = metadata ? form(source)
      : TestUtils.getDocumentContext(URI.create("file:///unreachable-types.bsl"), source, context);

    assertThat(typesOfLastReference(document, "А")).containsExactly("Строка");
  }

  @Test
  void nestedBranchesCannotRestoreExcludedContexts() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
        А = "";
        #Если Сервер Тогда
          #Если НЕ Сервер Тогда
            А = 1;
          #КонецЕсли
        #КонецЕсли
        Сообщить(А);
      КонецПроцедуры
      """);

    assertThat(typesOfLastReference(document, "А")).containsExactly("Строка");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void repeatedConditionNarrowsEarlierAssignmentsAndTheirAliases(boolean metadata) {
    var source = """
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
        #Если Клиент Тогда
          А = 1;
        #Иначе
          А = "";
        #КонецЕсли
        Б = А;
        #Если Клиент Тогда
          В = Б; // клиент
        #Иначе
          В = Б; // сервер
        #КонецЕсли
        Сообщить(В);
      КонецПроцедуры
      """;
    var document = metadata ? form(source)
      : TestUtils.getDocumentContext(URI.create("file:///repeated-preprocessor.bsl"), source, context);

    assertThat(typesOfLastReference(document, "В")).containsExactlyInAnyOrder("Число", "Строка");
    assertThat(expressionAt(document, "В = Б; // клиент", 4)).containsExactly("Число");
    assertThat(expressionAt(document, "В = Б; // сервер", 4)).containsExactly("Строка");
    assertThat(definitionAt(document, "В", "В = Б; // клиент")).containsExactly("Число");
    assertThat(definitionAt(document, "В", "В = Б; // сервер")).containsExactly("Строка");
    assertThat(typesOfLastReference(document, "В")).containsExactlyInAnyOrder("Число", "Строка");

    context.rebuildDocument(document, source.replaceFirst("#Если Клиент", "#Если НЕ Клиент"), 1);
    assertThat(expressionAt(document, "В = Б; // клиент", 4)).containsExactly("Строка");
    assertThat(expressionAt(document, "В = Б; // сервер", 4)).containsExactly("Число");
  }

  @Test
  void returningVariableInsideBranchUsesEarlierAssignmentsInThatContext() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Значение()
        #Если Клиент Тогда
          А = 1;
        #Иначе
          А = "";
        #КонецЕсли
        #Если Клиент Тогда
          Возврат А;
        #Иначе
          ВызватьИсключение "Только клиент";
        #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
        Результат = Значение();
        Сообщить(Результат);
      КонецПроцедуры
      """);

    assertThat(typesOfLastReference(document, "Результат")).containsExactly("Число");
  }

  private List<String> expressionAt(DocumentContext document, String marker, int offset) {
    return typeService.expressionTypesAt(document, positionOf(document, marker, offset)).refs().stream()
      .map(TypeRef::qualifiedName).toList();
  }

  private List<String> definitionAt(DocumentContext document, String name, String marker) {
    var start = positionOf(document, marker, 0);
    var variable = document.getSymbolTree().getVariables().stream()
      .filter(candidate -> candidate.getName().equals(name)
        && Ranges.containsPosition(candidate.getScope().getRange(), start)).findFirst().orElseThrow();
    var range = new Range(start, new Position(start.getLine(), start.getCharacter() + name.length()));
    var reference = new Reference(document.getSymbolTree().getModule(), variable, document.getUri(), range,
      OccurrenceType.DEFINITION);
    return typeService.typesAt(reference).refs().stream().map(TypeRef::qualifiedName).toList();
  }

  private static Position positionOf(DocumentContext document, String marker, int offset) {
    var source = document.getContent();
    var index = source.indexOf(marker) + offset;
    return new Position(source.substring(0, index).split("\n").length - 1,
      index - source.lastIndexOf('\n', index) - 1);
  }

  @Test
  void neutralFunctionReturnExcludesImpossibleBranch() {
    var document = form("""
      &НаКлиенте
      Функция Значение()
        #Если Сервер Тогда
          Возврат "Привет";
        #Иначе
          Возврат 1;
        #КонецЕсли
      КонецФункции
      &НаКлиенте
      Процедура Пример()
        А = Значение();
        Сообщить(А);
      КонецПроцедуры
      """);

    assertThat(typesOfLastReference(document, "А")).containsExactly("Число");
  }

  private List<String> typesOfLastReference(DocumentContext document, String name) {
    var variable = document.getSymbolTree().getVariables().stream()
      .filter(candidate -> candidate.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
    var source = document.getContent();
    var index = source.lastIndexOf(name);
    var position = new Position(source.substring(0, index).split("\n").length - 1,
      index - source.lastIndexOf('\n', index) - 1);
    return typeService.typesOfVariableAt(variable, position).refs().stream().map(TypeRef::qualifiedName).toList();
  }

  @ParameterizedTest
  @CsvSource({
    "SERVER, СерверныйМодуль", "THIN_CLIENT, КлиентскийМодуль", "WEB_CLIENT, КлиентскийМодуль",
    "THICK_CLIENT_MANAGED_FILE_SERVER, СерверныйМодуль", "THICK_CLIENT_MANAGED_FILE_CLIENT, СерверныйМодуль"
  })
  void assignmentsAndAliasesRespectPreprocessorWithoutLeakingInitialValue(ExecutionContext mode, String expected) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(true);
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
        Модуль = КлиентСерверныйМодуль;
      #Если Сервер Тогда
        Модуль = СерверныйМодуль;
      #ИначеЕсли ВебКлиент Тогда
        Модуль = КлиентскийМодуль;
      #Иначе
        Модуль = КлиентскийМодуль;
      #КонецЕсли
        Копия = Модуль;
        Копия.Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, null)).containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
    assertThat(owners(document, mode)).containsExactly(expected);
    assertThat(owners(document, null)).containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
  }

  @Test
  void contextualFunctionReturnsDoNotPolluteNeutralOrOtherContextCaches() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция ВыбратьМодуль()
      #Если Сервер Тогда
        Возврат СерверныйМодуль;
      #Иначе
        Возврат КлиентскийМодуль;
      #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов()
        ВыбратьМодуль().Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, ExecutionContext.SERVER)).containsExactly("СерверныйМодуль");
    assertThat(owners(document, ExecutionContext.THIN_CLIENT)).containsExactly("КлиентскийМодуль");
    assertThat(owners(document, null)).containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
    assertThat(owners(document, ExecutionContext.SERVER)).containsExactly("СерверныйМодуль");
  }

  @Test
  void serverCallInfersReturnInServerContext() {
    TestUtils.getDocumentContext(ROOT.resolve("CommonModules/СерверныйМодульВызовСервера/Ext/Module.bsl").toUri(), """
      Функция ВыбратьМодуль() Экспорт
      #Если Сервер Тогда
        Возврат СерверныйМодуль;
      #Иначе
        Возврат КлиентскийМодуль;
      #КонецЕсли
      КонецФункции
      """, context);
    var document = form("""
      &НаКлиенте
      Процедура Вызов()
        СерверныйМодульВызовСервера.ВыбратьМодуль().Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, ExecutionContext.THIN_CLIENT)).containsExactly("СерверныйМодуль");
  }

  @Test
  void moduleVariableCellsAreSeparatedByCompilationContext() {
    var document = form("""
      Перем Модуль;
      &НаКлиенте
      Процедура Инициализация()
      #Если ВебКлиент Тогда
        Модуль = КлиентскийМодуль;
      #Иначе
        Модуль = КлиентСерверныйМодуль;
      #КонецЕсли
      КонецПроцедуры
      &НаКлиенте
      Процедура Вызов()
        Модуль.Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, null)).containsExactlyInAnyOrder("КлиентскийМодуль", "КлиентСерверныйМодуль");
    assertThat(owners(document, ExecutionContext.WEB_CLIENT)).containsExactly("КлиентскийМодуль");
    assertThat(owners(document, ExecutionContext.THIN_CLIENT)).containsExactly("КлиентСерверныйМодуль");
    assertThat(owners(document, ExecutionContext.WEB_CLIENT)).containsExactly("КлиентскийМодуль");
    assertThat(owners(document, null)).containsExactlyInAnyOrder("КлиентскийМодуль", "КлиентСерверныйМодуль");

    context.rebuildDocument(document, document.getContent().replace("#Если ВебКлиент", "#Если НЕ ВебКлиент"), 1);
    assertThat(owners(document, ExecutionContext.WEB_CLIENT)).containsExactly("КлиентСерверныйМодуль");
    assertThat(owners(document, ExecutionContext.THIN_CLIENT)).containsExactly("КлиентскийМодуль");
  }

  @Test
  void ordinaryRuntimeBranchesStillPreserveEveryPossibleTarget() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов(Условие)
        Если Условие Тогда
          Модуль = СерверныйМодуль;
        Иначе
          Модуль = КлиентскийМодуль;
        КонецЕсли;
        Модуль.Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, ExecutionContext.SERVER))
      .containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
  }

  @Test
  void loopBackEdgeKeepsEntryAndAssignedReceiverTypes() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Вызов(Продолжать)
        Модуль = СерверныйМодуль;
        Пока Продолжать Цикл
          Результат = Новый Структура("Ключ", 1);
          Модуль = КлиентскийМодуль;
        КонецЦикла;
        Модуль.Цель();
      КонецПроцедуры
      """);

    assertThat(owners(document, null)).containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
    assertThat(owners(document, ExecutionContext.SERVER))
      .containsExactlyInAnyOrder("СерверныйМодуль", "КлиентскийМодуль");
  }

  @Test
  void frozenCalleeKeepsAlreadyInferredReturnTypes() {
    var factory = form("""
      &НаКлиенте
      Функция ВыбратьМодуль() Экспорт
        Возврат КлиентСерверныйМодуль;
      КонецФункции
      &НаКлиенте
      Процедура Вызов()
        ВыбратьМодуль().Цель();
      КонецПроцедуры
      """);
    assertThat(owners(factory, null)).containsExactly("КлиентСерверныйМодуль");
    factory.freezeComputedData();
    assertThat(context.tryClearDocument(factory)).isTrue();
    assertThat(context.getDocumentState(factory)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    var caller = TestUtils.getDocumentContext(
      ROOT.resolve("CommonModules/КлиентскийМодуль/Ext/Module.bsl").toUri(), """
        Процедура Вызов()
          ПолучитьФорму("ОбщаяФорма.Тест").ВыбратьМодуль().Цель();
        КонецПроцедуры
        """, context);
    assertThat(owners(caller, null)).containsExactly("КлиентСерверныйМодуль");

    var actual = owners(caller, ExecutionContext.THIN_CLIENT);

    assertThat(actual).containsExactly("КлиентСерверныйМодуль");
  }

  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private List<String> owners(DocumentContext document, @Nullable ExecutionContext mode) {
    var source = document.getContent();
    var index = source.lastIndexOf(".Цель") + 1;
    var line = source.substring(0, index).split("\n").length - 1;
    var lineStart = source.lastIndexOf('\n', index) + 1;
    return typeService.membersAt(document, new Position(line, index - lineStart), mode).stream()
      .map(TypeService.TypedMember::owner).filter(Objects::nonNull).map(TypeRef::qualifiedName).toList();
  }
}
