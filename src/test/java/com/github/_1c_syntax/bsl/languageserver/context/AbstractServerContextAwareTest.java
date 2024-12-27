package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.utils.Absolute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;
import java.nio.file.Path;

@SpringBootTest
public abstract class AbstractServerContextAwareTest {
  @Autowired
  protected ServerContext context;

  @PostConstruct
  public void init() {
    context.clear();
  }

  protected void initServerContext(String path) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot);
  }

  protected void initServerContext(Path configurationRoot) {
    context.setConfigurationRoot(configurationRoot);
    context.populateContext();
  }
}
