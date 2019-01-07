package org.github._1c_syntax.intellij.bsl.lsp.server.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github._1c_syntax.intellij.bsl.lsp.server.BSLLSPLauncher.createOptions;

class LanguageServerStartCommandTest {

    @Test
    void testExecute() {
        Options options = createOptions();
        CommandLine commandLine = new CommandLine.Builder().addOption(options.getOption("d")).build();
        Command command = new LanguageServerStartCommand(commandLine);

        int result = command.execute();
        assertThat(result).isEqualTo(0);
    }
}