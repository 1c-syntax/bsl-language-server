package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ScopeResolverTest {

  @Autowired
  ScopeResolver resolver;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/references.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void getConfigurationScope(){
    var configurationScope = resolver.getConfigurationScope();
    configurationScope.getMethod("ГлобальнаяСервернаяПроцедура");
    configurationScope.getProperty("ПервыйОбщийМодуль");
    configurationScope.getProperty("Документы").getScope().getProperty("Документ1");
    configurationScope.getProperty("Catalogs").getScope().getProperty("Справочник1").getScope().getMethod("ТестЭкспортная");

    configurationScope = resolver.getGlobalScope();
    configurationScope.getMethod("ГлобальнаяСервернаяПроцедура");
    configurationScope.getProperty("ПервыйОбщийМодуль");
    configurationScope.getProperty("Документы").getScope().getProperty("Документ1");
    configurationScope.getProperty("Catalogs").getScope().getProperty("Справочник1").getScope().getMethod("ТестЭкспортная");
  }

}