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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.cli.AnalyzeCommand;
import com.github._1c_syntax.bsl.languageserver.cli.FormatCommand;
import com.github._1c_syntax.bsl.languageserver.cli.LanguageServerStartCommand;
import com.github._1c_syntax.bsl.languageserver.cli.McpCommand;
import com.github._1c_syntax.bsl.languageserver.cli.VersionCommand;
import com.github._1c_syntax.bsl.languageserver.cli.WebsocketCommand;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static picocli.CommandLine.Command;

/**
 * Главный класс-лаунчер BSL Language Server.
 * <p>
 * Точка входа в приложение, обрабатывает аргументы командной строки
 * и запускает соответствующие команды (lsp, analyze, format и т.д.).
 * Интегрирован с Spring Boot для управления зависимостями и конфигурацией.
 */
@Command(
  name = "bsl-language-server",
  subcommands = {
    AnalyzeCommand.class,
    FormatCommand.class,
    VersionCommand.class,
    LanguageServerStartCommand.class,
    WebsocketCommand.class,
    McpCommand.class
  },
  usageHelpAutoWidth = true,
  synopsisSubcommandLabel = "[COMMAND [ARGS]]",
  footer = "@|green Copyright(c) 2018-2025|@",
  header = "@|green BSL language server|@")
@SpringBootApplication(scanBasePackageClasses = BSLLSPLauncher.class)
@Component
@ConditionalOnProperty(
  prefix = "app.command.line.runner",
  value = "enabled",
  havingValue = "true",
  matchIfMissing = true)
@RequiredArgsConstructor
public class BSLLSPLauncher implements Callable<Integer>, ExitCodeGenerator {

  private static final String DEFAULT_COMMAND = "lsp";

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  @Unmatched
  private List<String> unmatched;

  private final Set<Pattern> allowedAdditionalArgs = Set.of(
    CaseInsensitivePattern.compile("--spring\\..*"),
    CaseInsensitivePattern.compile("--app\\..*"),
    CaseInsensitivePattern.compile("--logging\\..*"),
    CaseInsensitivePattern.compile("--debug")
  );

  private final CommandLine.IFactory picocliFactory;

  private int exitCode;

  public static void main(String[] args) {
    applyMcpEndpointPath(args);

    var applicationContext = new SpringApplicationBuilder(BSLLSPLauncher.class)
      .web(getWebApplicationType(args))
      .profiles(getActiveProfiles(args))
      .run(args);

    var launcher = applicationContext.getBean(BSLLSPLauncher.class);
    launcher.run(args);

    if (launcher.getExitCode() >= 0) {
      System.exit(
        SpringApplication.exit(applicationContext)
      );
    }
  }

  public void run(String... args) {
    var cmd = new CommandLine(this, picocliFactory);

    // проверка использования дефолтной команды
    // если строка параметров пуста, то это точно вызов команды по умолчанию
    if (args.length == 0) {
      args = addDefaultCommand(args);
    } else {
      var parseResult = cmd.parseArgs(args);
      var unmatchedArgs = parseResult.unmatched().stream()
        .filter(s -> allowedAdditionalArgs.stream().noneMatch(pattern -> pattern.matcher(s).matches()))
        .toList();

      if (!unmatchedArgs.isEmpty()) {
        unmatchedArgs.forEach(s -> cmd.getErr().println("Unknown option: '" + s + "'"));
        cmd.usage(cmd.getOut());
        exitCode = cmd.getCommandSpec().exitCodeOnInvalidInput();
        return;
      }

      // если переданы параметры без команды и это не справка
      // то считаем, что параметры для команды по умолчанию
      if (!parseResult.hasSubcommand() && !parseResult.isUsageHelpRequested()) {
        args = addDefaultCommand(args);
      }
    }

    exitCode = cmd.execute(args);

  }

  private static String[] addDefaultCommand(String[] args) {
    List<String> tmpList = new ArrayList<>(Arrays.asList(args));
    tmpList.addFirst(DEFAULT_COMMAND);
    args = tmpList.toArray(new String[0]);
    return args;
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }

  public Integer call() {
    // заглушка, командой как таковой не пользуемся
    return 0;
  }

  private static WebApplicationType getWebApplicationType(String[] args) {
    // A servlet container is needed for the LSP WebSocket endpoint and/or the MCP Streamable HTTP endpoint.
    if (isWebsocketMode(args) || isMcpHttp(args)) {
      return WebApplicationType.SERVLET;
    }
    return WebApplicationType.NONE;
  }

  private static String[] getActiveProfiles(String[] args) {
    if (isMcpHttp(args)) {
      // MCP over Streamable HTTP. Two distinct sub-profiles by the LSP transport it sits next to:
      // `websocket-mcp` (stdout free) vs `lsp-mcp` (stdout is the LSP channel) — drives log routing.
      var lspTransportProfile = isWebsocketMode(args) ? "websocket-mcp" : "lsp-mcp";
      return new String[]{"mcp", lspTransportProfile};
    }
    if (isMcpStdio(args)) {
      // standalone `mcp` subcommand: MCP over stdio.
      return new String[]{"mcp", "mcp-stdio"};
    }
    return new String[0];
  }

  private static boolean isWebsocketMode(String[] args) {
    var argsList = Arrays.asList(args);
    return argsList.contains("-w") || argsList.contains("websocket");
  }

  /**
   * Флаг {@code --mcp} — поднять MCP по Streamable HTTP рядом с LSP. Команда {@code lsp}
   * необязательна (это режим по умолчанию), поэтому флаг работает и без неё, и с {@code websocket}.
   */
  private static boolean hasMcpFlag(String[] args) {
    return Arrays.asList(args).contains("--mcp");
  }

  /**
   * MCP по Streamable HTTP рядом с LSP (по stdio или websocket) — флаг {@code --mcp}.
   */
  private static boolean isMcpHttp(String[] args) {
    return hasMcpFlag(args);
  }

  /**
   * Самостоятельный режим {@code mcp} по stdio.
   */
  private static boolean isMcpStdio(String[] args) {
    return Arrays.asList(args).contains("mcp");
  }

  /**
   * Перенести значение {@code --mcp-path} в системное свойство до старта контекста:
   * эндпоинт Streamable HTTP регистрируется автоконфигурацией на refresh, раньше выполнения команды.
   */
  private static void applyMcpEndpointPath(String[] args) {
    if (!isMcpHttp(args)) {
      return;
    }
    var mcpPath = extractOptionValue(args, "--mcp-path");
    if (mcpPath != null && !mcpPath.isBlank()) {
      System.setProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", mcpPath);
    }
  }

  /**
   * Извлечь значение опции, поддерживая обе формы: {@code --opt=value} и {@code --opt value}.
   */
  @Nullable
  private static String extractOptionValue(String[] args, String option) {
    var prefix = option + "=";
    for (var i = 0; i < args.length; i++) {
      if (args[i].startsWith(prefix)) {
        return args[i].substring(prefix.length());
      }
      if (args[i].equals(option) && i + 1 < args.length) {
        return args[i + 1];
      }
    }
    return null;
  }
}
