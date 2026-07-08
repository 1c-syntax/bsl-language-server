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
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
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
@SpringBootApplication(scanBasePackageClasses = MainApplication.class)
@Component
@ConditionalOnProperty(
  prefix = "app.command.line.runner",
  value = "enabled",
  havingValue = "true",
  matchIfMissing = true)
@RequiredArgsConstructor
public class MainApplication implements Callable<Integer>, ExitCodeGenerator {

  private static final String DEFAULT_COMMAND = "lsp";

  /**
   * Опция осознанного включения отладочного режима Spring Boot.
   * <p>
   * Без неё любой запрос на отладку — флаги {@code --debug}/{@code --trace} или переменные среды
   * {@code DEBUG}/{@code TRACE} (Spring привязывает их к свойствам {@code debug}/{@code trace}
   * по relaxed binding) — игнорируется: см. {@link #guardSpringDebugMode(String[])}.
   */
  static final String ENABLE_DEBUG_OPTION = "--enable-debug-i-know-what-i-am-doing";

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
    CaseInsensitivePattern.compile("--server\\..*"),
    // Опции команды по умолчанию (lsp), допустимые без явного указания команды: `--mcp`, `--mcp-path`.
    CaseInsensitivePattern.compile("--mcp(-path)?(=.*)?"),
    CaseInsensitivePattern.compile("--debug(=.*)?"),
    CaseInsensitivePattern.compile("--trace(=.*)?")
  );

  private final CommandLine.IFactory picocliFactory;

  private int exitCode;

  public static void main(String[] args) {
    args = guardSpringDebugMode(args);
    applyMcpEndpointPath(args);

    var applicationContext = new SpringApplicationBuilder(MainApplication.class)
      .web(getWebApplicationType(args))
      .profiles(getActiveProfiles(args))
      .run(args);

    var launcher = applicationContext.getBean(MainApplication.class);
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

  static WebApplicationType getWebApplicationType(String[] args) {
    // A servlet container is needed for the LSP WebSocket endpoint or any MCP HTTP transport (Streamable / SSE).
    if (isWebsocketMode(args) || isMcpHttp(args) || isMcpSubcommandOverHttp(args)) {
      return WebApplicationType.SERVLET;
    }
    return WebApplicationType.NONE;
  }

  static String[] getActiveProfiles(String[] args) {
    if (isMcpHttp(args)) {
      // `--mcp` flag: MCP over Streamable HTTP alongside LSP. Two distinct sub-profiles by the LSP
      // transport it sits next to: `websocket-mcp` (stdout free) vs `lsp-mcp` (stdout is the LSP channel).
      var lspTransportProfile = isWebsocketMode(args) ? "websocket-mcp" : "lsp-mcp";
      return new String[]{"mcp", lspTransportProfile};
    }
    if (isMcpSubcommand(args)) {
      // standalone `mcp` subcommand: transport selected by --protocol (stdio | sse | streamable).
      var transportProfile = switch (mcpProtocol(args)) {
        case "sse" -> "mcp-sse";
        case "streamable" -> "mcp-streamable";
        default -> "mcp-stdio";
      };
      return new String[]{"mcp", transportProfile};
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
   * Самостоятельная команда {@code mcp} (транспорт выбирается параметром {@code --protocol}).
   */
  private static boolean isMcpSubcommand(String[] args) {
    return Arrays.asList(args).contains("mcp");
  }

  /**
   * Значение {@code --protocol} команды {@code mcp} (по умолчанию {@code stdio}).
   */
  private static String mcpProtocol(String[] args) {
    var protocol = extractOptionValue(args, "--protocol");
    return protocol == null ? "stdio" : protocol.toLowerCase(Locale.ROOT);
  }

  /**
   * Команда {@code mcp} с HTTP-транспортом ({@code --protocol sse|streamable}) — требует servlet-контейнера.
   */
  private static boolean isMcpSubcommandOverHttp(String[] args) {
    if (!isMcpSubcommand(args)) {
      return false;
    }
    var protocol = mcpProtocol(args);
    return protocol.equals("sse") || protocol.equals("streamable");
  }

  /**
   * Защита от случайного включения отладочного режима Spring Boot.
   * <p>
   * Отладка ({@code --debug}/{@code --trace}, свойства {@code debug}/{@code trace} или переменные
   * среды {@code DEBUG}/{@code TRACE}) включает объёмный лог core-логгеров и отчёт об
   * автоконфигурации. Для LSP по stdio это особенно опасно: посторонний вывод способен замусорить
   * канал протокола. При этом {@code DEBUG} — распространённое имя переменной среды, её легко
   * выставить в окружении случайно, а переименовать/отключить ключ на уровне Spring Boot нельзя.
   * <p>
   * Поэтому отладка включается только при явной передаче {@link #ENABLE_DEBUG_OPTION} в командной
   * строке. Без неё запрос на отладку нейтрализуется до старта контекста: флаги {@code --debug}/
   * {@code --trace} убираются из аргументов, а свойства {@code debug}/{@code trace} принудительно
   * выставляются в {@code false} (системное свойство приоритетнее переменной среды), чтобы случайно
   * заданный {@code DEBUG} не сработал.
   *
   * @param args исходные аргументы командной строки
   * @return аргументы без {@link #ENABLE_DEBUG_OPTION} и (если отладка не разрешена) без флагов
   *   {@code --debug}/{@code --trace}
   */
  static String[] guardSpringDebugMode(String[] args) {
    var withoutOptIn = removeArgs(args, MainApplication::isDebugOptInArg);
    if (hasDebugOptIn(args)) {
      // Пользователь осознанно включил отладку — пропускаем флаги как есть.
      return withoutOptIn;
    }

    var cleaned = removeArgs(withoutOptIn, MainApplication::isDebugOrTraceFlag);
    var strippedFlag = cleaned.length != withoutOptIn.length;

    if (strippedFlag || debugRequestedFromEnvironment()) {
      // Системное свойство приоритетнее переменной среды — так гасится случайный DEBUG/TRACE.
      System.setProperty("debug", "false");
      System.setProperty("trace", "false");
      System.err.println(
        "Ignoring debug/trace request: it turns on verbose Spring Boot debug logging that can "
          + "corrupt the LSP stdout channel. Pass " + ENABLE_DEBUG_OPTION + "=true to enable it "
          + "intentionally.");
    }

    return cleaned;
  }

  private static boolean hasDebugOptIn(String[] args) {
    for (var arg : args) {
      if (arg.equalsIgnoreCase(ENABLE_DEBUG_OPTION)) {
        return true;
      }
      if (isDebugOptInArg(arg)) {
        return isEnabled(arg.substring(ENABLE_DEBUG_OPTION.length() + 1));
      }
    }
    return false;
  }

  private static boolean isDebugOptInArg(String arg) {
    return arg.equalsIgnoreCase(ENABLE_DEBUG_OPTION)
      || arg.regionMatches(true, 0, ENABLE_DEBUG_OPTION + "=", 0, ENABLE_DEBUG_OPTION.length() + 1);
  }

  private static boolean isDebugOrTraceFlag(String arg) {
    return arg.equalsIgnoreCase("--debug") || arg.regionMatches(true, 0, "--debug=", 0, 8)
      || arg.equalsIgnoreCase("--trace") || arg.regionMatches(true, 0, "--trace=", 0, 8);
  }

  private static boolean debugRequestedFromEnvironment() {
    return isEnabled(System.getenv("DEBUG")) || isEnabled(System.getenv("TRACE"))
      || isEnabled(System.getProperty("debug")) || isEnabled(System.getProperty("trace"));
  }

  /**
   * Значение считается включающим отладку по правилам Spring Boot: задано и не равно {@code false}
   * (пустая строка, как у флага {@code --debug} без значения, тоже включает).
   */
  private static boolean isEnabled(@Nullable String value) {
    return value != null && !value.equalsIgnoreCase("false");
  }

  private static String[] removeArgs(String[] args, Predicate<String> drop) {
    return Arrays.stream(args).filter(drop.negate()).toArray(String[]::new);
  }

  /**
   * Перенести значение {@code --mcp-path} в системное свойство до старта контекста:
   * эндпоинт Streamable HTTP регистрируется автоконфигурацией на refresh, раньше выполнения команды.
   */
  static void applyMcpEndpointPath(String[] args) {
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
