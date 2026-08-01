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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormHandlerRoleIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hover для метода, у которого выставлен {@code eventContract}, содержит
 * шапку «Обработчик события платформы: <имя>» и платформенное описание
 * события из bsl-context.
 */
@CleanupContextBeforeClassAndAfterClass
class MethodSymbolMarkupContentBuilderEventHandlerTest extends AbstractServerContextAwareTest {

  @Autowired
  private MethodSymbolMarkupContentBuilder markupContentBuilder;

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  @MockitoBean
  FormHandlerRoleIndex formHandlerRoleIndex;

  @BeforeEach
  void resetResolver() {
    when(formHandlerRoleIndex.roleOf(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
    // Классификация метода в EventMethodSymbol идёт через isEventHandler; у мок-бина делегируем
    // в lookupContract (как в реальном бине), чтобы тесты продолжали задавать только lookupContract.
    when(eventHandlerResolver.isEventHandler(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenAnswer(inv -> eventHandlerResolver.lookupContract(inv.getArgument(0), inv.getArgument(1)).isPresent());
  }

  @Test
  void hoverIncludesEventHandlerHeader() {
    // given
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, ""))
    );
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content)
      .contains("Обработчик события платформы")
      .contains("ПриЗаписи")
      .contains("Возникает при записи объекта.");
  }

  @Test
  void hoverOfCommandHandlerNamesTheCommand() {
    // Обработчик команды событием не является: платформа зовёт его по действию
    // команды, а не по имени события — и в шапке правильнее видеть имя команды.
    var contract = MemberDescriptor.event("ЗаполнитьКоманда", "Обработчик команды формы «Заполнить».", List.of());
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ЗаполнитьКоманда")))
      .thenReturn(Optional.of(contract));
    when(formHandlerRoleIndex.roleOf(ArgumentMatchers.any(), ArgumentMatchers.eq("ЗаполнитьКоманда")))
      .thenReturn(Optional.of(new FormHandlerRoleIndex.Handler(FormHandlerRoleIndex.Role.COMMAND, "Заполнить")));

    var src = """
      Процедура ЗаполнитьКоманда(Команда)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ЗаполнитьКоманда").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .contains("Обработчик команды формы")
      .contains("`Заполнить`")
      .doesNotContain("Обработчик события платформы");
  }

  @Test
  void hoverIncludesEventPlatformMetadata() {
    // given — контракт события с непустыми метаданными синтакс-помощника (замечание + пример),
    // как их приносит bsl-context после #4304
    var metadata = new PlatformMetadata(
      "", "", List.of(), Set.of(), null,
      "", "Срабатывает перед сохранением объекта в информационную базу.",
      List.of("Отказ = Истина;"), List.of()
    );
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, ""))
    ).withMetadata(metadata);
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then — блок метаданных события (замечание/пример) виден в hover обработчика
    assertThat(content)
      .contains("Срабатывает перед сохранением объекта в информационную базу.")
      .contains("Отказ = Истина;");
  }

  @Test
  void parameterTypeIsShownByItsDisplayName() {
    // Репорт: при русской раскладке в сигнатуре светился `Any`. qualifiedName —
    // внутреннее имя типа, наружу идёт отображаемое.
    var value = new ParameterDescriptor(
      BilingualString.of("ВыбранноеЗначение", "SelectedValue"),
      TypeSet.of(TypeRef.ANY), false, BilingualString.EMPTY, "");
    var contract = MemberDescriptor.event("ОбработкаВыбора", "",
      List.of(new SignatureDescriptor(List.of(value), TypeSet.EMPTY, "")));
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ОбработкаВыбора")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ОбработкаВыбора(ВыбранноеЗначение)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ОбработкаВыбора").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .contains("ВыбранноеЗначение**: Произвольный")
      .doesNotContain(": Any");
  }

  @Test
  void userDescriptionsAreMergedWithTheContractOnes() {
    // Шапка-комментарий метода дополняет платформенное описание, а не заменяет его:
    // назначение метода идёт следом за описанием события, описание параметра — через
    // косую от контрактного. Параметр, которого в шапке нет, остаётся с одним
    // платформенным описанием, а параметр без описания в контракте — с одним
    // пользовательским.
    var cancel = new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")), false,
      BilingualString.of("Признак отказа от записи.", "Write cancel flag."), "");
    var mode = new ParameterDescriptor(BilingualString.of("РежимЗаписи", "WriteMode"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка")), false, BilingualString.EMPTY, "");
    var contract = MemberDescriptor.event("ПередЗаписью", "Возникает перед записью объекта.",
      List.of(new SignatureDescriptor(List.of(cancel, mode), TypeSet.EMPTY, "")));
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПередЗаписью")))
      .thenReturn(Optional.of(contract));

    var src = """
      // Не даёт записать документ без склада.
      //
      // Параметры:
      //   Отказ - Булево - выставляем, если склад не заполнен
      //   РежимЗаписи - Строка - режим, в котором идёт запись
      //
      Процедура ПередЗаписью(Отказ, РежимЗаписи)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПередЗаписью").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .as("описание события и назначение метода стоят рядом")
      .contains("Возникает перед записью объекта.")
      .contains("Не даёт записать документ без склада.")
      .as("у параметра с обоими описаниями они идут через косую")
      .contains("Признак отказа от записи. / выставляем, если склад не заполнен")
      .as("у параметра без контрактного описания остаётся пользовательское")
      .contains("РежимЗаписи**: Строка — режим, в котором идёт запись");
  }

  @Test
  void parametersAreNamedAfterTheContractNotTheCode() {
    // Репорт: у ПередЗакрытием(Отказ, СтандартнаяОбработка) в списке параметров
    // `СтандартнаяОбработка` показывалась дважды — второй параметр контракта
    // (`ЗавершениеРаботы`) выводился под именем из кода.
    var cancel = new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")), false, BilingualString.EMPTY, "");
    var shutdown = new ParameterDescriptor(BilingualString.of("ЗавершениеРаботы", "Exit"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")), false, BilingualString.EMPTY, "");
    var standardProcessing = new ParameterDescriptor(
      BilingualString.of("СтандартнаяОбработка", "StandardProcessing"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")), false, BilingualString.EMPTY, "");
    var contract = MemberDescriptor.event("ПередЗакрытием", "",
      List.of(new SignatureDescriptor(List.of(cancel, shutdown, standardProcessing), TypeSet.EMPTY, "")));
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПередЗакрытием")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПередЗакрытием(Отказ, СтандартнаяОбработка)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПередЗакрытием").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .as("параметры перечислены так, как их объявляет платформа")
      .contains("ЗавершениеРаботы")
      .satisfies(text -> assertThat(text.split("СтандартнаяОбработка\\*\\*", -1))
        .as("`СтандартнаяОбработка` — один параметр, а не два")
        .hasSize(2));
  }

  @Test
  void hoverWithContractRendersParameterTypesFromContract() {
    // given — контракт с типизированным параметром Отказ:Булево
    var cancelParam = new ParameterDescriptor(
      BilingualString.of("Отказ", "Cancel"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")),
      false,
      BilingualString.of("Признак отказа от записи.", ""),
      "");
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи объекта.",
      List.of(new SignatureDescriptor(List.of(cancelParam), TypeSet.EMPTY, ""))
    );
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content)
      .contains("Отказ")
      .contains("Булево")
      .contains("Признак отказа от записи.");
  }

  @Test
  void hoverWithoutEventContractHasNoHandlerHeader() {
    // given — resolver по умолчанию возвращает Optional.empty().
    var src = """
      Процедура ОбычныйМетод()
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ОбычныйМетод").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    // then
    assertThat(content).doesNotContain("Обработчик события платформы");
  }

  @Test
  void hoverWithEmptySignaturesShowsNoParametersSection() {
    // Контракт без signatures — секция параметров не выводится (ветка
    // signatures.isEmpty() в getParametersSection).
    var contract = MemberDescriptor.event(
      "ПриЗаписи",
      "Возникает при записи.",
      List.of()
    );
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("ПриЗаписи").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .contains("Возникает при записи.")
      .doesNotContain("**Параметры:**");
  }

  @Test
  void hoverFallsBackToEnNameWhenRuBlank() {
    // У параметра ru-имя пустое — должно использоваться en (ветка name.isBlank()
    // в eventParameterToString).
    var anonymous = new ParameterDescriptor(
      BilingualString.of("", "Cancel"),
      TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Булево")),
      false,
      BilingualString.EMPTY,
      "");
    var contract = MemberDescriptor.event(
      "Handler",
      "",
      List.of(new SignatureDescriptor(List.of(anonymous), TypeSet.EMPTY, ""))
    );
    when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("Handler")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура Handler(Cancel)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var method = documentContext.getSymbolTree().getMethodSymbol("Handler").orElseThrow();

    var content = markupContentBuilder.getContent(referenceTo(documentContext, method)).getValue();

    assertThat(content)
      .contains("Cancel")
      .contains("Булево");
  }

  private Reference referenceTo(DocumentContext documentContext, MethodSymbol method) {
    var loc = new Location(documentContext.getUri().toString(), method.getSubNameRange());
    return Reference.of(documentContext.getSymbolTree().getModule(),
      (SourceDefinedSymbol) method, loc);
  }
}
