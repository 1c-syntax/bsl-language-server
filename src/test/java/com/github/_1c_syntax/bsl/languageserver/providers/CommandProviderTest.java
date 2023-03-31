package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.commands.CommandSupplier;
import com.github._1c_syntax.bsl.languageserver.commands.DefaultCommandArguments;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CommandProviderTest {

  @Autowired
  private CommandProvider commandProvider;

  @Test
  void commandsIdsContainsTestCommand() {
    // given
    // TestCommandSupplier test component.

    // when
    var commandIds = commandProvider.getCommandIds();

    // then
    assertThat(commandIds).contains("test");
  }

  @Test
  void executeCommandArgumentsExtractedCorrectly() {
    // given
    var rawCommandArguments = "{ \"id\": \"test\", \"uri\": \"someUri\"}";
    var params = new ExecuteCommandParams("Some command", List.of(rawCommandArguments));

    // when
    var commandArguments = commandProvider.extractArguments(params);

    // then
    assertThat(commandArguments)
      .isOfAnyClassIn(DefaultCommandArguments.class)
    ;
  }

  @Test
  void commandExecutesCorrectly() {
    // given
    var commandArguments = new DefaultCommandArguments(URI.create("fake:///fake-uri"), "test");

    // when
    var result = commandProvider.executeCommand(commandArguments);

    // then
    assertThat(result)
      .isEqualTo(1);
  }

  @TestConfiguration
  static class Configuration {
    @Bean
    CommandSupplier<DefaultCommandArguments> commandSupplier() {
      return new TestCommandSupplier();
    }
  }

  static class TestCommandSupplier implements CommandSupplier<DefaultCommandArguments> {
    @Override
    public Class<DefaultCommandArguments> getCommandArgumentsClass() {
      return DefaultCommandArguments.class;
    }

    @Override
    public Optional<Object> execute(DefaultCommandArguments arguments) {
      return Optional.of(1);
    }
  }
}