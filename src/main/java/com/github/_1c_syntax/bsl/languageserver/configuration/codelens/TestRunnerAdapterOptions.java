/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.configuration.codelens;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SystemUtils;

/**
 * Параметры запускателя тестового фреймворка.
 */
@Data
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestRunnerAdapterOptions {

  /**
   * Имя исполняемого файла тестового фреймворка (linux и macOS).
   */
  private String executable = "1testrunner";
  /**
   * Имя исполняемого файла тестового фреймворка (windows).
   */
  private String executableWin = "1testrunner.bat";
  /**
   * Аргументы для получения списка тестов.
   */
  private String getTestsArguments = "-show %s";
  /**
   * Регулярное выражение для получения списка тестов.
   */
  private String getTestsResultPattern = "^[^<]*Имя\\sтеста\\s<([^>]+)>.*";
  /**
   * Аргументы для запуска одного теста.
   */
  private String runTestArguments = "-run %s %s";
  /**
   * Аргументы для запуска всех тестов.
   */
  private String runAllTestsArguments = "-run %s";

  /**
   * Получить имя исполняемого файла тестового фреймворка для текущей ОС.
   *
   * @return Имя исполняемого файла тестового фреймворка для текущей ОС.
   */
  public String getExecutableForCurrentOS() {
    return SystemUtils.IS_OS_WINDOWS ? executableWin : executable;
  }
}
