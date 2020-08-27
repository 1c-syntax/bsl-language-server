package com.github._1c_syntax.bsl.languageserver.cli.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class LanguageServerLauncherConfiguration {

  @Bean
  @Lazy
  public Launcher<LanguageClient> launcher(
    LanguageServer server,
    LanguageServerConfiguration configuration
  ) {
    InputStream in = System.in;
    OutputStream out = System.out;

    File logFile = configuration.getTraceLog();
    if (logFile == null) {
      return LSPLauncher.createServerLauncher(server, in, out);
    }

    Launcher<LanguageClient> launcher;

    try {
      PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
      launcher = LSPLauncher.createServerLauncher(server, in, out, false, printWriter);
    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      LOGGER.error("Can't create LSP trace file", e);
      if (logFile.isDirectory()) {
        LOGGER.error("Trace log setting must lead to file, not directory! {}", logFile.getAbsolutePath());
      }

      launcher = LSPLauncher.createServerLauncher(server, in, out);
    }

    return launcher;
  }

}
