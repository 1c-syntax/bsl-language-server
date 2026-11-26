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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.types.MultiName;

/**
 * Класс-справочник ключевых слов языка BSL.
 * <p>
 * Содержит константы с мультиязычными названиями ключевых слов
 * (русский и английский варианты) для использования в анализаторах.
 */
public final class Keywords {
  public static final MultiName THEN = MultiName.create("Then", "Тогда");
  public static final MultiName THEN_UP = MultiName.create("THEN", "ТОГДА");
  public static final MultiName IF = MultiName.create("If", "Если");
  public static final MultiName IF_UP = MultiName.create("IF", "ЕСЛИ");
  public static final MultiName ELSE = MultiName.create("Else", "Иначе");
  public static final MultiName ELSE_UP = MultiName.create("ELSE", "ИНАЧЕ");
  public static final MultiName ELSIF = MultiName.create("ElsIf", "ИначеЕсли");
  public static final MultiName ELSIF_UP = MultiName.create("ELSIF", "ИНАЧЕЕСЛИ");
  public static final MultiName ENDIF = MultiName.create("EndIf", "КонецЕсли");
  public static final MultiName ENDIF_UP = MultiName.create("ENDIF", "КОНЕЦЕСЛИ");

  public static final MultiName FOR = MultiName.create("For", "Для");
  public static final MultiName FOR_UP = MultiName.create("FOR", "ДЛЯ");
  public static final MultiName EACH = MultiName.create("Each", "Каждого");
  public static final MultiName EACH_UP = MultiName.create("EACH", "КАЖДОГО");
  public static final MultiName EACH_LO = MultiName.create("each", "каждого");
  public static final MultiName IN = MultiName.create("In", "Из");
  public static final MultiName IN_UP = MultiName.create("IN", "ИЗ");
  public static final MultiName DO = MultiName.create("Do", "Цикл");
  public static final MultiName DO_UP = MultiName.create("DO", "ЦИКЛ");
  public static final MultiName WHILE = MultiName.create("While", "Пока");
  public static final MultiName WHILE_UP = MultiName.create("WHILE", "ПОКА");
  public static final MultiName END_DO = MultiName.create("EndDo", "КонецЦикла");
  public static final MultiName END_DO_UP = MultiName.create("ENDDO", "КОНЕЦЦИКЛА");
  public static final MultiName TO = MultiName.create("To", "По");
  public static final MultiName TO_UP = MultiName.create("TO", "ПО");

  public static final MultiName BREAK = MultiName.create("Break", "Прервать");
  public static final MultiName BREAK_UP = MultiName.create("BREAK", "ПРЕРВАТЬ");
  public static final MultiName CONTINUE = MultiName.create("Continue", "Продолжить");
  public static final MultiName CONTINUE_UP = MultiName.create("CONTINUE", "ПРОДОЛЖИТЬ");
  public static final MultiName RETURN = MultiName.create("Return", "Возврат");
  public static final MultiName RETURN_UP = MultiName.create("RETURN", "ВОЗВРАТ");
  public static final MultiName GOTO = MultiName.create("Goto", "Перейти");
  public static final MultiName GOTO_UP = MultiName.create("GOTO", "ПЕРЕЙТИ");

  public static final MultiName PROCEDURE = MultiName.create("Procedure", "Процедура");
  public static final MultiName PROCEDURE_UP = MultiName.create("PROCEDURE", "ПРОЦЕДУРА");
  public static final MultiName END_PROCEDURE = MultiName.create("EndProcedure", "КонецПроцедуры");
  public static final MultiName END_PROCEDURE_UP = MultiName.create("ENDPROCEDURE", "КОНЕЦПРОЦЕДУРЫ");
  public static final MultiName FUNCTION = MultiName.create("Function", "Функция");
  public static final MultiName FUNCTION_UP = MultiName.create("FUNCTION", "ФУНКЦИЯ");
  public static final MultiName END_FUNCTION = MultiName.create("EndFunction", "КонецФункции");
  public static final MultiName END_FUNCTION_UP = MultiName.create("ENDFUNCTION", "КОНЕЦФУНКЦИИ");

  public static final MultiName VAL = MultiName.create("Val", "Знач");
  public static final MultiName VAL_UP = MultiName.create("VAL", "ЗНАЧ");
  public static final MultiName EXPORT = MultiName.create("Export", "Экспорт");
  public static final MultiName EXPORT_UP = MultiName.create("EXPORT", "ЭКСПОРТ");
  public static final MultiName VAR = MultiName.create("Var", "Перем");
  public static final MultiName VAR_UP = MultiName.create("VAR", "ПЕРЕМ");

  public static final MultiName TRY = MultiName.create("Try", "Попытка");
  public static final MultiName TRY_UP = MultiName.create("TRY", "ПОПЫТКА");
  public static final MultiName EXCEPT = MultiName.create("Except", "Исключение");
  public static final MultiName EXCEPT_UP = MultiName.create("EXCEPT", "ИСКЛЮЧЕНИЕ");
  public static final MultiName RAISE = MultiName.create("Raise", "ВызватьИсключение");
  public static final MultiName RAISE_UP = MultiName.create("RAISE", "ВЫЗВАТЬИСКЛЮЧЕНИЕ");
  public static final MultiName END_TRY = MultiName.create("EndTry", "КонецПопытки");
  public static final MultiName END_TRY_UP = MultiName.create("ENDTRY", "КОНЕЦПОПЫТКИ");

  public static final MultiName REGION = MultiName.create("Region", "Область");
  public static final MultiName REGION_UP = MultiName.create("REGION", "ОБЛАСТЬ");
  public static final MultiName ENDREGION = MultiName.create("EndRegion", "КонецОбласти");
  public static final MultiName ENDREGION_UP = MultiName.create("ENDREGION", "КОНЕЦОБЛАСТИ");

  public static final MultiName EXECUTE = MultiName.create("Execute", "Выполнить");
  public static final MultiName EXECUTE_UP = MultiName.create("EXECUTE", "ВЫПОЛНИТЬ");
  public static final MultiName EVAL = MultiName.create("Eval", "Вычислить");
  public static final MultiName ADD_HANDLER = MultiName.create("AddHandler", "ДобавитьОбработчик");
  public static final MultiName ADD_HANDLER_UP = MultiName.create("ADDHANDLER", "ДОБАВИТЬОБРАБОТЧИК");
  public static final MultiName REMOVE_HANDLER = MultiName.create("RemoveHandler", "УдалитьОбработчик");
  public static final MultiName REMOVE_HANDLER_UP = MultiName.create("REMOVEHANDLER", "УДАЛИТЬОБРАБОТЧИК");
  public static final MultiName NEW = MultiName.create("New", "Новый");
  public static final MultiName NEW_UP = MultiName.create("NEW", "НОВЫЙ");

  public static final MultiName TRUE = MultiName.create("True", "Истина");
  public static final MultiName TRUE_UP = MultiName.create("TRUE", "ИСТИНА");
  public static final MultiName FALSE = MultiName.create("False", "Ложь");
  public static final MultiName FALSE_UP = MultiName.create("FALSE", "ЛОЖЬ");
  public static final MultiName AND = MultiName.create("And", "И");
  public static final MultiName AND_UP = MultiName.create("AND", "И");
  public static final MultiName OR = MultiName.create("Or", "Или");
  public static final MultiName OR_UP = MultiName.create("OR", "ИЛИ");
  public static final MultiName NOT = MultiName.create("Not", "Не");
  public static final MultiName NOT_UP = MultiName.create("NOT", "НЕ");
  public static final MultiName UNDEFINED = MultiName.create("Undefined", "Неопределено");
  public static final MultiName UNDEFINED_UP = MultiName.create("UNDEFINED", "НЕОПРЕДЕЛЕНО");

  public static final MultiName SERVER = MultiName.create("Server", "Сервер");
  public static final MultiName SERVER_UP = MultiName.create("SERVER", "СЕРВЕР");
  public static final MultiName CLIENT = MultiName.create("Client", "Клиент");
  public static final MultiName CLIENT_UP = MultiName.create("CLIENT", "КЛИЕНТ");
  public static final MultiName MOBILE_APP_CLIENT =
    MultiName.create("MobileAppClient", "МобильноеПриложениеКлиент");
  public static final MultiName MOBILE_APP_CLIENT_UP =
    MultiName.create("MOBILEAPPCLIENT", "МОБИЛЬНОЕПРИЛОЖЕНИЕКЛИЕНТ");
  public static final MultiName MOBILE_APP_SERVER =
    MultiName.create("MobileAppServer", "МобильноеПриложениеСервер");
  public static final MultiName MOBILE_APP_SERVER_UP =
    MultiName.create("MOBILEAPPSERVER", "МОБИЛЬНОЕПРИЛОЖЕНИЕСЕРВЕР");
  public static final MultiName MOBILE_CLIENT = MultiName.create("MobileClient", "МобильныйКлиент");
  public static final MultiName MOBILE_CLIENT_UP = MultiName.create("MOBILECLIENT", "МОБИЛЬНЫЙКЛИЕНТ");
  public static final MultiName THICK_CLIENT_ORDINARY_APPLICATION =
    MultiName.create("ThickClientOrdinaryApplication", "ТолстыйКлиентОбычноеПриложение");
  public static final MultiName THICK_CLIENT_ORDINARY_APPLICATION_UP =
    MultiName.create("THICKCLIENTORDINARYAPPLICATION", "ТОЛСТЫЙКЛИЕНТОБЫЧНОЕПРИЛОЖЕНИЕ");
  public static final MultiName THICK_CLIENT_MANAGED_APPLICATION =
    MultiName.create("ThickClientManagedApplication", "ТолстыйКлиентУправляемоеПриложение");
  public static final MultiName THICK_CLIENT_MANAGED_APPLICATION_UP =
    MultiName.create("THICKCLIENTMANAGEDAPPLICATION", "ТОЛСТЫЙКЛИЕНТУПРАВЛЯЕМОЕПРИЛОЖЕНИЕ");
  public static final MultiName EXTERNAL_CONNECTION =
    MultiName.create("ExternalConnection", "ВнешнееСоединение");
  public static final MultiName EXTERNAL_CONNECTION_UP =
    MultiName.create("EXTERNALCONNECTION", "ВНЕШНЕЕСОЕДИНЕНИЕ");
  public static final MultiName THIN_CLIENT = MultiName.create("ThinClient", "ТонкийКлиент");
  public static final MultiName THIN_CLIENT_UP = MultiName.create("THINCLIENT", "ТОНКИЙКЛИЕНТ");
  public static final MultiName WEB_CLIENT = MultiName.create("WebClient", "ВебКлиент");
  public static final MultiName WEB_CLIENT_UP = MultiName.create("WEBCLIENT", "ВЕБКЛИЕНТ");

  public static final MultiName AT_CLIENT = MultiName.create("AtClient", "НаКлиенте");
  public static final MultiName AT_CLIENT_UP = MultiName.create("ATCLIENT", "НАКЛИЕНТЕ");
  public static final MultiName AT_SERVER = MultiName.create("AtServer", "НаСервере");
  public static final MultiName AT_SERVER_UP = MultiName.create("ATSERVER", "НАСЕРВЕРЕ");
  public static final MultiName AT_SERVER_NO_CONTEXT =
    MultiName.create("AtServerNoContext", "НаСервереБезКонтекста");
  public static final MultiName AT_SERVER_NO_CONTEXT_UP =
    MultiName.create("ATSERVERNOCONTEXT", "НАСЕРВЕРЕБЕЗКОНТЕКСТА");
  public static final MultiName AT_CLIENT_AT_SERVER_NO_CONTEXT =
    MultiName.create("AtClientAtServerNoContext", "НаКлиентеНаСервереБезКонтекста");
  public static final MultiName AT_CLIENT_AT_SERVER_NO_CONTEXT_UP =
    MultiName.create("ATCLIENTATSERVERNOCONTEXT", "НАКЛИЕНТЕНАСЕРВЕРЕБЕЗКОНТЕКСТА");
  public static final MultiName AT_CLIENT_AT_SERVER =
    MultiName.create("AtClientAtServer", "НаКлиентеНаСервере");
  public static final MultiName AT_CLIENT_AT_SERVER_UP =
    MultiName.create("ATCLIENTATSERVER", "НАКЛИЕНТЕНАСЕРВЕРЕ");

  public static final MultiName PUBLIC_REGION = MultiName.create("Public", "ПрограммныйИнтерфейс");
  public static final MultiName INTERNAL_REGION = MultiName.create("Internal", "СлужебныйПрограммныйИнтерфейс");
  public static final MultiName PRIVATE_REGION = MultiName.create("Private", "СлужебныеПроцедурыИФункции");
  public static final MultiName EVENT_HANDLERS_REGION = MultiName.create("EventHandlers", "ОбработчикиСобытий");
  public static final MultiName FORM_EVENT_HANDLERS_REGION =
    MultiName.create("FormEventHandlers", "ОбработчикиСобытийФормы");
  public static final MultiName FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION =
    MultiName.create("FormHeaderItemsEventHandlers", "ОбработчикиСобытийЭлементовШапкиФормы");
  public static final MultiName FORM_COMMANDS_EVENT_HANDLERS_REGION =
    MultiName.create("FormCommandsEventHandlers", "ОбработчикиКомандФормы");
  public static final MultiName VARIABLES_REGION = MultiName.create("Variables", "ОписаниеПеременных");
  public static final MultiName INITIALIZE_REGION = MultiName.create("Initialize", "Инициализация");
  public static final MultiName FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START =
    MultiName.create("FormTableItemsEventHandlers", "ОбработчикиСобытийЭлементовТаблицыФормы");
  public static final MultiName ASYNC = MultiName.create("Async", "Асинх");
  public static final MultiName AWAIT = MultiName.create("Await", "Ждать");

  private Keywords() {
    // static utils
  }
}
