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
package com.github._1c_syntax.bsl.languageserver.codelenses.testrunner;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Расчетчик списка тестов в документе.
 * Физически выполняет команды по получению идентификаторов тестов на основании конфигурации.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "testIds")
public class TestRunnerAdapter {

  private static final Pattern NEW_LINE_PATTERN = Pattern.compile("\r?\n");

  private final LanguageServerConfiguration configuration;

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * Очищает кэш при изменении конфигурации.
   *
   * @param event Событие
   */
  @EventListener
  @CacheEvict(allEntries = true)
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
  }

  /**
   * Получить идентификаторы тестов, содержащихся в файле.
   *
   * @param documentContext Контекст документа с тестами.
   * @return Список идентификаторов тестов.
   */
  @Cacheable
  public List<String> getTestIds(DocumentContext documentContext) {
    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();

    if (options.isGetTestsByTestRunner()) {
      return computeTestIdsByTestRunner(documentContext);
    }

    return computeTestIdsByLanguageServer(documentContext);
  }

  private List<String> computeTestIdsByTestRunner(DocumentContext documentContext) {
    var options = configuration.getCodeLensOptions().getTestRunnerAdapterOptions();

    var executable = SystemUtils.IS_OS_WINDOWS ? options.getExecutableWin() : options.getExecutable();
    var path = Paths.get(documentContext.getUri()).toString();
    var arguments = String.format(options.getGetTestsArguments(), path);

    var getTestsCommand = new CommandLine(executable).addArguments(arguments, false);

    var timeout = 10_000L;
    var watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofMillis(timeout)).get();

    var outputStream = new ByteArrayOutputStream();
    var streamHandler = new PumpStreamHandler(outputStream);

    var resultHandler = new DefaultExecuteResultHandler();

    var executor = DefaultExecutor.builder().get();
    executor.setWatchdog(watchdog);
    executor.setStreamHandler(streamHandler);

    try {
      executor.execute(getTestsCommand, resultHandler);
    } catch (IOException e) {
      LOGGER.error("Can't execute testrunner getTests command", e);
      return Collections.emptyList();
    }
    try {
      resultHandler.waitFor();
    } catch (InterruptedException e) {
      LOGGER.error("Can't wait for testrunner getTests command", e);
      Thread.currentThread().interrupt();
      return Collections.emptyList();
    }

    var getTestsRegex = Pattern.compile(options.getGetTestsResultPattern());

    Charset charset;
    if (SystemUtils.IS_OS_WINDOWS) {
      charset = Charset.forName("cp866");
    } else {
      charset = Charset.defaultCharset();
    }
    var output = outputStream.toString(charset);

    return Arrays.stream(NEW_LINE_PATTERN.split(output))
      .map(getTestsRegex::matcher)
      .filter(Matcher::matches)
      .map(matcher -> matcher.group(1))
      .toList();
  }

  private static List<String> computeTestIdsByLanguageServer(DocumentContext documentContext) {
    return documentContext.getSymbolTree()
      .getMethods()
      .stream()
      .filter(methodSymbol -> methodSymbol.getAnnotations().stream()
        .map(Annotation::getName)
        .anyMatch("Тест"::equalsIgnoreCase))
      .map(MethodSymbol::getName)
      .toList();
  }
}
