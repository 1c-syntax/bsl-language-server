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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Синхронный глобальный метод клиента и его асинхронный аналог — одна таблица на все
 * диагностики синхронных и модальных вызовов.
 * <p>
 * Имена сопоставляются без учёта регистра, на любом из двух языков; аналог отдаётся на языке
 * того написания, по которому метод найден.
 */
enum SynchronousMethod {

  DO_QUERY_BOX("Вопрос", "DoQueryBox", "ПоказатьВопрос", "ShowQueryBox", true, true),
  // Немодальное открытие формы — не метод с оповещением: код после него выполняется сразу
  // и подозрения не вызывает
  OPEN_FORM_MODAL("ОткрытьФормуМодально", "OpenFormModal", "ОткрытьФорму", "OpenForm", true, false),
  OPEN_VALUE("ОткрытьЗначение", "OpenValue", "ПоказатьЗначение", "ShowValue", true, true),
  DO_MESSAGE_BOX("Предупреждение", "DoMessageBox", "ПоказатьПредупреждение", "ShowMessageBox", true, true),
  INPUT_DATE("ВвестиДату", "InputDate", "ПоказатьВводДаты", "ShowInputDate", true, true),
  INPUT_VALUE("ВвестиЗначение", "InputValue", "ПоказатьВводЗначения", "ShowInputValue", true, true),
  INPUT_STRING("ВвестиСтроку", "InputString", "ПоказатьВводСтроки", "ShowInputString", true, true),
  INPUT_NUMBER("ВвестиЧисло", "InputNumber", "ПоказатьВводЧисла", "ShowInputNumber", true, true),
  INSTALL_ADD_IN("УстановитьВнешнююКомпоненту", "InstallAddIn",
    "НачатьУстановкуВнешнейКомпоненты", "BeginInstallAddIn", true, true),
  INSTALL_FILE_SYSTEM_EXTENSION("УстановитьРасширениеРаботыСФайлами", "InstallFileSystemExtension",
    "НачатьУстановкуРасширенияРаботыСФайлами", "BeginInstallFileSystemExtension", true, true),
  INSTALL_CRYPTO_EXTENSION("УстановитьРасширениеРаботыСКриптографией", "InstallCryptoExtension",
    "НачатьУстановкуРасширенияРаботыСКриптографией", "BeginInstallCryptoExtension", true, true),
  ATTACH_CRYPTO_EXTENSION("ПодключитьРасширениеРаботыСКриптографией", "AttachCryptoExtension",
    "НачатьПодключениеРасширенияРаботыСКриптографией", "BeginAttachingCryptoExtension", false, true),
  ATTACH_FILE_SYSTEM_EXTENSION("ПодключитьРасширениеРаботыСФайлами", "AttachFileSystemExtension",
    "НачатьПодключениеРасширенияРаботыСФайлами", "BeginAttachingFileSystemExtension", false, true),
  PUT_FILE("ПоместитьФайл", "PutFile", "НачатьПомещениеФайла", "BeginPutFile", true, true),
  FILE_COPY("КопироватьФайл", "FileCopy", "НачатьКопированиеФайла", "BeginCopyingFile", false, true),
  MOVE_FILE("ПереместитьФайл", "MoveFile", "НачатьПеремещениеФайла", "BeginMovingFile", false, true),
  FIND_FILES("НайтиФайлы", "FindFiles", "НачатьПоискФайлов", "BeginFindingFiles", false, true),
  DELETE_FILES("УдалитьФайлы", "DeleteFiles", "НачатьУдалениеФайлов", "BeginDeletingFiles", false, true),
  CREATE_DIRECTORY("СоздатьКаталог", "CreateDirectory",
    "НачатьСозданиеКаталога", "BeginCreatingDirectory", false, true),
  TEMP_FILES_DIR("КаталогВременныхФайлов", "TempFilesDir",
    "НачатьПолучениеКаталогаВременныхФайлов", "BeginGettingTempFilesDir", false, true),
  DOCUMENTS_DIR("КаталогДокументов", "DocumentsDir",
    "НачатьПолучениеКаталогаДокументов", "BeginGettingDocumentsDir", false, true),
  USER_DATA_WORK_DIR("РабочийКаталогДанныхПользователя", "UserDataWorkDir",
    "НачатьПолучениеРабочегоКаталогаДанныхПользователя", "BeginGettingUserDataWorkDir", false, true),
  GET_FILES("ПолучитьФайлы", "GetFiles", "НачатьПолучениеФайлов", "BeginGettingFiles", false, true),
  PUT_FILES("ПоместитьФайлы", "PutFiles", "НачатьПомещениеФайлов", "BeginPuttingFiles", false, true),
  REQUEST_USER_PERMISSION("ЗапроситьРазрешениеПользователя", "RequestUserPermission",
    "НачатьЗапросРазрешенияПользователя", "BeginRequestingUserPermission", false, true),
  RUN_APP("ЗапуститьПриложение", "RunApp", "НачатьЗапускПриложения", "BeginRunningApplication", false, true);

  private static final Map<String, SynchronousMethod> BY_NAME = new HashMap<>();
  private static final Map<String, String> ANALOG_BY_NAME = new HashMap<>();
  private static final Map<String, SynchronousMethod> BY_CALLBACK_ANALOG = new HashMap<>();

  static {
    for (var method : values()) {
      BY_NAME.put(key(method.nameRu), method);
      BY_NAME.put(key(method.nameEn), method);
      ANALOG_BY_NAME.put(key(method.nameRu), method.analogRu);
      ANALOG_BY_NAME.put(key(method.nameEn), method.analogEn);
      if (method.callbackAnalog) {
        BY_CALLBACK_ANALOG.put(key(method.analogRu), method);
        BY_CALLBACK_ANALOG.put(key(method.analogEn), method);
      }
    }
  }

  private final String nameRu;
  private final String nameEn;
  private final String analogRu;
  private final String analogEn;
  private final boolean modal;
  private final boolean callbackAnalog;

  SynchronousMethod(String nameRu, String nameEn, String analogRu, String analogEn,
                    boolean modal, boolean callbackAnalog) {
    this.nameRu = nameRu;
    this.nameEn = nameEn;
    this.analogRu = analogRu;
    this.analogEn = analogEn;
    this.modal = modal;
    this.callbackAnalog = callbackAnalog;
  }

  /**
   * Синхронный метод по имени вызова.
   *
   * @param methodName имя вызываемого метода в любом регистре и на любом из двух языков.
   * @return метод; пусто, если у вызванного метода нет асинхронного аналога в таблице.
   */
  static Optional<SynchronousMethod> byName(String methodName) {
    return Optional.ofNullable(BY_NAME.get(key(methodName)));
  }

  /**
   * Асинхронный аналог на языке написания вызова.
   *
   * @param methodName имя вызываемого синхронного метода — то, по которому он найден {@link #byName}.
   * @return имя аналога на том же языке, что и {@code methodName}.
   */
  static String analogOf(String methodName) {
    return ANALOG_BY_NAME.get(key(methodName));
  }

  /**
   * Является ли метод асинхронным аналогом с оповещением о завершении — после такого вызова
   * код продолжает выполняться, не дожидаясь результата.
   *
   * @param methodName имя вызываемого метода в любом регистре и на любом из двух языков.
   * @return {@code true}, если это асинхронный аналог с оповещением.
   */
  static boolean isCallbackAnalog(String methodName) {
    return BY_CALLBACK_ANALOG.containsKey(key(methodName));
  }

  /**
   * Открывает ли метод модальное окно.
   *
   * @return {@code true} для методов, блокирующих интерфейс модальным окном.
   */
  boolean isModal() {
    return modal;
  }

  private static String key(String name) {
    return name.toUpperCase(Locale.ROOT);
  }
}
