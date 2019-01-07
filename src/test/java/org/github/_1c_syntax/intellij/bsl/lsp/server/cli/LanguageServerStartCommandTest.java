package org.github._1c_syntax.intellij.bsl.lsp.server.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github._1c_syntax.intellij.bsl.lsp.server.BSLLSPLauncher.createOptions;

class LanguageServerStartCommandTest {

    @Test
    void testExecute() throws ParseException {
        Options options = createOptions();
        DefaultParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, new String[]{"-d", "en"});
        Command command = new LanguageServerStartCommand(commandLine);

        int result = command.execute();
        assertThat(result).isEqualTo(0);
    }
}