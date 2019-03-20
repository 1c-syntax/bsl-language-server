package org.github._1c_syntax.bsl.languageserver.configuration;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticConfiguration;
import org.github._1c_syntax.bsl.languageserver.configuration.diagnostics.LineLengthDiagnosticConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageServerConfigurationTest {

  @Test
  void createDefault() {
    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create();

    // then
    assertThat(configuration.getDiagnosticLanguage()).isEqualTo(DiagnosticLanguage.RU);
    assertThat(configuration.getDiagnostics()).isEmpty();
  }

  @Test
  void createFromFile() {

    // given
    File configurationFile = new File("./src/test/resources/bsl-language-server.conf");

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    // then
    DiagnosticLanguage diagnosticLanguage = configuration.getDiagnosticLanguage();
    Map<String, Either<Boolean, DiagnosticConfiguration>> diagnostics = configuration.getDiagnostics();

    assertThat(diagnosticLanguage).isEqualTo(DiagnosticLanguage.EN);
    assertThat(diagnostics).hasSize(2);

    Either<Boolean, DiagnosticConfiguration> lineLength = diagnostics.get("LineLength");
    assertThat(lineLength.isRight()).isTrue();
    assertThat(lineLength.getRight()).isOfAnyClassIn(LineLengthDiagnosticConfiguration.class);
    assertThat((LineLengthDiagnosticConfiguration) lineLength.getRight())
      .extracting(LineLengthDiagnosticConfiguration::getMaxLineLength)
      .isEqualTo(140);

    Either<Boolean, DiagnosticConfiguration> methodSize = diagnostics.get("MethodSize");
    assertThat(methodSize.isLeft()).isTrue();
    assertThat(methodSize.getLeft()).isEqualTo(false);


  }
}