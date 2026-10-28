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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.Check;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnavailableMember;
import com.github._1c_syntax.bsl.languageserver.types.MemberAccessChecker.UnknownMember;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownMemberCompilationContextTest extends AbstractDiagnosticTest<UnknownMemberDiagnostic> {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  private static final Set<Check> BOTH = Set.of(Check.EXISTENCE, Check.AVAILABILITY);

  @Autowired
  private MemberAccessChecker checker;

  @Autowired
  private TypeService typeService;

  @Autowired
  private ReferenceResolver referenceResolver;

  UnknownMemberCompilationContextTest() {
    super(UnknownMemberDiagnostic.class);
  }

  @ParameterizedTest
  @CsvSource({"RU, RU, Массив", "RU, EN, Array", "EN, RU, Массив", "EN, EN, Array"})
  void localizesMessagesAndUsesTheProjectLanguageForTypeNames(Language ui, Language script, String typeName,
                                                              @TempDir Path projectRoot) throws IOException {
    FileUtils.copyDirectory(ROOT.toFile(), projectRoot.toFile());
    var configurationFile = projectRoot.resolve("Configuration.xml");
    Files.writeString(configurationFile, Files.readString(configurationFile).replace(
      "<ScriptVariant>Russian</ScriptVariant>",
      "<ScriptVariant>" + (script == Language.EN ? "English" : "Russian") + "</ScriptVariant>"));
    initServerContext(projectRoot);
    var document = TestUtils.getDocumentContext(projectRoot.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), """
      &AtServerNoContext
      Procedure Caller()
        Value = New Array;
        Value.Typo();
      EndProcedure
      """, context);
    assertThat(document.getScriptVariantLanguage()).isEqualTo(script);
    configuration.setLanguage(ui);
    diagnosticInstance.getInfo().refresh();

    assertThat(getDiagnostics(document)).singleElement().satisfies(diagnostic -> {
      assertThat(diagnostic.getMessage().getLeft()).isEqualTo(ui == Language.EN
        ? "Type \"" + typeName + "\" has no method or property \"Typo\""
        : "У типа \"" + typeName + "\" нет метода или свойства \"Typo\"");
      assertThat(diagnostic.getRelatedInformation().getLast().getMessage()).isEqualTo(ui == Language.EN
        ? "Compilation context: Server" : "Контекст компиляции: Сервер");
    });
  }

  @Test
  void receiverFromAnotherContextDoesNotHideMissingMethod() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
      #Если Сервер Тогда
        Значение = Новый Соответствие;
      #Иначе
        Значение = Новый Массив;
      #КонецЕсли
        Значение.Добавить(1);
      КонецПроцедуры
      """);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("Соответствие", "Добавить").doesNotContain("Массив");
    assertThat(diagnostics.getFirst().getRelatedInformation().getLast().getMessage())
      .isEqualTo("Контекст компиляции: Сервер");
    assertThat(checker.check(document, Set.of(ExecutionContext.THIN_CLIENT), BOTH)).isEmpty();
  }

  @Test
  void structureFieldsAreCheckedInTheirCompilationContext() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
      #Если Сервер Тогда
        Значение = Новый Структура("Серверное", 1);
      #Иначе
        Значение = Новый Структура("Клиентское", 1);
      #КонецЕсли
        Сообщить(Значение.Клиентское);
      КонецПроцедуры
      """);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("Структура", "Клиентское");
    assertThat(diagnostics.getFirst().getRelatedInformation().getLast().getMessage()).contains("Сервер");
  }

  @Test
  void referenceTypeQueriesPreserveCompilationContext() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
      #Если Сервер Тогда
        Значение = Новый Соответствие;
      #Иначе
        Значение = Новый Массив;
      #КонецЕсли
        Значение.Добавить(1);
      КонецПроцедуры
      """);
    var reference = referenceResolver.findReference(document.getUri(), new Position(7, 3)).orElseThrow();

    assertThat(typeService.typesAt(reference, ExecutionContext.SERVER).refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Соответствие");
    assertThat(typeService.typesAt(reference, ExecutionContext.THIN_CLIENT).refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Массив");
    assertThat(typeService.typesAt(reference).refs()).extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Массив", "Соответствие");
  }

  @Test
  void returnTypeIsNarrowedBeforeCheckingTheMember() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Коллекция()
      #Если Сервер Тогда
        Возврат Новый Соответствие;
      #Иначе
        Возврат Новый Массив;
      #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Процедура Пример()
        Коллекция().Добавить(1);
      КонецПроцедуры
      """);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("Соответствие", "Добавить");
  }

  @Test
  void methodDirectiveAndElseExcludeTheWrongReceiver() {
    var document = form("""
      &НаКлиенте
      Процедура Пример()
      #Если Сервер Тогда
        Значение = Новый Соответствие;
        НесуществующаяПроцедура();
      #Иначе
        Значение = Новый Массив;
      #КонецЕсли
        Значение.Добавить(1);
      КонецПроцедуры
      """);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "#Если Сервер И НЕ Сервер Тогда\n%s\n#КонецЕсли",
    "#Если Сервер Тогда\n#Если НЕ Сервер Тогда\n%s\n#КонецЕсли\n#КонецЕсли",
    "#Если Сервер Тогда\n#ИначеЕсли НЕ Сервер Тогда\n#Иначе\n%s\n#КонецЕсли"
  })
  void impossibleBranchesDoNotProduceUnknownMemberMessages(String wrapper) {
    var source = wrapper.formatted("""
      Процедура Пример()
        Значение = Новый Массив;
        Значение.Опечатка();
        НесуществующаяПроцедура();
      КонецПроцедуры
      """);
    var document = standalone(source, FileType.BSL);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(FileType.class)
  void unknownPreprocessorSymbolsPreserveOrdinaryNameChecks(FileType type) {
    var document = standalone("""
      #Если Windows Тогда
        НесуществующаяПервая();
      #Иначе
        НесуществующаяВторая();
      #КонецЕсли
      """, type);

    assertThat(getDiagnostics(document)).hasSize(2);
  }

  @ParameterizedTest
  @EnumSource(FileType.class)
  void symbolContradictionsAreSkippedInBothLanguages(FileType type) {
    var document = standalone("""
      #Если Windows И НЕ Windows Тогда
        НесуществующаяПроцедура();
      #КонецЕсли
      """, type);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"BSL, 0", "OS, 1"})
  void oneScriptDoesNotInheritPlatformContextRestrictions(FileType type, int count) {
    var document = standalone("""
      #Если ТонкийКлиент И Сервер Тогда
        НесуществующаяПроцедура();
      #КонецЕсли
      """, type);

    assertThat(getDiagnostics(document)).hasSize(count);
  }

  @Test
  void standaloneFilesKeepTypeAndGlobalNameChecks() {
    var document = standalone("""
      Процедура Пример(Неизвестный)
        Массив = Новый Массив;
        Массив.Опечатка();
        НесуществующаяПроцедура();
        Неизвестный.ЛюбоеИмя();
      КонецПроцедуры
      """, FileType.BSL);

    assertThat(getDiagnostics(document)).hasSize(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"СерверныйМодуль.Цель();", "КлиентСерверныйМодуль.ЦельЕслиСервер();"})
  void knownUnavailableMethodsAreNotUnknownMembers(String call) {
    var document = form("&НаКлиенте\nПроцедура Пример()\n" + call + "\nКонецПроцедуры\n");

    assertThat(getDiagnostics(document)).isEmpty();
    assertThat(checker.check(document, ExecutionContext.ALL, BOTH))
      .allSatisfy(problem -> assertThat(problem).isInstanceOf(UnavailableMember.class))
      .extracting(problem -> ((UnavailableMember) problem).context())
      .containsExactlyInAnyOrder(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT, ExecutionContext.MOBILE_CLIENT,
        ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_ORDINARY,
        ExecutionContext.MOBILE_APPLICATION_CLIENT);
  }

  @Test
  void formContextRestrictionIsNotMistakenForMissingMember() {
    var document = form("""
      &НаСервереБезКонтекста
      Процедура Пример()
        Цель();
      КонецПроцедуры
      &НаСервере
      Процедура Цель()
      КонецПроцедуры
      """);

    assertThat(getDiagnostics(document)).isEmpty();
    assertThat(checker.check(document, ExecutionContext.ALL, BOTH))
      .allSatisfy(problem -> assertThat(problem).isInstanceOf(UnavailableMember.class))
      .extracting(problem -> ((UnavailableMember) problem).context())
      .containsExactly(ExecutionContext.SERVER, ExecutionContext.MOBILE_APPLICATION_SERVER,
        ExecutionContext.MOBILE_STANDALONE_SERVER);
    assertThat(checker.check(document, ExecutionContext.ALL, Set.of(Check.EXISTENCE))).isEmpty();
  }

  @Test
  void validServerTransitionDoesNotProduceEitherProblem() {
    var document = form("""
      &НаКлиенте
      Процедура Пример()
        Цель();
      КонецПроцедуры
      &НаСервереБезКонтекста
      Процедура Цель()
      КонецПроцедуры
      """);

    assertThat(checker.check(document, ExecutionContext.ALL, BOTH)).isEmpty();
  }

  @Test
  void distinctReceiverProblemsArePreservedWithoutDuplicatingEither() {
    var document = form("""
      &НаКлиенте
      Процедура Пример(Условие)
        Если Условие Тогда
          Получатель = СерверныйМодуль;
        Иначе
          Получатель = Новый Массив;
        КонецЕсли;
        Получатель.Цель();
      КонецПроцедуры
      """);

    var problems = checker.check(document, Set.of(ExecutionContext.THIN_CLIENT), BOTH);
    assertThat(problems).hasSize(3);
    assertThat(problems).filteredOn(UnknownMember.class::isInstance).singleElement().satisfies(problem ->
      assertThat(((UnknownMember) problem).receiverTypes().refs()).extracting(TypeRef::qualifiedName)
        .containsExactly("Массив"));
    assertThat(problems).filteredOn(problem -> problem instanceof UnavailableMember member
      && member.kind() == MemberKind.METHOD).singleElement().satisfies(problem ->
      assertThat(((UnavailableMember) problem).moduleName()).contains("СерверныйМодуль"));
    assertThat(problems).filteredOn(problem -> problem instanceof UnavailableMember member
      && member.kind() == MemberKind.PROPERTY).singleElement().satisfies(problem ->
      assertThat(((UnavailableMember) problem).range().getStart().getLine()).isEqualTo(3));
  }

  @Test
  void aKnownReceiverIsCheckedEvenAlongsideAnUnknownOne() {
    var document = standalone("""
      Процедура Пример(Условие, Неизвестный)
        Если Условие Тогда
          Получатель = Неизвестный;
        Иначе
          Получатель = Новый Массив;
        КонецЕсли;
        Получатель.Опечатка();
      КонецПроцедуры
      """, FileType.BSL);

    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.getFirst().getMessage().getLeft()).contains("Массив", "Опечатка");
  }

  @Test
  void theSameCheckerDoesNotRetainThePreviousFilesBranches() {
    assertThat(getDiagnostics(standalone("#Если Windows И НЕ Windows Тогда\nНетМетода();\n#КонецЕсли", FileType.OS)))
      .isEmpty();
    assertThat(getDiagnostics(standalone("НетМетода();", FileType.OS))).hasSize(1);
  }

  private DocumentContext form(String source) {
    initServerContext(ROOT);
    return TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private DocumentContext standalone(String source, FileType type) {
    initServerContext();
    return TestUtils.getDocumentContext(URI.create(type == FileType.OS ? "file:///unknown-member.os"
      : "file:///unknown-member.bsl"), source, context);
  }
}
