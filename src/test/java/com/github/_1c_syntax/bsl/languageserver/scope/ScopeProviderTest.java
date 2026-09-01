package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ScopeProviderTest {
  @Autowired
  ScopeProvider provider;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/references.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void getConfigurationScope() {

    var configurationScope = provider.getConfigurationScope();
    configurationScope.getMethod("ГлобальнаяСервернаяПроцедура").get();

    var property = configurationScope.getProperty("ПервыйОбщийМодуль").get();
    provider.getScope(property).getMethod("НеУстаревшаяПроцедура").get();
    provider.getScope(property).getMethod("УстаревшаяФункция").get();

    property = configurationScope.getProperty("Документы").get();
    provider.getScope(property).getProperty("Документ1").get();

    property = configurationScope.getProperty("Catalogs").get();
    property = provider.getScope(property).getProperty("Справочник1").get();

    var catalogScope = provider.getScope(property);
    catalogScope.getMethod("ТестЭкспортная").get();
    catalogScope.getProperty("Реквизит1").get();
  }

}