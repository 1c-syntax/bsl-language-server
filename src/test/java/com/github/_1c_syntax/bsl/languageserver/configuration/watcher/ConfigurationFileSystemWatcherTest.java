package com.github._1c_syntax.bsl.languageserver.configuration.watcher;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ConfigurationFileSystemWatcherTest {

  @Autowired
  private ConfigurationFileSystemWatcher watcher;

  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void test() throws IOException {
    // given
    var file = File.createTempFile("bsl-config", ".json");
    var content = "{\"language\": \"ru\"}";
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    configuration.update(file);

    // when
    content = "{\"language\": \"en\"}";
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);

    watcher.watch();

    // then
    assertThat(configuration.getLanguage()).isEqualTo(Language.EN);
  }

}